package ascore.jobs;

import java.util.List;
import java.util.Optional;

/**
 * J4 - rule-5 seam for durable job history. Query methods are shaped by what the SYSTEM needs, not
 * by what Mongo makes easy: findByClaimedByAndStatusIn exists specifically so J9's orphan sweep can
 * find a dead node's live jobs. Design the seam around the callers.
 * Consumes: nothing - ours (J5 MongoJobStore + JobRepository implement it).
 */
public interface JobStore {
	Job save(Job job);
	Optional<Job> findById(String id);
	List<Job> find(Optional<JobStatus> status, Optional<String> mapId);
	List<Job> findByClaimedByAndStatusIn(String nodeId, List<JobStatus> statuses);
}
