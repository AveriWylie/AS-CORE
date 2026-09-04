package ascore.ingress;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * A8 - the HTTP edge. Two endpoints, both 202 Accepted with no body,
 * delegating instantly to A7. No logic here beyond delegation - thin by
 * design.
 *
 * Consumes (not ours) - Spring Web + validation triggers:
 *   {@code @RestController}        - "this class's methods ARE endpoints; return
 *                            values become JSON responses."
 *   {@code @PostMapping}("/path")  - binds POST /path to the method.
 *   {@code @RequestBody}           - "parse the request's JSON into this parameter"
 *                            (Jackson does it, using A1/A2's shapes).
 *   {@code @Valid}                 - THE TRIGGER for A1/A2's annotations. Without
 *                            it they are decoration. Failure -> framework
 *                            throws MethodArgumentNotValidException -> C2
 *                            turns it into the 400.
 *   {@code @Validated} (class level) - required for validating EACH ELEMENT of the
 *                            List in the events endpoint; per-element
 *                            validation is opt-in (see A2's note).
 *   ResponseEntity.accepted().build() - the 202 with empty body.
 *
 * SECURITY (lives OUTSIDE this class, per the security architecture): the
 * role rule is one line in SecurityConfig. Nothing in this file checks identity, ever.
 *
 * Done when: D1 (web layer) and D2 go green - valid body + ROBLOX key ->
 * 202; missing field -> 400 naming it; DASH/NODE key -> rejected.
 */
@RestController
@Validated
public class TelemetryController {

	private final TelemetryService ts;

	public TelemetryController (TelemetryService ts) {this.ts = ts;}

	/*
	 * Both endpoints return 202 Accepted with no body, because A7 hands the
	 * work to an executor and returns immediately. 200 would imply the write
	 * had happened; it has not yet. 202 is the honest code for "accepted for
	 * processing".
	 *
	 * {@code @Valid} is what activates the constraints on the request records. Without
	 * it they are decoration and a malformed body reaches the service.
	 */
	@PostMapping("/api/telemetry")
	public ResponseEntity<Void> snapshot(@Valid @RequestBody TelemetrySnapshotRequest request) {
		ts.accept(request);
		return ResponseEntity.accepted().build();
	}

	/*
	 * List<@Valid GameEventRequest>, not @Valid List<...>. Per-element
	 * validation inside a collection is opt-in, and it is the class-level
	 * {@code @Validated} that makes the inner annotation take effect.
	 */
	@PostMapping("/api/telemetry/events")
	public ResponseEntity<Void> events(@RequestBody List<@Valid GameEventRequest> events) {
		ts.acceptEvents(events);
		return ResponseEntity.accepted().build();
	}
}
