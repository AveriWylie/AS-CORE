package ascore.jobs;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import ascore.realtime.RealtimePublisher;
import org.springframework.stereotype.Service;

/**
 * J8 - the lifecycle logic; the enforcer of the JobStatus state machine and the retry policy.
 *   create: Job(QUEUED) -> JobStore.save -> QueueStore.enqueue -> /topic/jobs (depth delta).
 *   claim(nodeId, capabilities): map capabilities -> claimable JobTypes; QueueStore.claimOne over
 *       them; on claim set CLAIMED, claimedBy, claimedAt, attempts+1, save, publish.
 *   progress(id, pct, log): first progress sets RUNNING + startedAt; publish /topic/jobs/{id}.
 *   complete(id, resultRef, resultMeta): DONE, finishedAt, QueueStore.ack, save, publish both.
 *   fail(id, error): attempts < maxRetries -> backoff (base * 2^attempts, slept on a virtual
 *       thread) then re-enqueue as QUEUED; else terminal FAILED + /topic/alerts. A crash during the
 *       backoff sleep loses only the delay - J9's orphan sweep re-covers the job.
 * Consumes: @Service, Executor (AsyncConfig, for the backoff sleep), JobStore, QueueStore,
 * RealtimePublisher - interfaces only.
 */
@Service
public class JobService {
	// TODO(shahyar): constructor + create/claim/progress/complete/fail per blueprint J8.
}
