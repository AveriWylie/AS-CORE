package ascore.jobs;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * QueueStore without Redis, following RedisQueueStore's semantics: FIFO per band,
 * high bands before normal ones, an in-flight list per node, release only when still
 * in flight, and ack clearing the id from its queue as well.
 *
 * claimOne never blocks. It tries each band once and returns, so logic tests do not
 * wait out a timeout. The real blocking and atomicity are proven against Redis.
 */
public class InMemoryQueueStore implements QueueStore {

	private final Map<String, Deque<String>> queues = new HashMap<>();
	private final Map<String, Deque<String>> inflight = new HashMap<>();
	private final Map<String, String> origin = new HashMap<>();

	@Override
	public synchronized void enqueue(JobType type, String jobId, int priority) {
		String queue = type + (priority > 0 ? ":high" : "");
		origin.put(jobId, queue);
		queues.computeIfAbsent(queue, k -> new ArrayDeque<>()).addLast(jobId);
	}

	@Override
	public synchronized Optional<String> claimOne(List<JobType> types, String nodeId, Duration timeout) {
		for (String source : sources(types)) {
			Deque<String> queue = queues.get(source);
			if (queue == null || queue.isEmpty()) continue;
			String id = queue.pollFirst();
			inflight.computeIfAbsent(nodeId, k -> new ArrayDeque<>()).addLast(id);
			return Optional.of(id);
		}
		return Optional.empty();
	}

	@Override
	public synchronized boolean release(String nodeId, String jobId) {
		Deque<String> held = inflight.get(nodeId);
		if (held == null || !held.remove(jobId)) return false;
		String queue = origin.get(jobId);
		if (queue != null) queues.computeIfAbsent(queue, k -> new ArrayDeque<>()).addFirst(jobId);
		return true;
	}

	@Override
	public synchronized void ack(String nodeId, String jobId) {
		Deque<String> held = inflight.get(nodeId);
		if (held != null) held.remove(jobId);
		String queue = origin.remove(jobId);
		if (queue != null && queues.containsKey(queue)) queues.get(queue).remove(jobId);
	}

	public synchronized boolean isQueued(String jobId) {return queues.values().stream().anyMatch(q -> q.contains(jobId));}

	public synchronized boolean isInFlight(String nodeId, String jobId) {
		Deque<String> held = inflight.get(nodeId);
		return held != null && held.contains(jobId);
	}

	private static List<String> sources(List<JobType> types) {
		List<String> sources = new ArrayList<>();
		for (JobType type : types) sources.add(type + ":high");
		for (JobType type : types) sources.add(type.toString());
		return sources;
	}

}
