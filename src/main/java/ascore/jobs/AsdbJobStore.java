package ascore.jobs;

import static ascore.asdb.AsdbDocumentStore.eq;
import static ascore.asdb.AsdbDocumentStore.in;
import ascore.asdb.AsdbBinaryClient;
import ascore.asdb.AsdbDocumentStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * JobStore on asdb. A job without an id is new and inserted, which assigns one; a job with
 * an id is overwritten in place. Only the service creates jobs, so no upsert is needed.
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbJobStore implements JobStore {

	private final AsdbDocumentStore<Job> jobs;

	public AsdbJobStore(AsdbBinaryClient client) {
		this.jobs = new AsdbDocumentStore<>(client, Job.class);
		jobs.ensureSchema("claimedBy");
	}

	@Override
	public Job save(Job job) {
		if (job.getId() == null) return jobs.insert(job);
		return jobs.upsert(job);
	}

	@Override
	public Optional<Job> findById(String id) {return jobs.find("where " + eq("id", id)).stream().findFirst();}

	@Override
	public List<Job> find(Optional<JobStatus> status, Optional<String> mapId) {
		List<String> filters = new ArrayList<>();
		status.ifPresent(s -> filters.add(eq("status", s)));
		mapId.ifPresent(m -> filters.add(eq("mapId", m)));
		return filters.isEmpty() ? jobs.find() : jobs.find("where " + String.join(" and ", filters));
	}

	@Override
	public List<Job> findByClaimedByAndStatusIn(String nodeId, List<JobStatus> statuses) {
		return jobs.find("where " + eq("claimedBy", nodeId) + " and " + in("status", statuses));
	}

}
