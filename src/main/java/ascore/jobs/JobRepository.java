package ascore.jobs;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

// J5 - package-private so only MongoJobStore reaches it; Spring Data derives each query from its method name
interface JobRepository extends MongoRepository<Job, String> {

	List<Job> findByStatusAndMapId(JobStatus status, String mapId);

	List<Job> findByStatus(JobStatus status);

	List<Job> findByMapId(String mapId);

	List<Job> findByClaimedByAndStatusIn(String claimedBy, List<JobStatus> statuses);
}
