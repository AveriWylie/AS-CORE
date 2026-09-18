package ascore.jobs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * J2 - POST /api/jobs body, sent by the dashboard.
 *
 * type is the enum, so an unknown value fails in Jackson and reaches the client as
 * a 400. priority defaults to 0, payload to an empty map, maxRetries to 3.
 */
public record JobCreateRequest(
		@NotNull JobType type,
		@NotBlank String mapId,
		@Min(0) Integer priority,
		Map<String, Object> payload,
		@Min(0) Integer maxRetries) {

	public JobCreateRequest {
		priority = priority == null ? 0 : priority;
		payload = payload == null ? Map.of() : payload;
		maxRetries = maxRetries == null ? 3 : maxRetries;
	}

}
