package ascore.jobs;

import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
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

	private static final String QUEUE = "jobs:queue:";
	private static final String INFLIGHT = "jobs:inflight:";
	private static final String ORIGIN = "jobs:origin";
	private static final Duration POLL = Duration.ofMillis(100);

	// records the job's queue, then appends it: one script, so a job is never queued without an origin
	private static final RedisScript<Long> ENQUEUE = RedisScript.of("""
			redis.call('HSET', KEYS[2], ARGV[1], KEYS[1])
			return redis.call('RPUSH', KEYS[1], ARGV[1])""", Long.class);

	/**
	 * Moves an id from in-flight back to the head of its queue, but only if it is still in
	 * flight. One script, so the membership check and the move cannot be split by a
	 * concurrent complete().
	 */
	private static final RedisScript<Long> RELEASE = RedisScript.of("""
			local removed = redis.call('LREM', KEYS[1], 1, ARGV[1])
			if removed > 0 then
				local origin = redis.call('HGET', KEYS[2], ARGV[1])
				if origin then redis.call('LPUSH', origin, ARGV[1]) end
			end
			return removed""", Long.class);

	/**
	 * Drops an id from in-flight, and from its queue too, in case a sweep released it first.
	 * That second removal is what keeps a completed job from ever being claimed again.
	 */
	private static final RedisScript<Long> ACK = RedisScript.of("""
			local removed = redis.call('LREM', KEYS[1], 1, ARGV[1])
			local origin = redis.call('HGET', KEYS[2], ARGV[1])
			if origin then redis.call('LREM', origin, 0, ARGV[1]) end
			redis.call('HDEL', KEYS[2], ARGV[1])
			return removed""", Long.class);

	private final StringRedisTemplate redis;

	public RedisQueueStore(StringRedisTemplate redis) {this.redis = redis;}

	@Override public void enqueue(JobType type, String jobId, int priority) {
		String queue = QUEUE + type + (priority > 0 ? ":high" : "");
		redis.execute(ENQUEUE, List.of(queue, ORIGIN), jobId);
	}

	/**
	 * Each attempt is one LMOVE: pop from the head of a queue and push onto this node's
	 * in-flight list as a single command, so no two nodes can take the same id.
	 *
	 * It polls rather than parking on BLMOVE, because BLMOVE blocks on a single source list and
	 * a claim spans several types with two bands each. Blocking on the first list would starve
	 * the rest. Polling costs round trips, not correctness: every attempt is still atomic.
	 */
	@Override public Optional<String> claimOne(List<JobType> types, String nodeId, Duration timeout) {
		List<String> sources = sources(types);
		ListOperations.MoveTo<String> inflight = ListOperations.MoveTo.toTail(INFLIGHT + nodeId);
		long deadline = System.nanoTime() + timeout.toNanos();

		while (true) {
			for (String source : sources) {
				String id = redis.opsForList().move(ListOperations.MoveFrom.fromHead(source), inflight);
				if (id != null) return Optional.of(id);
			}
			if (System.nanoTime() >= deadline) return Optional.empty();
			try {
				Thread.sleep(POLL);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
		}
	}

	@Override public boolean release(String nodeId, String jobId) {
		Long removed = redis.execute(RELEASE, List.of(INFLIGHT + nodeId, ORIGIN), jobId);
		return removed != null && removed > 0;
	}

	@Override public void ack(String nodeId, String jobId) {redis.execute(ACK, List.of(INFLIGHT + nodeId, ORIGIN), jobId);}

	// every high band before any normal one, so priority work is claimed first across all types
	private static List<String> sources(List<JobType> types) {
		List<String> sources = new ArrayList<>();
		for (JobType type : types) sources.add(QUEUE + type + ":high");
		for (JobType type : types) sources.add(QUEUE + type);
		return sources;
	}

}
