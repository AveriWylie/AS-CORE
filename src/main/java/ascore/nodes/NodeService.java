package ascore.nodes;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * N8 - node logic.
 *   register(req): upsert via NodeStore (N4), stamp lastRegisteredAt; returns the SAME result for
 *       first and repeat registration - idempotency is inherited from the natural-key document
 *       (N3), this method just must not add non-idempotent side effects.
 *   heartbeat(id, req): HeartbeatStore.recordHeartbeat; UNKNOWN id -> reject (agents must register
 *       before heartbeating - a heartbeat for an unregistered node is a bug on the agent side).
 *   listWithStatus(): NodeStore.findAll() merged with HeartbeatStore liveness -> per node
 *       {node, status UP|DOWN, load}. Status is COMPUTED AT READ TIME from key existence, never
 *       stored - so it can never be stale.
 * Consumes: @Service; NodeStore (N4), HeartbeatStore (N6) - interfaces only.
 */
@Service
public class NodeService {

	private final NodeStore nodes;
	private final HeartbeatStore heartbeats;

	public NodeService(NodeStore nodes, HeartbeatStore heartbeats) {
		this.nodes = nodes;
		this.heartbeats = heartbeats;
	}

	public Node register(NodeRegisterRequest request) {
		Instant now = Instant.now();
		Instant registeredAt = nodes.findById(request.nodeId()).map(Node::getRegisteredAt).orElse(now);
		Node node = Node.from(request, registeredAt, now);
		nodes.save(node);
		return node;
	}

	// false for an unregistered node, which the controller turns into a 404
	public boolean heartbeat(String nodeId, HeartbeatRequest request) {
		if (nodes.findById(nodeId).isEmpty()) return false;
		heartbeats.recordHeartbeat(nodeId, request.currentLoad());
		return true;
	}

	public List<NodeView> listWithStatus() {
		Set<String> alive = heartbeats.aliveNodeIds();
		return nodes.findAll().stream()
				.map(n -> alive.contains(n.getNodeId())
						? new NodeView(n, NodeView.Status.UP, heartbeats.loadOf(n.getNodeId()).orElse(null))
						: new NodeView(n, NodeView.Status.DOWN, null))
				.toList();
	}

}
