package ascore.jobs;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * J5 - the Mongo adapter behind JobStore, the A6/N5 pattern. find() picks the
 * derived query matching whichever filters are present.
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "mongo", matchIfMissing = true)
public class MongoJobStore implements JobStore {

	private final JobRepository repository;

	public MongoJobStore(JobRepository repository) {this.repository = repository;}

	@Override
	public Job save(Job job) {return repository.save(job);}

	@Override
	public Optional<Job> findById(String id) {return repository.findById(id);}

	@Override
	public List<Job> find(Optional<JobStatus> status, Optional<String> mapId) {
		if (status.isPresent() && mapId.isPresent()) return repository.findByStatusAndMapId(status.get(), mapId.get());
		if (status.isPresent()) return repository.findByStatus(status.get());
		if (mapId.isPresent()) return repository.findByMapId(mapId.get());
		return repository.findAll();
	}

	@Override
	public List<Job> findByClaimedByAndStatusIn(String nodeId, List<JobStatus> statuses) {
		return repository.findByClaimedByAndStatusIn(nodeId, statuses);
	}

}
