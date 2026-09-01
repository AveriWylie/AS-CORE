package dev.shayveri.core.jobs;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import dev.shayveri.core.realtime.RealtimePublisher;

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
 * TODO(shahyar): implement the six methods; keep every status change going through one place so the
 * legal-transition set is enforced in exactly one file.
 */
@Service
public class JobService {
	// TODO(shahyar): constructor + create/claim/progress/complete/fail per blueprint J8.
}
