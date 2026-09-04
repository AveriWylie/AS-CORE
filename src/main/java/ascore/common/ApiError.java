package ascore.common;

import java.time.Instant;
import java.util.Map;

/**
 * C1 - the uniform error body every failed request returns.
 *
 * Responsibility: one fixed JSON shape for ALL errors, so every client
 * (Roblox scripts, node agents, dashboard) parses failures the same way.
 *
 * Consumes: nothing - this is entirely ours. Jackson serializes the record
 * to JSON automatically; each record component becomes a JSON key.
 *
 * WHY fieldErrors IS ALWAYS PRESENT, never null and never absent. A client
 * that has to check whether the key exists before reading it is a client that
 * will eventually forget to. An empty map costs two bytes of JSON and removes
 * a null check from every consumer, so the compact constructor normalises null
 * to empty rather than trusting callers to pass one.
 */
public record ApiError(
		// The HTTP status code, repeated in the body so a logged payload is self-describing
		int status,
		// One human-readable summary line. Fixed text, never a framework message; see C2.
		String message,
		// Field name to what is wrong with it. Empty, never null, when this is not a validation failure.
		Map<String, String> fieldErrors,
		// When the error happened. Serialized as an ISO-8601 instant by Spring Boot's Jackson defaults. */
		Instant timestamp) {

	/*
	A compact constructor runs before the fields are assigned, which makes it
	the one place that can guarantee the invariant above for every caller,
	including ones written later.

	The map is copied rather than stored as given. A record is meant to be
	immutable, and keeping a reference to a caller's mutable HashMap would make
	that a promise this type cannot actually keep.
	*/
	public ApiError {
		fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
	}

	/** The common case: a failure with no per-field detail. */
	public static ApiError of(int status, String message) {
		return new ApiError(status, message, Map.of(), Instant.now());
	}
}
