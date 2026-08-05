package dev.shayveri.core.ingress;

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
 *   @RestController        - "this class's methods ARE endpoints; return
 *                            values become JSON responses."
 *   @PostMapping("/path")  - binds POST /path to the method.
 *   @RequestBody           - "parse the request's JSON into this parameter"
 *                            (Jackson does it, using A1/A2's shapes).
 *   @Valid                 - THE TRIGGER for A1/A2's annotations. Without
 *                            it they are decoration. Failure -> framework
 *                            throws MethodArgumentNotValidException -> C2
 *                            turns it into the 400.
 *   @Validated (class level) - required for validating EACH ELEMENT of the
 *                            List in the events endpoint; per-element
 *                            validation is opt-in (see A2's note).
 *   ResponseEntity.accepted().build() - the 202 with empty body.
 *
 * SECURITY (lives OUTSIDE this class, per the security architecture): the
 * role rule is one line in SecurityConfig - see the TODO
 * Nothing in this file checks identity, ever.
 *
 * TODO(averi):
 *   1. private final TelemetryService field + constructor.
 *   2. POST /api/telemetry: method taking
 *          @Valid @RequestBody TelemetrySnapshotRequest request
 *      body: delegate to service.accept(request); return
 *      ResponseEntity.accepted().build();  (return type ResponseEntity<Void>)
 *   3. POST /api/telemetry/events: method taking
 *          @RequestBody List<@Valid GameEventRequest> events
 *      body: delegate + same 202.
 *   4. The SecurityConfig line (see below), so only ROBLOX passes.
 *
 * Done when: D1 (web layer) and D2 go green - valid body + ROBLOX key ->
 * 202; missing field -> 400 naming it; DASH/NODE key -> rejected.
 *
 * git add TelemetryController.java src/main/java/dev/shayveri/core/ingress/
 *
 * git status
 */
@RestController
@Validated
public class TelemetryController {

	private final TelemetryService ts;

	public TelemetryController (TelemetryService ts) {
		this.ts = ts;
	}

}
