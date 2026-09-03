package dev.shayveri.core.nodes;

import java.util.HashSet;
import java.util.Set;

import dev.shayveri.core.realtime.RealtimePublisher;

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
	@Scheduled(fixedDelay = 15000)
	public void sweep() {
		// TODO(averi): transition detection + publish per blueprint N9.
	}
}
