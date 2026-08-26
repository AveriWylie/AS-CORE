package dev.shayveri.core.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

	/**
	 * Validation failures -> 400 with per-field detail.
	 *
	 * TODO(averi): implement:
	 *   1. Build a Map<String, String> of the failed fields. The API walk:
	 *      ex.getBindingResult().getFieldErrors() returns a List<FieldError>;
	 *      each FieldError has .getField() (the name, e.g. "placeId") and
	 *      .getDefaultMessage() (the complaint, e.g. "must not be null").
	 *   2. Construct the ApiError (status 400, message "validation failed",
	 *      the map, Instant.now()).
	 *   3. return ResponseEntity.badRequest().body(apiError);
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		throw new UnsupportedOperationException("TODO(averi): implement per C2 step 1-3");
	}

	/**
	 * Unparseable JSON -> 400 (empty fieldErrors map; there are no fields
	 * to blame when the whole body is garbage).
	 *
	 * TODO(averi): construct the ApiError (400, "malformed request body",
	 * empty map, now) and return it via ResponseEntity.badRequest().
	 * Do NOT put ex.getMessage() in the response - framework messages can
	 * leak internals; keep the message fixed.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
		throw new UnsupportedOperationException("TODO(averi): implement per C2");
	}

	/**
	 * Anything unexpected -> clean 500, no stack trace in the response.
	 *
	 * TODO(averi): return (500, "internal error", empty map, now) via
	 * ResponseEntity.internalServerError(). The stack trace belongs in the
	 * server log, never in the HTTP response - log it here with your
	 * chosen logger before returning.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		throw new UnsupportedOperationException("TODO(averi): implement per C2");
	}

}
