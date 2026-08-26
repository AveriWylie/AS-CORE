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
 * What is batching:
 * FIRSTLY, the problem is that every telemtry request sends http headers (metadata)
 * alongside JSON data, If u send a seperate networkr equest every time a player clicks
 * a button, the network overhead will overwhelm the roblox servers and this backend.
 *
 * Batching is the solution, collect events in a local Luau table inside Roblox and
 * send them all over the network in a single, larger compressed JSON payload every
 * 10 to 30 seconds.
 *
 * one of many network engineering principles that (this one less so but other ne
 * principles) are reasons for using java over something that compiles faster yet
 * still uses oop like C++. Other ones are a quick google search away or stated
 * else where inline in this project
 *
 * Consumes: same as A1 - Jackson (automatic binding; it also parses ISO-8601
 * strings like "2026-07-08T12:00:00Z" into Instant for free) and Jakarta
 * Validation annotations.
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
		// How we ensure null type safety at compile time
		// java allows null type objects at any time
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
	public GameEventRequest {data = data == null ? Map.of() : data;}

	public record Position(double x, double y, double z) { }

}
