package ascore.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ascore.nodes.InMemoryHeartbeatStore;
import ascore.observability.InMemoryAuditStore;
import ascore.observability.TestObservability;

// T3 and T5. A node is dead here simply by never sending a heartbeat
class OrphanRequeueSweepTest {

	private final InMemoryJobStore jobs = new InMemoryJobStore();
	private final InMemoryQueueStore queue = new InMemoryQueueStore();
	private final InMemoryHeartbeatStore heartbeats = new InMemoryHeartbeatStore();
	private final List<Object> alerts = new ArrayList<>();
	private final JobService service = new JobService(jobs, queue, (topic, payload) -> { }, Runnable::run, 0, 0,
			TestObservability.metrics(queue), TestObservability.audit(new InMemoryAuditStore()));
	private final OrphanRequeueSweep sweep = new OrphanRequeueSweep(heartbeats, queue, jobs, (topic, payload) -> {
		if (topic.equals("/topic/alerts")) alerts.add(payload);
	});

	private Job claimedBy(String nodeId) {
		Job job = service.create(new JobCreateRequest(JobType.CUSTOM, "map-1", 0, null, 3), "dash");
		service.claim(new ClaimRequest(nodeId, Map.of())).orElseThrow();
		return job;
	}

	// T3
	@Test
	void deadNodesJobIsRequeued() {
		Job job = claimedBy("A");

		sweep.sweep();

		Job after = jobs.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.QUEUED, after.getStatus());
		assertEquals(1, after.getAttempts(), "a requeue must not reset attempts");
		assertFalse(queue.isInFlight("A", job.getId()));
		assertTrue(queue.isQueued(job.getId()));
		assertEquals(1, alerts.size());
	}

	@Test
	void liveNodesJobIsLeftAlone() {
		heartbeats.recordHeartbeat("A", 1);
		Job job = claimedBy("A");

		sweep.sweep();

		assertEquals(JobStatus.CLAIMED, jobs.findById(job.getId()).orElseThrow().getStatus());
		assertTrue(alerts.isEmpty());
	}

	// T5. complete() acks first, so the sweep's release finds nothing in flight and leaves it DONE
	@Test
	void completedJobStaysDoneWhenTheSweepRunsAfter() {
		Job job = claimedBy("A");
		service.complete(job.getId(), new CompleteRequest("result", null));

		sweep.sweep();

		assertEquals(JobStatus.DONE, jobs.findById(job.getId()).orElseThrow().getStatus());
		assertFalse(queue.isQueued(job.getId()));
		assertTrue(alerts.isEmpty());
	}

}
