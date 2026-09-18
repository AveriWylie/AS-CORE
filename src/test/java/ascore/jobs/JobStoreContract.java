package ascore.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// what every JobStore must do, run against asdb and Mongo. Each test uses its own mapId
abstract class JobStoreContract {

	@Autowired
	protected JobStore store;

	private static final Instant AT = Instant.ofEpochMilli(1_700_000_000_000L);

	private Job saved(String mapId) {
		return store.save(Job.from(new JobCreateRequest(JobType.TEXTURE_BAKE, mapId, 1, Map.of("res", 2048, "tags", List.of("a")), 3), AT));
	}

	@Test
	void newJobGetsAnIdAndReadsBack() {
		Job job = saved("map-" + UUID.randomUUID());
		assertNotNull(job.getId());

		Job read = store.findById(job.getId()).orElseThrow();
		assertEquals(JobType.TEXTURE_BAKE, read.getType());
		assertEquals(JobStatus.QUEUED, read.getStatus());
		assertEquals(Map.of("res", 2048, "tags", List.of("a")), read.getPayload());
		assertEquals(AT, read.getCreatedAt());
	}

	@Test
	void savingAChangedJobOverwritesIt() {
		Job job = saved("map-" + UUID.randomUUID());
		job.claim("n1", AT);
		store.save(job);

		Job read = store.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.CLAIMED, read.getStatus());
		assertEquals("n1", read.getClaimedBy());
		assertEquals(1, read.getAttempts());
	}

	@Test
	void findFiltersByStatusAndMapId() {
		String map = "map-" + UUID.randomUUID();
		saved(map);
		Job claimed = saved(map);
		claimed.claim("n1", AT);
		store.save(claimed);

		assertEquals(2, store.find(Optional.empty(), Optional.of(map)).size());
		assertEquals(1, store.find(Optional.of(JobStatus.QUEUED), Optional.of(map)).size());
		assertEquals(0, store.find(Optional.of(JobStatus.DONE), Optional.of(map)).size());
	}

	@Test
	void findsANodesActiveJobs() {
		String node = "node-" + UUID.randomUUID();
		Job job = saved("map-" + UUID.randomUUID());
		job.claim(node, AT);
		store.save(job);

		assertEquals(1, store.findByClaimedByAndStatusIn(node, List.of(JobStatus.CLAIMED, JobStatus.RUNNING)).size());
		assertEquals(0, store.findByClaimedByAndStatusIn(node, List.of(JobStatus.RUNNING)).size());
	}

}
