package ascore.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

// T1 and T6, plus the unregistered-heartbeat rule, against in-memory stores
class NodeServiceTest {

	private final InMemoryNodeStore nodes = new InMemoryNodeStore();
	private final InMemoryHeartbeatStore heartbeats = new InMemoryHeartbeatStore();
	private final NodeService service = new NodeService(nodes, heartbeats);

	private static NodeRegisterRequest request(String nodeId) {
		return new NodeRegisterRequest(nodeId, "lab-" + nodeId, Map.of("gpu", "RTX 3080"), 2);
	}

	/**
	 * T1. A second register with the same id is an overwrite, not a second node, and it
	 * keeps the original registeredAt while moving lastRegisteredAt forward.
	 */
	@Test
	void reRegistrationIsIdempotent() throws InterruptedException {
		Node first = service.register(request("n1"));
		Thread.sleep(2);
		Node second = service.register(request("n1"));

		assertEquals(1, nodes.findAll().size());
		assertEquals(first.getRegisteredAt(), second.getRegisteredAt());
		assertTrue(second.getLastRegisteredAt().isAfter(first.getLastRegisteredAt()));
	}

	@Test
	void heartbeatFromUnregisteredNodeIsRejected() {
		assertFalse(service.heartbeat("ghost", new HeartbeatRequest(0, null)));
		assertTrue(heartbeats.aliveNodeIds().isEmpty());
	}

	// T6. Registered without a heartbeat is DOWN with no load; with one, UP with its load
	@Test
	void listMergesRegistryWithLiveness() {
		service.register(request("up"));
		service.register(request("down"));
		service.heartbeat("up", new HeartbeatRequest(3, null));

		Map<String, NodeView> byId = service.listWithStatus().stream()
				.collect(Collectors.toMap(v -> v.node().getNodeId(), Function.identity()));

		assertEquals(NodeView.Status.UP, byId.get("up").status());
		assertEquals(3, byId.get("up").load());
		assertEquals(NodeView.Status.DOWN, byId.get("down").status());
		assertNull(byId.get("down").load());
	}

}
