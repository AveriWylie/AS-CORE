package ascore.nodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ascore.realtime.RealtimePublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * N9 - the one genuinely novel unit in this module, and the reason it earns high focus.
 *
 * THE PROBLEM: Redis key expiry is SILENT. When node:hb:{id} expires, nothing fires - but the plan
 * requires a /topic/nodes broadcast on the UP->DOWN transition. A pure key-expiry design detects
 * death but cannot announce it.
 *
 * THE SOLUTION: a 15s scheduled sweep holds the PREVIOUS sweep's liveness snapshot in memory,
 * compares it to the current live set, and publishes ONLY the transitions (UP->DOWN, DOWN->UP).
 * In-memory previous-state is safe to lose on restart - the next sweep rebuilds it, at worst
 * re-emitting one transition, which the dashboard treats idempotently.
 *
 * THE NUMBERS: 45s TTL (N7) + 15s sweep = 60s worst-case detection, exactly the plan's acceptance
 * bound. Both constants are derived from that bound.
 *
 * Consumes: @Scheduled(fixedDelay = 15000) on the method; @EnableScheduling required ONCE on a
 * config class or this is silently inert (same trap family as auto-index-creation and @Valid);
 * HeartbeatStore.aliveNodeIds() (ours), RealtimePublisher (ours) for /topic/nodes.
 */
@Component
public class NodeStatusSweep {

	private static final Logger log = LoggerFactory.getLogger(NodeStatusSweep.class);

	private final HeartbeatStore heartbeats;
	private final RealtimePublisher publisher;

	// only ever touched by the scheduler thread, and fixedDelay never overlaps runs
	private Set<String> previousAlive = new HashSet<>();

	public NodeStatusSweep(HeartbeatStore heartbeats, RealtimePublisher publisher) {
		this.heartbeats = heartbeats;
		this.publisher = publisher;
	}

	@Scheduled(fixedDelay = 15000)
	public void sweep() {
		Set<String> current;

		try {
			current = heartbeats.aliveNodeIds();
		} catch (DataAccessException e) {
			log.warn("node sweep skipped, heartbeat store unreachable: {}", e.getMessage());
			return;
		}

		for (String id : current) if (!previousAlive.contains(id)) publish(id, "UP");
		for (String id : previousAlive) if (!current.contains(id)) publish(id, "DOWN");
		previousAlive = current;
	}

	private void publish(String nodeId, String status) {publisher.publish("/topic/nodes", Map.of("nodeId", nodeId, "status", status));}

}
