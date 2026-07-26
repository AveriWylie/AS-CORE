package dev.shayveri.core.ingress;

import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * A1 - the exact shape of POST /api/telemetry's JSON body. This IS the
 * contract with the Roblox-side Luau script; the plan fixes these names.
 * We use this as a base validation layer so telemetry snapshot request is
 * just whatwe recieve tpo validate before any use and document making.
 *
 * The components are declared (the contract is given); what is left to you
 * is the VALIDATION - the annotations that make bad payloads impossible to
 * miss. Validation lives here as annotations and nowhere else.
 *
 * Consumes (not ours):
 *   Jackson              - binds incoming JSON to this record automatically;
 *                          each JSON key maps to the same-named component.
 *                          Invisible: no annotation needed when names match.
 *   Jakarta Validation   - the annotations below, package jakarta.validation
 *                          .constraints.*. They do nothing on their own; they
 *                          fire when the controller (A8) marks the parameter
 *                          @Valid, and failures surface as
 *                          MethodArgumentNotValidException -> handled by C2.
 *
 * TODO(averi): add validation annotations directly above each component:
 *   placeId       @NotBlank          (import jakarta.validation.constraints.NotBlank)
 *                                    - null, "", and "   " all rejected
 *   jobId         @NotBlank
 *   playerCount   @NotNull @Min(0)   - required, and never negative
 *   serverFps     @NotNull @Positive - required, and zero/negative is nonsense
 *   round         (no annotation - free-form, optional)
 *   customMetrics (no annotation - optional)
 *
 * TODO(averi, second): add a COMPACT CONSTRUCTOR to default customMetrics
 * to an empty map so no downstream code ever null-checks it:
 *
 *   public TelemetrySnapshotRequest {
 *       customMetrics = (customMetrics == null) ? Map.of() : customMetrics;
 *   }
 *
 * Done when: the D1 validation tests pass - a missing placeId produces a
 * violation naming "placeId", a valid body produces zero violations.
 *
 * ------------------------------------------------------------------------
 * A compact constructor is record syntax: no parentheses, runs before the
 * components are assigned - in a Java record this is a streamlined syntax
 * used to validate or normalize input arguments before they are assigned
 * to the record's field
 *
 * A Java record is a concise way to define an immutable data class. The
 * compiler automatically generates:
 *
 * a constructor
 * getters (type(), placeId(), etc.)
 * equals()
 * hashCode()
 * toString()
 *
 * So instead of writing a full POJO, you just declare the fields.
 *
 * This record is a Data Transfer Object (DTO) for a telemetry event
 * sent from the game client to your backend. Each instance represents
 * one event, and the endpoint is expected to receive a JSON array of
 * these records.
 */

public record TelemetrySnapshotRequest(
		@NotBlank String placeId,
		@NotBlank String jobId,
		@NotNull @Min(0) Integer playerCount,
		@NotNull @Positive Double serverFps,
		String round,
		Map<String, Object> customMetrics) {

	// difference in indentation is conventional for looks above
	// as its too long for horizontal we list them not at code
	public TelemetrySnapshotRequest {
		customMetrics = customMetrics == null ? Map.of() : customMetrics;
	}
}
