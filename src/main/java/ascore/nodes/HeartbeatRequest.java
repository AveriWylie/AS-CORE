package ascore.nodes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * POST /api/nodes/{id}/heartbeat body, sent every 15s by agents.
 *
 * currentLoad is the running job count; runningJobIds is optional and normalised
 * to an empty list.
 */
public record HeartbeatRequest(
		@NotNull @Min(0) Integer currentLoad,
		List<String> runningJobIds) {

	public HeartbeatRequest {runningJobIds = runningJobIds == null ? List.of() : runningJobIds;}

}
