package dev.shayveri.core.ingress;

import java.time.Instant;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A2 - one discrete event in POST /api/telemetry/events. The endpoint
 * accepts a JSON ARRAY of these (batching), so the controller parameter
 * will be List<GameEventRequest>.
 *
 * Consumes: same as A1 - Jackson (automatic binding; it also parses ISO-8601
 * strings like "2026-07-08T12:00:00Z" into Instant for free) and Jakarta
 * Validation annotations.
 *
 * TODO(averi): add validation:
 *   type       @NotBlank   - PLAYER_DEATH | ROUND_START | ROUND_END |
 *                            PERF_SPIKE | CUSTOM (kept as a String for v1;
 *                            an enum would 400 on any unknown value, which
 *                            is too strict while the Luau side is evolving)
 *   placeId    @NotBlank
 *   jobId      @NotBlank
 *   occurredAt @NotNull    - the client-side timestamp
 *   position   (no annotation - only PLAYER_DEATH sends it)
 *    - Nested position vector for creation of heatmaps (player deaths) later. A nested record
 * 		maps to a nested JSON object: {"position": {"x": 1, "y": 2, "z": 3}}.
 * 		No validation needed - if present, Jackson requires all three numbers
 * 		to parse.
 *   data       (no annotation - optional payload)
 *    - This is a flexible "extra information" field. again optional
 * 		This lets the client attach arbitrary metadata without changing
 * 		the DTO every time a new event type is introduced.
 *
 * Plus the same compact-constructor trick as A1 to default data to Map.of().
 *
 * NOTE for the controller later (A8): to validate every element of a
 * List<GameEventRequest>, the parameter needs @Valid on the list AND the
 * controller class needs @Validated - element validation is opt-in.
 *
 * Done when: D1 covers one event with a blank type -> violation naming "type".
 */

public record GameEventRequest(
		@NotBlank String type,
		@NotBlank String placeId,
		@NotBlank String jobId,
		@NotNull Instant occurredAt,
		// no validation field -> optionally in JSON request
		Position position,
		Map<String, Object> data) {

	/**
	 * Java using C legacy code - FYI
	 * ------------------------------------------
	 * tertiary conditional operator equivalent:
	 *
	 * if data == null {
	 *     data = map.of();
	 * } else {
	 *     data = data; (logically = to pass)
	 * }
	 * ------------------------------------------
	 */
	public GameEventRequest {
		data = data == null ? Map.of() : data;
	}

	public record Position(double x, double y, double z) { }

}
