package ascore.nodes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HeartbeatStore without Redis. Liveness is a map rather than an expiring key, so
 * a test decides when a node dies by calling expire instead of waiting out a TTL.
 * Public because the jobs package's orphan sweep depends on the same interface.
 */
public class InMemoryHeartbeatStore implements HeartbeatStore {

	private final Map<String, Integer> alive = new HashMap<>();

	@Override
	public void recordHeartbeat(String nodeId, int load) {alive.put(nodeId, load);}

	@Override
	public boolean isAlive(String nodeId) {return alive.containsKey(nodeId);}

	@Override
	public Set<String> aliveNodeIds() {return Set.copyOf(alive.keySet());}

	@Override
	public Optional<Integer> loadOf(String nodeId) {return Optional.ofNullable(alive.get(nodeId));}

	public void expire(String nodeId) {alive.remove(nodeId);}

}
