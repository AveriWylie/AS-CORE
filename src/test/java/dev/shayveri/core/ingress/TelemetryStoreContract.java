package dev.shayveri.core.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import dev.shayveri.core.ingress.asdb.AsdbEntityMapper;

/**
 * The behaviour every {@link TelemetryStore} must have, written once and run
 * against every implementation.
 *
 * <p>WHY THIS SHAPE. The point of rule 5 was that the storage engine could be
 * swapped without anything above the seam noticing. That claim was never
 * actually tested: MongoTelemetryStore and AsdbTelemetryStore each had their
 * own tests asserting their own behaviour, so the two could drift apart
 * indefinitely and every test would stay green. A contract test is the thing
 * that makes "functionally equivalent" a measurement rather than an intention.
 *
 * <p>HOW TO READ A FAILURE HERE. A test failing in one subclass and passing in
 * the other is not a bug in the test. It is the two stores disagreeing, and the
 * disagreement is the finding. Either the implementation is wrong, or the
 * divergence is real and belongs in the DIVERGENCES list below rather than
 * being silently tolerated.
 *
 * <p>KNOWN DIVERGENCES, deliberately not asserted as equal:
 *
 * <ul>
 * <li><b>Generated ids.</b> Mongo fills a null {@code @Id} with an ObjectId.
 * asdb has no such notion and the mapper omits the field. Nothing in ingress
 * reads ids back, so this is invisible today and would matter to a read path.
 * <li><b>TTL.</b> Mongo enforces {@code @Indexed(expireAfter = "7d")} with a
 * TTL index it builds itself. asdb has no TTL index; its server runs a sweeper
 * configured by a command-line flag. Same intent, different mechanism, and only
 * one of them fails loudly when it is missing.
 * <li><b>Transactions.</b> Neither is atomic across documents, so a batch that
 * fails partway leaves the earlier documents written. This is a match rather
 * than a gap, and it is recorded so nobody assumes otherwise.
 * </ul>
 *
 * <p>NO TEST CLEARS A COLLECTION. Each test tags its documents with a UUID and
 * reads only those back. That keeps the suite safe to run against a shared or
 * long-lived database, and it means two implementations can be exercised
 * against the same server without interfering.
 */
abstract class TelemetryStoreContract {

	@Autowired
	protected TelemetryStore store;

	/** Which implementation is under test, for failure messages. */
	protected abstract String storeName();

	/** Every document in {@code collection} whose placeId matches, as plain maps. */
	protected abstract List<Map<String, Object>> findByPlaceId(String collection, String placeId);

	protected static String snapshots() {
		return AsdbEntityMapper.collectionOf(TelemetrySnapshot.class);
	}

	protected static String events() {
		return AsdbEntityMapper.collectionOf(GameEvent.class);
	}

	/** A marker unique to one test, so its documents can be found without clearing anything. */
	private static String marker() {
		return "contract-" + UUID.randomUUID();
	}

	private static TelemetrySnapshot snapshot(String placeId) {
		return new TelemetrySnapshot(
				placeId, "job-a", 42, 58.5, "round-3",
				Map.of("kills", 7),
				Instant.ofEpochMilli(1754500000000L));
	}

	private static GameEvent event(String placeId) {
		return new GameEvent(
				"DEATH", placeId, "job-a",
				Instant.ofEpochMilli(1754500000000L),
				null,
				Map.of("weapon", "sword"),
				Instant.ofEpochMilli(1754500001000L));
	}

	/* ---------- the contract ---------- */

	@Test
	@DisplayName("a saved snapshot is retrievable with its scalar fields intact")
	void savesASnapshot() {
		String placeId = marker();
		store.saveSnapshot(snapshot(placeId));
		List<Map<String, Object>> found = findByPlaceId(snapshots(), placeId);
		assertEquals(1, found.size(), storeName() + " stored the wrong number of snapshots");
		Map<String, Object> doc = found.get(0);
		assertEquals("job-a", doc.get("jobId"), storeName() + " lost jobId");
		assertEquals("round-3", doc.get("round"), storeName() + " lost round");
		// numbers are compared as strings: Mongo returns Integer/Double, asdb
		// returns Long/Double, and the contract is about the VALUE surviving,
		// not about which box the driver happened to choose.
		assertEquals("42", String.valueOf(doc.get("playerCount")), storeName() + " lost playerCount");
		assertEquals("58.5", String.valueOf(doc.get("serverFps")), storeName() + " lost serverFps");
	}

	@Test
	@DisplayName("receivedAt survives as an instant, not as a formatted string")
	void preservesTheTimestamp() {
		/*
		 * The one field the TTL story depends on. Stored as a string it would
		 * sort lexically, which breaks the moment a format changes and cannot
		 * be range-scanned by asdb's sweeper at all.
		 */
		String placeId = marker();
		store.saveSnapshot(snapshot(placeId));

		Object receivedAt = findByPlaceId(snapshots(), placeId).get(0).get("receivedAt");
		assertNotNull(receivedAt, storeName() + " dropped receivedAt");

		long millis = receivedAt instanceof java.util.Date date
				? date.toInstant().toEpochMilli()
				: Long.parseLong(String.valueOf(receivedAt));

		assertEquals(1754500000000L, millis, storeName() + " changed the instant");
	}

	@Test
	@DisplayName("a nested map round trips")
	void preservesNestedDocuments() {
		String placeId = marker();
		store.saveSnapshot(snapshot(placeId));
		Object metrics = findByPlaceId(snapshots(), placeId).get(0).get("customMetrics");
		assertTrue(metrics instanceof Map, storeName() + " did not store customMetrics as a document: " + metrics);
		assertEquals("7", String.valueOf(((Map<?, ?>) metrics).get("kills")), storeName() + " lost a nested value");
	}

	@Test
	@DisplayName("a batch of events all land")
	void savesABatch() {
		// the path saveEvents actually takes in production
		String placeId = marker();
		store.saveEvents(List.of(event(placeId), event(placeId), event(placeId)));
		assertEquals(3, findByPlaceId(events(), placeId).size(), storeName() + " lost documents from a batch");
	}

	@Test
	@DisplayName("an empty batch is a no-op, not an error")
	void emptyBatchIsANoOp() {
		// the service layer hands over whatever the request contained, so both
		// stores must tolerate nothing rather than making the caller check.
		store.saveEvents(List.of());
	}

	@Test
	@DisplayName("a null batch is a no-op, not an error")
	void nullBatchIsANoOp() {
		store.saveEvents(null);
	}

	@Test
	@DisplayName("a value that looks like query syntax is stored as data")
	void valuesAreNeverSyntax() {
		/*
		 * Telemetry values arrive from Roblox game servers, which is to say
		 * from outside. Mongo is immune structurally, since BSON never parses
		 * values. asdb's text path depends on AsdbEntityMapper.quote getting
		 * the escaping right, and its binary path is immune the same way Mongo
		 * is. Asserting it here means whichever transport is configured has to
		 * prove it.
		 */
		String placeId = marker() + "\" } | delete //";
		store.saveSnapshot(snapshot(placeId));

		List<Map<String, Object>> found = findByPlaceId(snapshots(), placeId);
		assertEquals(1, found.size(), storeName() + " did not store the hostile value verbatim");
		assertEquals(placeId, found.get(0).get("placeId"), storeName() + " altered the value");
	}
}
