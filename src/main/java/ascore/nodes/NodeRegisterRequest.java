package ascore.nodes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * N1 - POST /api/nodes/register body.
 *
 * capabilities is optional and normalised to an empty map, so the service never
 * null-checks it (same normalisation as A1).
 */
public record NodeRegisterRequest(
		@NotBlank String nodeId,
		@NotBlank String hostname,
		Map<String, Object> capabilities,
		@NotNull @Min(1) Integer maxConcurrentJobs) {

	public NodeRegisterRequest {capabilities = capabilities == null ? Map.of() : capabilities;}

}
