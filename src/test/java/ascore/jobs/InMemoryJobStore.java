package ascore.jobs;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// JobStore without Mongo. It assigns an id on first save, as Mongo does
public class InMemoryJobStore implements JobStore {

	private final Map<String, Job> jobs = new LinkedHashMap<>();

	@Override
	public Job save(Job job) {
		if (job.getId() == null) assignId(job, UUID.randomUUID().toString());
		jobs.put(job.getId(), job);
		return job;
	}

	@Override
	public Optional<Job> findById(String id) {return Optional.ofNullable(jobs.get(id));}

	@Override
	public List<Job> find(Optional<JobStatus> status, Optional<String> mapId) {
		return jobs.values().stream()
				.filter(j -> status.isEmpty() || j.getStatus() == status.get())
				.filter(j -> mapId.isEmpty() || j.getMapId().equals(mapId.get()))
				.toList();
	}

	@Override
	public List<Job> findByClaimedByAndStatusIn(String nodeId, List<JobStatus> statuses) {
		return jobs.values().stream()
				.filter(j -> nodeId.equals(j.getClaimedBy()) && statuses.contains(j.getStatus()))
				.toList();
	}

	private static void assignId(Job job, String id) {
		try {
			Field field = Job.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(job, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

}
