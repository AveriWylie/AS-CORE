package ascore.realtime;

import ascore.jobs.Job;
import ascore.jobs.JobStatus;
import ascore.jobs.JobStore;
import ascore.jobs.JobType;
import ascore.jobs.QueueStore;
import ascore.nodes.NodeService;
import ascore.overrides.ConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// R2 - GET /api/snapshot (DASH): current full state for dashboard reconnect-hydration.
// Assembled ONLY through other modules' interfaces (NodeService, JobStore, ConfigStore) - realtime owns no data.
// R3 AlertBuffer (ours, last 50 in memory) feeds the alerts section. Consumes: Spring Web; SecurityConfig -> DASH.
@RestController
public class SnapshotController {

	private final NodeService nodes;
	private final JobStore jobs;
	private final QueueStore queue;
	private final ConfigStore config;
	private final AlertBuffer alerts;

	public SnapshotController(NodeService nodes, JobStore jobs, QueueStore queue, ConfigStore config, AlertBuffer alerts) {
		this.nodes = nodes;
		this.jobs = jobs;
		this.queue = queue;
		this.config = config;
		this.alerts = alerts;
	}

	/**
	 * The dashboard subscribes first and fetches this second. A delta that lands in between
	 * is then both in the snapshot and on the socket, and applying it twice is harmless
	 * because each one carries the new state rather than a change to it.
	 */
	@GetMapping("/api/snapshot")
	public Map<String, Object> snapshot() {
		Map<String, Long> depths = new TreeMap<>();
		for (JobType type : JobType.values()) depths.put(type.name(), queue.depth(type));
		List<Job> active = new ArrayList<>(jobs.find(Optional.of(JobStatus.CLAIMED), Optional.empty()));
		active.addAll(jobs.find(Optional.of(JobStatus.RUNNING), Optional.empty()));

		return Map.of(
				"nodes", nodes.listWithStatus(),
				"queue", Map.of("depths", depths, "active", active.stream().map(SnapshotController::summary).toList()),
				"config", config.activePointers(),
				"alerts", alerts.recent());
	}

	private static Map<String, Object> summary(Job job) {
		return Map.of("id", job.getId(), "type", job.getType(), "status", job.getStatus(),
				"claimedBy", job.getClaimedBy(), "attempts", job.getAttempts());
	}

}
