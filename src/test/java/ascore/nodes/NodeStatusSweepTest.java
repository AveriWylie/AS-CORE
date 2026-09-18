package ascore.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * T4. The sweep publishes transitions and nothing else. A steady node publishes once
 * when it comes up and once when it dies, never on the sweeps in between.
 */
class NodeStatusSweepTest {

	private final InMemoryHeartbeatStore heartbeats = new InMemoryHeartbeatStore();
	private final List<String> topics = new ArrayList<>();
	private final List<Object> payloads = new ArrayList<>();
	private final NodeStatusSweep sweep = new NodeStatusSweep(heartbeats, (topic, payload) -> {
		topics.add(topic);
		payloads.add(payload);
	});

	@Test
	void publishesOnlyTransitions() {
		heartbeats.recordHeartbeat("n1", 0);
		sweep.sweep();
		assertEquals(List.of(Map.of("nodeId", "n1", "status", "UP")), payloads);

		payloads.clear();
		sweep.sweep();
		assertTrue(payloads.isEmpty(), "a node that stayed up published again");

		heartbeats.expire("n1");
		sweep.sweep();
		assertEquals(List.of(Map.of("nodeId", "n1", "status", "DOWN")), payloads);

		assertTrue(topics.stream().allMatch("/topic/nodes"::equals));
	}

}
