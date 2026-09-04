package ascore.jobs;

/**
 * J1b - the job lifecycle states. Enum, not string (contrast A2's event type which stayed a string
 * while the Luau side evolves): job status is OUR internal state machine, and an invalid transition
 * must be impossible to represent, not merely validated. Legal transitions:
 *   QUEUED -> CLAIMED -> RUNNING -> DONE
 *   CLAIMED|RUNNING -> FAILED -> (QUEUED again if attempts < maxRetries, else terminal FAILED)
 * The enum fixes the vocabulary; JobService enforces the edges.
 */
public enum JobStatus {
	QUEUED, CLAIMED, RUNNING, DONE, FAILED
}
