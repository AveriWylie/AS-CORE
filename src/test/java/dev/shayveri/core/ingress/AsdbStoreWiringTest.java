package dev.shayveri.core.ingress;

import dev.shayveri.core.asdb.AsdbClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the real Spring context with the asdb backend selected and writes
 * through the real {@link TelemetryStore} bean.
 *
 * <p>WHAT THIS COVERS THAT NOTHING ELSE DID. The mapper tests prove the
 * generated ASL is right, and driving that ASL at a server proves the server
 * accepts it. Neither touches Spring. This is the only test that exercises
 * the wiring: that {@code shayveri.store=asdb} actually selects
 * AsdbTelemetryStore over MongoTelemetryStore, that the bean constructs (which
 * runs ensureSchema against a live server), and that the context starts at all
 * with Mongo on the classpath but unreachable.
 *
 * <p>Requires a running asdb server. Skipped rather than failed when there
 * isn't one, so the suite stays green on a machine without it.
 */
@SpringBootTest
@TestPropertySource(properties = {"shayveri.store=asdb", "shayveri.store.asdb.url=http://127.0.0.1:7070",})
class AsdbStoreWiringTest {

	@Autowired
	private TelemetryStore store;

	@Test
	@DisplayName("shayveri.store=asdb selects the asdb implementation")
	void theAsdbStoreIsWiredIn() {
		assertInstanceOf(AsdbTelemetryStore.class, store, "expected the asdb backend, got " + store.getClass().getName());
	}

	@Test
	@DisplayName("a snapshot written through the Spring bean lands in asdb")
	void snapshotRoundTrips() {

		AsdbTelemetryStore asdb = assertInstanceOf(AsdbTelemetryStore.class, store);
		assumeTrue(asdb.isHealthy(), "no asdb server on 127.0.0.1:7070");
		String marker = "wiring-" + System.nanoTime();

		store.saveSnapshot(new TelemetrySnapshot(
				marker, "job-wiring", 7, 59.5, "r1",
				Map.of("kills", 3),
				Instant.now()));

		AsdbClient client = new AsdbClient("http://127.0.0.1:7070", java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5));
		String body = client.execute("from telemtry_snapshots | where placeId == \"" + marker + "\" | select jobId, playerCount");
		assertEquals("{\"count\":1,\"documents\":[{\"jobId\":\"job-wiring\",\"playerCount\":7}]}", body);
	}

	@Test
	@DisplayName("an empty event batch is a no-op, not a malformed statement")
	void emptyBatchIsSafe() {
		store.saveEvents(List.of());
	}
}
