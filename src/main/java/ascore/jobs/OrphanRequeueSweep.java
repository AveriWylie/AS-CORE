package ascore.jobs;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ascore.nodes.HeartbeatStore;
import ascore.realtime.RealtimePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
 * ascore.nodes.HeartbeatStore (ours, cross-module), QueueStore, JobStore,
 * RealtimePublisher (all ours).
 */
@Component
public class OrphanRequeueSweep {

	private static final Logger log = LoggerFactory.getLogger(OrphanRequeueSweep.class);
	private static final List<JobStatus> LIVE = List.of(JobStatus.CLAIMED, JobStatus.RUNNING);

	private final HeartbeatStore heartbeats;
	private final QueueStore queue;
	private final JobStore jobs;
	private final RealtimePublisher publisher;

	public OrphanRequeueSweep(HeartbeatStore heartbeats, QueueStore queue, JobStore jobs, RealtimePublisher publisher) {
		this.heartbeats = heartbeats;
		this.queue = queue;
		this.jobs = jobs;
		this.publisher = publisher;
	}

	@Scheduled(fixedDelay = 30000)
	public void sweep() {
		try {
			for (String node : holders()) {
				if (heartbeats.isAlive(node)) continue;
				for (Job job : jobs.findByClaimedByAndStatusIn(node, LIVE)) requeue(node, job);
			}
		} catch (DataAccessException e) {
			log.warn("orphan sweep skipped, a store is unreachable: {}", e.getMessage());
		}
	}

	// only nodes currently holding CLAIMED or RUNNING work can have orphans
	private Set<String> holders() {
		Set<String> nodes = new HashSet<>();
		for (JobStatus status : LIVE) {
			for (Job job : jobs.find(Optional.of(status), Optional.empty())) nodes.add(job.getClaimedBy());
		}
		return nodes;
	}

	// requeued only if this sweep took it out of flight, so a job completed mid-sweep stays DONE
	private void requeue(String node, Job job) {
		if (!queue.release(node, job.getId())) return;
		job.requeue(null);
		jobs.save(job);
		publisher.publish("/topic/alerts", Map.of("event", "job-orphaned", "jobId", job.getId(), "nodeId", node));
	}

}
