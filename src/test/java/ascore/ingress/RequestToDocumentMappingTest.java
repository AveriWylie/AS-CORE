package ascore.ingress;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Conversion unit tests - the request -> document mapping (A1->A3, A2->A4).
 *
 * This is the "slight integration" between a request and its document: it
 * exercises TWO classes (the request record + the document's from() factory),
 * but runs as a PURE unit test - no Spring, no Mongo, no Docker, no server.
 * Just: build a request object, call from(), assert the document's getters.
 *
 * It fills a real gap in the blueprint test plan:
 *   D1 tests validation (the request's annotations)
 *   D3 tests persistence (needs Mongo + Testcontainers)
 *   ...but the from() MAPPING itself had no fast test. This is it.
 *
 * What each test proves: every request field lands in the right document
 * field, receivedAt is stamped from the passed Instant (not the client), and
 * id stays null until Mongo assigns it.
 *
 * Consumes (not ours): JUnit 5 only (@Test, assertions). Nothing else.
 *
 * NOTE: these will not COMPILE until A3 (TelemetrySnapshot) and A2
 * (GameEventRequest) compile with the getter names asserted below - which
 * also makes this file a spec for what those getters must be named
 * (e.g. getReceivedAt, not getRecievedAt). Remove @Disabled per test as its
 * document compiles.
 */
class RequestToDocumentMappingTest {

    // ================= A1 -> A3 : TelemetrySnapshot.from =================

    @Test
    void snapshotFromCopiesEveryFieldAndStampsReceivedAt() {

        Instant received = Instant.parse("2026-07-08T12:00:00Z"); // arrange: a fully-populated request + a server receipt time

        var request = new TelemetrySnapshotRequest("8271", "job-1", 12, 58.5, "round-4", Map.of("zombies", 30));
        var doc = TelemetrySnapshot.from(request, received); // act: the conversion under test

        // assert: every field mapped through, plus the server field
        assertEquals("8271", doc.getPlaceId());
        assertEquals("job-1", doc.getJobId());
        assertEquals(12, doc.getPlayerCount());
        assertEquals(58.5, doc.getServerFps());
        assertEquals("round-4", doc.getRound());
        assertEquals(Map.of("zombies", 30), doc.getCustomMetrics());
        assertEquals(received, doc.getReceivedAt()); // stamped by CORE, not client
        assertNull(doc.getId()); // Mongo assigns id later
    }

    // ================= A2 -> A4 : GameEvent.from =================

    @Test
    void eventFromCopiesEveryFieldAndStampsReceivedAt() {

        Instant occurred = Instant.parse("2026-07-08T12:00:00Z");   // client clock
        Instant received = Instant.parse("2026-07-08T12:00:01Z");   // server clock (later)

        var position = new GameEventRequest.Position(12.0, 3.5, -40.0);
        var request = new GameEventRequest("PLAYER_DEATH", "8271", "job-1", occurred, position, Map.of("weapon", "axe"));
        var doc = GameEvent.from(request, received);

        assertEquals("PLAYER_DEATH", doc.getType());
        assertEquals("8271", doc.getPlaceId());
        assertEquals("job-1", doc.getJobId());
        assertEquals(occurred, doc.getOccurredAt());   // client clock preserved
        assertEquals(received, doc.getReceivedAt());   // server clock, different value
        assertEquals(position, doc.getPosition());
        assertEquals(Map.of("weapon", "axe"), doc.getData());
        assertNull(doc.getId());
    }

    @Test
    void eventWithoutPositionKeepsPositionNull() {

        Instant occurred = Instant.parse("2026-07-08T12:00:00Z");
        Instant received = Instant.parse("2026-07-08T12:00:01Z");

        // a round starting has no location; position is optional, not absent-as-zero
        var request = new GameEventRequest("ROUND_START", "8271", "job-1", occurred, null, Map.of("round", 4));
        var doc = GameEvent.from(request, received);

        assertNull(doc.getPosition());
        assertEquals("ROUND_START", doc.getType());
        assertEquals(received, doc.getReceivedAt());
    }
}
