package ascore.jobs;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * J6 - the rule-5 seam for the hot queue path. The contract is shaped around ONE guarantee it must
 * make possible (see J7): a claim is atomic. Every method here is chosen so the implementation can
 * keep that guarantee - claimOne is a single conceptual operation, never a findable-then-takeable
 * pair, because splitting it is exactly the race the acceptance test hunts.
 *
 * Consumes: nothing - ours.
 */
public interface QueueStore {
	void enqueue(JobType type, String jobId, int priority);

	/** Atomic claim across the given types for this node, blocking up to timeout (the 20s long-poll). */
	Optional<String> claimOne(List<JobType> types, String nodeId, Duration timeout);

	/** Return an in-flight id to its queue (requeue path); must be a no-op if already gone. */
	void release(String nodeId, String jobId);

	/** Drop an in-flight id (successful completion). */
	void ack(String nodeId, String jobId);
}
