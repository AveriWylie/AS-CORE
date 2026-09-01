package dev.shayveri.core.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * C2 - converts every failure into the C1 shape. The plan's acceptance
 * criterion this enforces: "malformed payloads return 400 with field
 * errors, never 500."
 *
 * Consumes (not ours):
 *   {@code @RestControllerAdvice}  - Spring Web. Marks this class as a global
 *                            interceptor: when ANY controller throws, Spring
 *                            looks here for a matching @ExceptionHandler
 *                            before writing the response. No controller ever
 *                            catches its own errors.
 *   {@code @ExceptionHandler}(X.class)
 *                          - "when exception type X escapes a controller,
 *                            call this method instead of crashing."
 *   MethodArgumentNotValidException
 *                          - thrown BY the framework when a @Valid check on
 *                            a request body fails (see A1). Carries every
 *                            failed field inside its BindingResult.
 *   HttpMessageNotReadableException
 *                          - thrown BY the framework when the body is not
 *                            parseable JSON at all (Jackson gave up).
 *   ResponseEntity         - Spring Web's "status code + body" wrapper:
 *                            ResponseEntity.badRequest().body(x) -> 400 x,
 *                            ResponseEntity.internalServerError().body(x) -> 500 x.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Validation failures -> 400 with per-field detail.
	 *
	 * <p>This is the handler the acceptance criterion is about: a body that
	 * fails a {@code @Valid} check must come back as 400 naming the offending
	 * fields, never as a 500.
	 *
	 * <p>LinkedHashMap, not HashMap: the fields come out in the order the
	 * framework reported them, so two identical bad requests produce byte
	 * identical responses. That is what makes the body assertable in a test and
	 * diffable in a log.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {

		Map<String, String> fieldErrors = new LinkedHashMap<>();

		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			/*
			 * putIfAbsent, not put. One field can fail several constraints at
			 * once (playerCount is both @NotNull and @Min(0)), and last-write
			 * wins would make which complaint you see depend on the framework's
			 * internal ordering. First reported is at least stable.
			 */
			String complaint = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
			fieldErrors.putIfAbsent(error.getField(), complaint);
		}

		return ResponseEntity.badRequest()
				.body(new ApiError(400, "validation failed", fieldErrors, Instant.now()));
	}

	/**
	 * Validation failures on METHOD parameters -> 400, same shape as above.
	 *
	 * <p>WHY A SECOND VALIDATION HANDLER. {@code @Valid} on a whole
	 * {@code @RequestBody} raises MethodArgumentNotValidException, handled
	 * above. A constraint on a container ELEMENT does not: the events endpoint
	 * takes {@code List<@Valid GameEventRequest>}, which is checked by method
	 * validation (enabled by {@code @Validated} on the controller) and raises
	 * {@link ConstraintViolationException} instead.
	 *
	 * <p>Without this, that endpoint answered a caller's bad input with a 500,
	 * which is precisely the failure C2 exists to prevent, on the one path the
	 * obvious test does not cover. Found by asking whether {@code @Validated}
	 * was load-bearing; it is, and it needed this.
	 *
	 * <p>The property path is trimmed to its last segment. Method validation
	 * reports paths like {@code events[0].placeId}, and the leading method and
	 * argument names are noise to a client that only wants to know which field
	 * it got wrong.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {

		Map<String, String> fieldErrors = new LinkedHashMap<>();

		for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
			String path = String.valueOf(violation.getPropertyPath());
			int lastDot = path.lastIndexOf('.');
			String field = lastDot < 0 ? path : path.substring(lastDot + 1);
			fieldErrors.putIfAbsent(field, violation.getMessage());
		}

		return ResponseEntity.badRequest()
				.body(new ApiError(400, "validation failed", fieldErrors, Instant.now()));
	}

	/**
	 * Unparseable JSON -> 400, with an empty field map: there are no fields to
	 * blame when the whole body is garbage.
	 *
	 * <p>The message is FIXED and deliberately says nothing about the parse
	 * failure. Jackson's own text quotes the offending input and names internal
	 * classes and line numbers, which hands an unauthenticated caller a probe
	 * into the server. The detail goes to the log instead, where it is useful
	 * and not public.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
		log.debug("rejected an unparseable request body", ex);
		return ResponseEntity.badRequest().body(ApiError.of(400, "malformed request body"));
	}

	/**
	 * A missing route or static resource -> 404, still in the C1 shape.
	 *
	 * <p>THIS HANDLER EXISTS BECAUSE OF THE ONE BELOW IT. Since Spring Boot
	 * 3.2, an unmatched request raises {@link NoResourceFoundException}, and
	 * this class is a plain {@code @RestControllerAdvice} rather than a
	 * subclass of {@code ResponseEntityExceptionHandler}. Without this method
	 * the catch-all would swallow that exception and answer every 404 with a
	 * 500, which is both wrong and alarming to anything monitoring the service.
	 *
	 * <p>Spring picks the most specific matching handler, so declaring this at
	 * all is what keeps the status correct.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
		return ResponseEntity.status(404).body(ApiError.of(404, "not found"));
	}

	/**
	 * Anything that already carries its own HTTP status keeps it.
	 *
	 * <p>Same reasoning as the 404 above, generalised: {@code ResponseStatusException}
	 * and friends all implement {@code ErrorResponse}, and they exist precisely
	 * to say "answer with this status". Letting the catch-all flatten them into
	 * 500 would discard a decision some other layer made on purpose.
	 */
	@ExceptionHandler(ErrorResponseException.class)
	public ResponseEntity<ApiError> handleErrorResponse(ErrorResponseException ex) {
		int status = ex.getStatusCode().value();
		return ResponseEntity.status(status).body(ApiError.of(status, "request failed"));
	}

	/**
	 * Anything unexpected -> a clean 500, with no stack trace in the response.
	 *
	 * <p>Logged at ERROR with the exception attached, because this is the only
	 * record that will exist: the caller is told nothing beyond "internal
	 * error", by design. A stack trace in an HTTP response tells an attacker
	 * the framework versions, the package layout and often the file paths.
	 *
	 * <p>Note how narrow this is in practice. The handlers above claim
	 * validation failures, unreadable bodies, missing routes and anything
	 * carrying its own status, so what reaches here really is unexpected, which
	 * is what makes an ERROR log line here worth paging on.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("unhandled exception escaped a controller", ex);
		return ResponseEntity.internalServerError().body(ApiError.of(500, "internal error"));
	}

}
