package ascore.nodes;

import java.util.Optional;
import java.util.Set;

/**
 * N6 - the rule-5 seam for hot liveness state, AND the named asdb-candidate slot from the plan's
 * v2 list (the lowest-risk slot to swap first: losing a heartbeat only costs one sweep interval).
 *
 * The whole failure-detection design lives behind this contract: a node with no live key IS down.
 * There is no timeout scheduler anywhere - key expiry is the detector. This interface exposes only
 * what that design needs; the 45s TTL is an implementation detail of N7, not part of the contract.
 *
 * Consumes: nothing - ours.
 *
 * Cross-module note: Module 3's OrphanRequeueSweep depends on THIS interface (isAlive) to decide
 * which nodes' jobs to requeue - rule 5 paying off across module boundaries.
 */
public interface HeartbeatStore {
	void recordHeartbeat(String nodeId, int load);
	boolean isAlive(String nodeId);
	Set<String> aliveNodeIds();
	Optional<Integer> loadOf(String nodeId);
}
