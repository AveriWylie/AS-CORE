package ascore.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Executor;
import ascore.observability.AsCoreMetrics;
import ascore.observability.AuditService;
import ascore.realtime.RealtimePublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

	// the capability a node must report as true to be handed each type; unlisted types need none
	private static final Map<JobType, String> REQUIRES = Map.of(
			JobType.TEXTURE_BAKE, "blender",
			JobType.LIGHT_BAKE, "blender",
			JobType.MESH_OPTIMIZE, "blender");

	private final JobStore jobs;
	private final QueueStore queue;
	private final RealtimePublisher publisher;
	private final Executor executor;
	private final Duration claimTimeout;
	private final long backoffBaseMs;
	private final AsCoreMetrics metrics;
	private final AuditService audit;

	public JobService(JobStore jobs, QueueStore queue, RealtimePublisher publisher,
			@Qualifier("telemetryExecutor") Executor executor,
			@Value("${shayveri.jobs.claim-timeout-seconds:20}") long claimTimeoutSeconds,
			@Value("${shayveri.jobs.backoff-base-ms:1000}") long backoffBaseMs,
			AsCoreMetrics metrics, AuditService audit) {
		this.jobs = jobs;
		this.queue = queue;
		this.publisher = publisher;
		this.executor = executor;
		this.claimTimeout = Duration.ofSeconds(claimTimeoutSeconds);
		this.backoffBaseMs = backoffBaseMs;
		this.metrics = metrics;
		this.audit = audit;
	}

	public Job create(JobCreateRequest request, String who) {
		Job job = jobs.save(Job.from(request, Instant.now()));
		queue.enqueue(job.getType(), job.getId(), job.getPriority());
		metrics.jobTransition("NEW", job.getStatus().name());
		audit.audit(who, "job.create", job.getId(), null, Map.of("type", job.getType().name(), "mapId", job.getMapId()));
		publisher.publish("/topic/jobs", Map.of("event", "queued", "jobId", job.getId()));
		return job;
	}

	public Optional<Job> claim(ClaimRequest request) {
		Optional<String> id = queue.claimOne(claimableTypes(request.capabilities()), request.nodeId(), claimTimeout);
		if (id.isEmpty()) return Optional.empty();

		Job job = jobs.findById(id.get()).orElseThrow(() -> new NoSuchElementException("no job " + id.get()));
		JobStatus from = job.getStatus();
		job.claim(request.nodeId(), Instant.now());
		jobs.save(job);
		transitioned(from, job);
		publisher.publish("/topic/jobs", Map.of("event", "claimed", "jobId", job.getId(), "nodeId", request.nodeId()));
		return Optional.of(job);
	}

	public Job progress(String id, ProgressRequest request) {
		Job job = active(id);
		JobStatus from = job.getStatus();
		job.start(Instant.now());
		jobs.save(job);
		transitioned(from, job);
		publisher.publish("/topic/jobs/" + id, Map.of("pct", request.pct(), "log", request.log() == null ? "" : request.log()));
		return job;
	}

	public Job complete(String id, CompleteRequest request) {
		Job job = active(id);
		JobStatus from = job.getStatus();
		queue.ack(job.getClaimedBy(), id);
		job.complete(request.resultRef(), request.resultMeta(), Instant.now());
		jobs.save(job);
		transitioned(from, job);
		publisher.publish("/topic/jobs", Map.of("event", "done", "jobId", id));
		publisher.publish("/topic/jobs/" + id, Map.of("status", "DONE"));
		return job;
	}

	/**
	 * Retries while attempts <= maxRetries, so maxRetries counts retries rather than total
	 * runs: a job with maxRetries 3 runs four times and is requeued exactly three. The job stays
	 * in flight through the backoff and is released after it.
	 */
	public Job fail(String id, FailRequest request) {
		Job job = active(id);
		JobStatus from = job.getStatus();
		String node = job.getClaimedBy();

		if (job.getAttempts() <= job.getMaxRetries()) {
			job.requeue(request.error());
			jobs.save(job);
			transitioned(from, job);
			long delay = backoffBaseMs * (1L << job.getAttempts());
			executor.execute(() -> {
				sleep(delay);
				queue.release(node, id);
			});
			publisher.publish("/topic/jobs/" + id, Map.of("status", "QUEUED", "retryInMs", delay));
			return job;
		}

		queue.ack(node, id);
		job.fail(request.error(), Instant.now());
		jobs.save(job);
		transitioned(from, job);
		publisher.publish("/topic/alerts", Map.of("event", "job-failed", "jobId", id, "error", request.error()));
		return job;
	}

	public List<Job> list(Optional<JobStatus> status, Optional<String> mapId) {return jobs.find(status, mapId);}

	static List<JobType> claimableTypes(Map<String, Object> capabilities) {
		return Arrays.stream(JobType.values())
				.filter(t -> !REQUIRES.containsKey(t) || Boolean.TRUE.equals(capabilities.get(REQUIRES.get(t))))
				.toList();
	}

	// a job the caller can act on: it exists and is currently held by a node
	private Job active(String id) {
		Job job = jobs.findById(id).orElseThrow(() -> new NoSuchElementException("no job " + id));
		if (job.getStatus() != JobStatus.CLAIMED && job.getStatus() != JobStatus.RUNNING) {
			throw new IllegalStateException("job " + id + " is " + job.getStatus());
		}
		return job;
	}

	private void transitioned(JobStatus from, Job job) {
		if (from != job.getStatus()) metrics.jobTransition(from.name(), job.getStatus().name());
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
