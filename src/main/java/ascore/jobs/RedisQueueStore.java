package ascore.jobs;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * J7 - HARD SPOT 1. The single most correctness-critical class in the system, which is why this
 * module runs at the highest focus level.
 *
 * THE ACCEPTANCE CRITERION: "two nodes claiming concurrently never receive the same job." This is
 * NOT achieved by locking, checking, or retrying in Java. It is achieved by ONE property of Redis:
 * Redis executes each command single-threaded, and
 *     BLMOVE  source  destination  LEFT  RIGHT  timeout
 * pops an id off the queue list AND pushes it onto this node's in-flight list as ONE atomic
 * command. There is no observable instant in which the id is in neither list or in both. Therefore
 * no interleaving of two concurrent claims can hand the same id to two nodes - the guarantee is a
 * property of the primitive, and the test (T1, 50 iterations, two threads on one job) verifies it
 * rather than creates it.
 *
 * THE ANTI-PATTERN (do not do this): read the queue in one call, decide in Java, move in a second
 * call. Any check-then-act split across two round-trips re-opens the exact race the criterion
 * forbids. If a future change makes claimOne look like two operations, the guarantee is already
 * broken regardless of tests passing on a quiet machine.
 *
 * Keys: jobs:queue:{type} (one list per type; priority handled as two bands - priority>0 pushed to
 * a high-band list checked first - which satisfies the plan's "keyed by priority" for 9 nodes
 * without a full priority queue). jobs:inflight:{nodeId} (this node's crash ledger; J9 reads it).
 *
 * Consumes: StringRedisTemplate.opsForList() -
 *   leftPush(queueKey, id)                              enqueue
 *   move(src, LEFT, inflightKey, RIGHT, timeout)  ->    BLMOVE, the blocking atomic claim; parks
 *                                                       server-side up to 20s = long-poll with zero
 *                                                       polling traffic (this is WHY the stack chose
 *                                                       virtual threads: a parked claim costs ~nothing)
 *   remove(inflightKey, 1, id)                          ack / release
 */
@Component
public class RedisQueueStore implements QueueStore {
	@Override public void enqueue(JobType type, String jobId, int priority) {
		throw new UnsupportedOperationException("TODO(shahyar): J7");
	}
	@Override public Optional<String> claimOne(List<JobType> types, String nodeId, Duration timeout) {
		throw new UnsupportedOperationException("TODO(shahyar): J7");
	}
	@Override public void release(String nodeId, String jobId) {
		throw new UnsupportedOperationException("TODO(shahyar): J7");
	}
	@Override public void ack(String nodeId, String jobId) {
		throw new UnsupportedOperationException("TODO(shahyar): J7");
	}
}
