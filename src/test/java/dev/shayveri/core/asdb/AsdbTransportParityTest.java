package dev.shayveri.core.asdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.shayveri.core.ingress.TelemetrySnapshot;

/**
 * The binary transport against a real asdb server, and against the text one.
 *
 * <p>WHAT THIS COVERS THAT {@code AbpCodecTest} CANNOT. That test proves the
 * bytes are right. This proves the server agrees: that a document written over
 * ABP is findable by an ASL query, that both transports produce the SAME stored
 * document, and that a pooled connection survives being reused. Encoding
 * correctness and protocol correctness are different claims and only one of
 * them can be checked without a socket.
 *
 * <p>Needs asdb running with both listeners, and is skipped rather than failed
 * without one:
 *
 * <pre>
 *   asdb telemetry.db --port 7070 --abp-port 7071
 * </pre>
 */
class AsdbTransportParityTest {

	private static final String HOST = "127.0.0.1";
	private static final int HTTP_PORT = 7070;
	private static final int ABP_PORT = 7071;

	private final AsdbClient http = new AsdbClient("http://" + HOST + ":" + HTTP_PORT, Duration.ofSeconds(2), Duration.ofSeconds(5));

	private AsdbBinaryClient binary() {
		return new AsdbBinaryClient(HOST, ABP_PORT, Duration.ofSeconds(2), Duration.ofSeconds(5), 8);
	}

	/** A collection name unique per run, so repeated runs do not accumulate. */
	private String collection;

	@BeforeEach
	void requireServer() {
		try (AsdbBinaryClient client = binary()) {
			assumeTrue(client.isHealthy(), "asdb is not running with --abp-port " + ABP_PORT + "; skipping");
		}
		collection = "parity_" + System.nanoTime();
		http.execute("create " + collection + " {}");
	}

	private static TelemetrySnapshot snapshot(String placeId) {
		return new TelemetrySnapshot(
				placeId, "job-a", 42, 58.5, "round-3",
				Map.of("kills", 7),
				Instant.ofEpochMilli(1754500000000L));
	}

	@Test
	@DisplayName("a binary insert is findable by an ASL query")
	void binaryInsertIsVisibleToAsl() {
		// the two ports are one database; if this fails they are not.
		try (AsdbBinaryClient client = binary()) {
			assertEquals(1, client.insert(collection, List.of(AsdbEntityMapper.toMap(snapshot("place-binary")))));
		}

		assertTrue(http.execute("from " + collection + " | where placeId == \"place-binary\"").contains("place-binary"));
	}

	@Test
	@DisplayName("both transports store the same document")
	void transportsAgree() {
		/*
		 * The claim that matters for the swap: switching
		 * shayveri.store.asdb.protocol must change how bytes travel and nothing
		 * about what is stored. Fields are compared individually so a failure
		 * names the field rather than dumping two documents.
		 */
		try (AsdbBinaryClient client = binary()) {
			client.insert(collection, List.of(AsdbEntityMapper.toMap(snapshot("via-binary"))));
		}
		http.execute(AsdbEntityMapper.insertStatement(collection, List.of(snapshot("via-text"))));

		try (AsdbBinaryClient client = binary()) {
			Map<String, Object> fromBinary = only(client, "via-binary");
			Map<String, Object> fromText = only(client, "via-text");

			assertEquals(fromText.keySet(), fromBinary.keySet(), "the two paths wrote different field sets");

			for (String field : fromText.keySet()) {
				if (field.equals("placeId")) {
					continue; // deliberately different, it is how they are told apart
				}
				assertEquals(fromText.get(field), fromBinary.get(field), "field " + field + " differs between transports");
			}
		}
	}

	@Test
	@DisplayName("an Instant lands as epoch millis on the binary path too")
	void instantIsEpochMillis() {
		try (AsdbBinaryClient client = binary()) {
			client.insert(collection, List.of(AsdbEntityMapper.toMap(snapshot("place-instant"))));
			assertEquals(1754500000000L, only(client, "place-instant").get("receivedAt"));
		}
	}

	@Test
	@DisplayName("a value that would be ASL syntax is stored as data")
	void injectionIsInertOnTheBinaryPath() {
		/*
		 * The text path survives this because AsdbEntityMapper.quote escapes it.
		 * The binary path survives it because nothing ever lexes the value.
		 * Worth testing end to end rather than only in the codec: it is the
		 * SERVER that must not treat it as syntax, and only the server can say.
		 */
		String hostile = "x\" } | delete //";

		try (AsdbBinaryClient client = binary()) {
			client.insert(collection, List.of(AsdbEntityMapper.toMap(snapshot(hostile))));
			assertEquals(hostile, only(client, hostile).get("placeId"), "the value must come back unchanged");
		}

		// and the delete stage must not have run
		assertTrue(http.execute("from " + collection).contains("placeId"));
	}

	@Test
	@DisplayName("a batch is one request, and all of it lands")
	void batchInsert() {
		// saveEvents takes this path, and it is the one worth 44x over HTTP.
		try (AsdbBinaryClient client = binary()) {
			List<Map<String, Object>> batch = java.util.stream.IntStream.range(0, 250)
					.mapToObj(i -> (Map<String, Object>) AsdbEntityMapper.toMap(snapshot("batch-" + i)))
					.toList();

			assertEquals(250, client.insert(collection, batch));
			assertEquals(250, client.execute("from " + collection).documents().size());
		}
	}

	@Test
	@DisplayName("a pooled connection is reused across many requests")
	void connectionsAreReused() {
		// the entire point of the protocol. If pooling were broken this would
		// still pass functionally, so the assertion is on the result and the
		// value of the test is that a desynchronised stream would fail it.
		try (AsdbBinaryClient client = binary()) {
			for (int i = 0; i < 200; i++) {
				assertEquals(1, client.insert(collection, List.of(AsdbEntityMapper.toMap(snapshot("reuse-" + i)))));
			}
			assertEquals(200, client.execute("from " + collection).documents().size());
		}
	}

	@Test
	@DisplayName("concurrent callers do not read each other's replies")
	void concurrentCallersAreIsolated() throws Exception {
		/*
		 * asdb replies in arrival order per connection, so two threads sharing
		 * one socket would interleave frames and read each other's answers.
		 * This is the test that says the pool is doing its job: 8 threads, and
		 * every single reply must be an affected-count of 1.
		 */
		try (AsdbBinaryClient client = binary()) {
			int threads = 8;
			int each = 25;
			CountDownLatch start = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(threads);
			AtomicReference<Throwable> failure = new AtomicReference<>();

			for (int t = 0; t < threads; t++) {
				final int id = t;
				Thread.ofVirtual().start(() -> {
					try {
						start.await();
						for (int i = 0; i < each; i++) {
							assertEquals(1, client.insert(collection, List.of(AsdbEntityMapper.toMap(snapshot("t" + id + "-" + i)))));
						}
					} catch (Throwable e) {
						failure.compareAndSet(null, e);
					} finally {
						done.countDown();
					}
				});
			}

			start.countDown();
			assertTrue(done.await(30, TimeUnit.SECONDS), "threads did not finish");

			if (failure.get() != null) {
				throw new AssertionError("a concurrent caller failed", failure.get());
			}

			assertEquals(threads * each, client.execute("from " + collection).documents().size());
		}
	}

	@Test
	@DisplayName("a server error is an exception, not a silent no-op")
	void errorsSurface() {
		// a telemetry write that vanishes leaves no trace anywhere, so failing
		// loudly matters more here than it might elsewhere.
		try (AsdbBinaryClient client = binary()) {
			assertThrows(AsdbClient.AsdbException.class,
					() -> client.insert("no_such_collection_" + System.nanoTime(), List.of(Map.of("a", 1))));

			// and the client is still usable afterwards
			assertEquals(1, client.insert(collection, List.of(AsdbEntityMapper.toMap(snapshot("after-error")))));
		}
	}

	@Test
	@DisplayName("an unreachable server is reported, not hung on")
	void unreachableServerFails() {
		try (AsdbBinaryClient dead = new AsdbBinaryClient(HOST, 1, Duration.ofMillis(300), Duration.ofMillis(300), 2)) {
			assertThrows(AsdbClient.AsdbException.class, () -> dead.insert("x", List.of(Map.of("a", 1))));
			assertTrue(!dead.isHealthy());
		}
	}

	/** The one document with this placeId, decoded. */
	private Map<String, Object> only(AsdbBinaryClient client, String placeId) {
		List<Map<String, Object>> docs = client
				.execute("from " + collection + " | where placeId == " + AsdbEntityMapper.quote(placeId))
				.documents();
		assertEquals(1, docs.size(), "expected exactly one document for placeId " + placeId);
		return docs.get(0);
	}
}
