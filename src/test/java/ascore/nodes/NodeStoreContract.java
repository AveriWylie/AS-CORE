package ascore.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What every NodeStore must do, run against asdb and Mongo. A test failing for one and
 * passing for the other is the two stores disagreeing. Each test uses its own nodeId, so
 * nothing is cleared and a long-lived database is safe.
 */
abstract class NodeStoreContract {

	@Autowired
	protected NodeStore store;

	// millisecond instants, since both stores keep millis and Instant.now() carries more
	private static final Instant REGISTERED = Instant.ofEpochMilli(1_700_000_000_000L);

	private static Node node(String id, String hostname) {
		return new Node(id, hostname, Map.of("blender", true, "gpu", Map.of("vram", 8)), 2, REGISTERED, REGISTERED);
	}

	@Test
	void savedNodeReadsBackField_forField() {
		String id = "node-" + UUID.randomUUID();
		store.save(node(id, "host-a"));

		Node read = store.findById(id).orElseThrow();
		assertEquals("host-a", read.getHostname());
		assertEquals(Map.of("blender", true, "gpu", Map.of("vram", 8)), read.getCapabilities());
		assertEquals(2, read.getMaxConcurrentJobs());
		assertEquals(REGISTERED, read.getRegisteredAt());
	}

	@Test
	void savingTheSameIdOverwrites() {
		String id = "node-" + UUID.randomUUID();
		store.save(node(id, "host-a"));
		store.save(node(id, "host-b"));

		assertEquals("host-b", store.findById(id).orElseThrow().getHostname());
		assertEquals(1, store.findAll().stream().filter(n -> n.getNodeId().equals(id)).count());
	}

	@Test
	void unknownIdIsEmpty() {assertTrue(store.findById("node-" + UUID.randomUUID()).isEmpty());}

}
