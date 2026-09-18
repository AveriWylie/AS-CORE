package ascore.jobs;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * J3 - the job document, and the project's RESEARCH DATASET: full history lives here forever (Redis
 * only ever holds ids in flight). Every bake's before/after metrics in resultMeta are the actual
 * scientific payload the whole pipeline exists to produce.
 *
 * The timings are explicit separate Instants (createdAt, claimedAt, startedAt, finishedAt) rather
 * than a status-change log, because the durations between them ARE the research signal (queue wait,
 * claim-to-start latency, bake duration) and must be trivially queryable.
 *
 * Consumes: @Document("jobs"), @Id (generated here, unlike Node's natural key - a job has no
 * external identity), @Indexed on status and mapId (the two filters GET /api/jobs requires).
 *
 * {@code @Indexed} JobStatus status; String claimedBy; int attempts; int maxRetries;
 * Instant createdAt/claimedAt/startedAt/finishedAt; String resultRef; Map resultMeta; String
 * lastError. Constructor for creation (status QUEUED, attempts 0); getters; controlled mutators
 * only for the lifecycle transitions JobService drives.
 */
@Document("jobs")
public class Job {

	@Id
	private String id;
	private JobType type;
	@Indexed
	private String mapId;
	private int priority;
	private Map<String, Object> payload;
	@Indexed
	private JobStatus status;
	private String claimedBy;
	private int attempts;
	private int maxRetries;
	private Instant createdAt;
	private Instant claimedAt;
	private Instant startedAt;
	private Instant finishedAt;
	private String resultRef;
	private Map<String, Object> resultMeta;
	private String lastError;

	public Job(JobType type, String mapId, int priority, Map<String, Object> payload, int maxRetries, Instant createdAt) {
		this.type = type;
		this.mapId = mapId;
		this.priority = priority;
		this.payload = payload;
		this.maxRetries = maxRetries;
		this.createdAt = createdAt;
		this.status = JobStatus.QUEUED;
		this.attempts = 0;
	}

	public static Job from(JobCreateRequest request, Instant createdAt) {
		return new Job(request.type(), request.mapId(), request.priority(), request.payload(), request.maxRetries(), createdAt);
	}

	// every claim counts as an attempt, so the retry ceiling is enforced on attempts alone
	void claim(String nodeId, Instant at) {
		status = JobStatus.CLAIMED;
		claimedBy = nodeId;
		claimedAt = at;
		attempts++;
	}

	// the first progress report is when the work actually began
	void start(Instant at) {
		status = JobStatus.RUNNING;
		if (startedAt == null) startedAt = at;
	}

	void complete(String resultRef, Map<String, Object> resultMeta, Instant at) {
		status = JobStatus.DONE;
		this.resultRef = resultRef;
		this.resultMeta = resultMeta;
		finishedAt = at;
	}

	void fail(String error, Instant at) {
		status = JobStatus.FAILED;
		lastError = error;
		finishedAt = at;
	}

	// back to the queue with attempts intact; error is null when the node died rather than reported
	void requeue(String error) {
		status = JobStatus.QUEUED;
		if (error != null) lastError = error;
	}

	public String getId() {return id;}

	public JobType getType() {return type;}

	public String getMapId() {return mapId;}

	public int getPriority() {return priority;}

	public Map<String, Object> getPayload() {return Map.copyOf(payload);}

	public JobStatus getStatus() {return status;}

	public String getClaimedBy() {return claimedBy;}

	public int getAttempts() {return attempts;}

	public int getMaxRetries() {return maxRetries;}

	public Instant getCreatedAt() {return createdAt;}

	public Instant getClaimedAt() {return claimedAt;}

	public Instant getStartedAt() {return startedAt;}

	public Instant getFinishedAt() {return finishedAt;}

	public String getResultRef() {return resultRef;}

	public Map<String, Object> getResultMeta() {return resultMeta == null ? Map.of() : Map.copyOf(resultMeta);}

	public String getLastError() {return lastError;}

}
