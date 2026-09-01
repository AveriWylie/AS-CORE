package dev.shayveri.core.jobs;

import dev.shayveri.core.nodes.HeartbeatStore;
import dev.shayveri.core.realtime.RealtimePublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * J9 - HARD SPOT 2. The acceptance criterion: a node that dies mid-job -> its CLAIMED/RUNNING jobs
 * requeue automatically.
 *
 * THE DESIGN: a 30s scheduled sweep walks jobs:inflight:{nodeId} for each node the registry knows,
 * asks Module 2's HeartbeatStore.isAlive(nodeId) - THROUGH THE INTERFACE, never RedisHeartbeatStore
 * directly; this cross-module rule-5 dependency is the seam paying off - and for each DEAD node
 * releases every in-flight id back to its queue, flips the Job to QUEUED (attempts left intact, so
 * the retry ceiling still applies), and publishes /topic/alerts.
 *
 * THE IDEMPOTENCY GUARD (the subtle correctness point, T5): the sweep can race a slow complete().
 * If complete() already ack'd an id, release() of that same id must be a no-op - which is why
 * QueueStore.release is specified membership-checked. Result: a job completing exactly as its node
 * is declared dead stays DONE and never double-appears in a queue. Without this guard the two hard
 * spots would interact to duplicate work.
 *
 * Consumes: @Scheduled(fixedDelay = 30000) + @EnableScheduling (shared with N9's config);
 * dev.shayveri.core.nodes.HeartbeatStore (ours, cross-module), QueueStore, JobStore,
 * RealtimePublisher (all ours).
 *
 * TODO(shahyar): for each known node, if !heartbeats.isAlive(id), drain its inflight list via
 * release + Job->QUEUED + alert. Order the operations so a crash mid-sweep leaves a recoverable
 * state (release before status flip; the next sweep re-covers anything missed).
 */
@Component
public class OrphanRequeueSweep {
	@Scheduled(fixedDelay = 30000)
	public void sweep() {
		// TODO(shahyar): J9 - dead-node inflight drain, idempotent.
	}
}
