package dev.shayveri.core.jobs;

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
 * TODO(averi): fields @Id String id; JobType type; String mapId; int priority; Map payload;
 * {@code @Indexed} JobStatus status; String claimedBy; int attempts; int maxRetries;
 * Instant createdAt/claimedAt/startedAt/finishedAt; String resultRef; Map resultMeta; String
 * lastError. Constructor for creation (status QUEUED, attempts 0); getters; controlled mutators
 * only for the lifecycle transitions JobService drives.
 */
@Document("jobs")
public class Job {
	@Id
	private String id;
	// TODO(averi): remaining fields + creation constructor + lifecycle mutators per blueprint J3.
}
