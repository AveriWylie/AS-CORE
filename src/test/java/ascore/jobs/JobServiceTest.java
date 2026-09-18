package ascore.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import ascore.observability.InMemoryAuditStore;
import ascore.observability.TestObservability;
import org.junit.jupiter.api.Test;

/**
 * T2 and T4, plus the state edges JobService enforces. The executor runs inline and
 * the backoff and claim timeout are zero, so a retry happens within the fail() call.
 */
class JobServiceTest {

	private final InMemoryJobStore jobs = new InMemoryJobStore();
	private final InMemoryQueueStore queue = new InMemoryQueueStore();
	private final List<String> topics = new ArrayList<>();
	private final JobService service = new JobService(jobs, queue, (topic, payload) -> topics.add(topic), Runnable::run, 0, 0,
			TestObservability.metrics(queue), TestObservability.audit(new InMemoryAuditStore()));

	private Job create(JobType type) {return service.create(new JobCreateRequest(type, "map-1", 0, null, 3), "dash");}

	private static ClaimRequest node(String nodeId) {return new ClaimRequest(nodeId, Map.of());}

	// T2
	@Test
	void capabilityGatesClaimableTypes() {
		create(JobType.TEXTURE_BAKE);

		assertTrue(service.claim(node("no-blender")).isEmpty());
		assertTrue(service.claim(new ClaimRequest("has-blender", Map.of("blender", true))).isPresent());
	}

	/**
	 * T4. maxRetries 3 means four runs: the first three failures requeue, the fourth is
	 * terminal. attempts counts every claim, so it ends at four.
	 */
	@Test
	void retriesUntilTheCeilingThenFails() {
		Job job = create(JobType.CUSTOM);

		for (int run = 0; run < 4; run++) {
			service.claim(node("n1")).orElseThrow();
			service.fail(job.getId(), new FailRequest("boom"));
		}

		Job end = jobs.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.FAILED, end.getStatus());
		assertEquals(4, end.getAttempts());
		assertTrue(topics.contains("/topic/alerts"));
	}

	@Test
	void progressStampsStartOnlyOnce() {
		Job job = create(JobType.CUSTOM);
		service.claim(node("n1"));

		Instant first = service.progress(job.getId(), new ProgressRequest(10, null)).getStartedAt();
		Job later = service.progress(job.getId(), new ProgressRequest(50, "halfway"));

		assertEquals(JobStatus.RUNNING, later.getStatus());
		assertEquals(first, later.getStartedAt());
	}

	@Test
	void completingAQueuedJobIsRejected() {
		Job job = create(JobType.CUSTOM);
		assertThrows(IllegalStateException.class, () -> service.complete(job.getId(), new CompleteRequest("r", null)));
	}

	@Test
	void unknownJobIsNotFound() {
		assertThrows(NoSuchElementException.class, () -> service.progress("ghost", new ProgressRequest(10, null)));
	}

}
