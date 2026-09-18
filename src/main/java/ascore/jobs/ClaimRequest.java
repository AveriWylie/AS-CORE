package ascore.jobs;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

// POST /api/jobs/claim body: which node is asking, and what it can run
public record ClaimRequest(@NotBlank String nodeId, Map<String, Object> capabilities) {

	public ClaimRequest {capabilities = capabilities == null ? Map.of() : capabilities;}

}
