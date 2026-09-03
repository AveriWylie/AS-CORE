package dev.shayveri.core.ingress;

import dev.shayveri.core.asdb.AsdbClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * The whole path, for real: HTTP POST into SHAYVERI, out to asdb.
 *
 * <p>WHAT THIS COVERS THAT NOTHING ELSE DID. Every other test stops short of
 * the edge. The mapper tests check generated strings, the wiring test calls the
 * store bean directly, and driving ASL at the server skips Java entirely. None
 * of them go through the controller, the validation annotations, the security
 * filter, or the async handoff in TelemetryService. This does.
 *
 * <p>THE ASYNC WRINKLE. acceptSnapshot hands the write to an Executor and
 * returns 202 immediately, so the row is NOT in the database when the response
 * arrives. Polling rather than asserting once is not flakiness-papering, it is
 * the actual contract: 202 means accepted, not stored.
 *
 * <p>Needs a running asdb server; skipped rather than failed without one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"shayveri.store=asdb", "shayveri.store.asdb.url=http://127.0.0.1:7070",})
class TelemetryHttpToAsdbTest {

	private static final String ROBLOX_KEY = "dev-roblox-key";

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	private final AsdbClient asdb = new AsdbClient("http://127.0.0.1:7070", Duration.ofSeconds(2), Duration.ofSeconds(5));

	private ResponseEntity<String> post(String path, String json, String key) {

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		if (key != null) {
			headers.set("X-Api-Key", key);
		}

		return rest.exchange("http://localhost:" + port + path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
	}

	/** Waits for the async write to land, since 202 does not mean stored. */
	private String awaitQuery(String asl, String expectSubstring) {

		String body = "";

		for (int i = 0; i < 50; i++) {
			body = asdb.execute(asl);

			if (body.contains(expectSubstring)) {
				return body;
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return body;
	}

	@Test
	@DisplayName("POST /api/telemetry lands a snapshot in asdb")
	void snapshotReachesAsdb() {

		assumeTrue(asdb.isHealthy(), "no asdb server on 127.0.0.1:7070");
		String marker = "http-" + System.nanoTime();

		// plain concatenation rather than a text block: a text block's opening
		// """ must be followed by a line terminator, which a reformat can
		// silently remove and turn into a compile error.
		String json = ("{\"placeId\":\"%s\",\"jobId\":\"job-http\",\"playerCount\":11,"
					+ "\"serverFps\":57.25,\"round\":\"r9\",\"customMetrics\":{\"kills\":2}}")
					.formatted(marker);

		ResponseEntity<String> response = post("/api/telemetry", json, ROBLOX_KEY);
		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(), "the edge returns 202, not 200: the write is queued, not done");
		String stored = awaitQuery("from telemtry_snapshots | where placeId == \"" + marker + "\" | select jobId, playerCount", "job-http");
		assertEquals("{\"count\":1,\"documents\":[{\"jobId\":\"job-http\",\"playerCount\":11}]}", stored);
	}

	@Test
	@DisplayName("POST /api/telemetry/events lands a batch in asdb")
	void eventBatchReachesAsdb() {

		assumeTrue(asdb.isHealthy(), "no asdb server on 127.0.0.1:7070");
		String marker = "evt-" + System.nanoTime();
		
		String json = ("[{\"type\":\"kill\",\"placeId\":\"%s\",\"jobId\":\"j1\","
					+ "\"occurredAt\":\"2026-08-06T12:00:00Z\"},"
					+ "{\"type\":\"death\",\"placeId\":\"%s\",\"jobId\":\"j1\","
					+ "\"occurredAt\":\"2026-08-06T12:00:01Z\"}]")
					.formatted(marker, marker);

		ResponseEntity<String> response = post("/api/telemetry/events", json, ROBLOX_KEY);
		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
		String stored = awaitQuery("from game_events | where placeId == \"" + marker + "\" | select type", "kill");
		assertTrue(stored.startsWith("{\"count\":2,"), "both events should land: " + stored);
	}

	@Test
	@DisplayName("no API key is rejected before anything reaches the store")
	void unauthenticatedIsRejected() {
		ResponseEntity<String> response = post("/api/telemetry", "{\"placeId\":\"nope\",\"jobId\":\"j\"}", null);
		assertTrue(response.getStatusCode().is4xxClientError(), "expected a 4xx without a key, got " + response.getStatusCode());
	}

	@Test
	@DisplayName("@Valid rejects a malformed body with 400, not 202")
	void validationRejectsBadBody() {
		assumeTrue(asdb.isHealthy(), "no asdb server on 127.0.0.1:7070");
		// missing required fields: the constraints on the request record are
		// only active because the controller says @Valid
		ResponseEntity<String> response = post("/api/telemetry", "{}", ROBLOX_KEY);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "an invalid body must not be accepted, got " + response.getStatusCode());
	}

	@Test
	@DisplayName("a non-ROBLOX key is rejected: telemetry is ROBLOX-only")
	void wrongRoleIsRejected() {
		// A8 step 4. DASH and NODE are valid keys, so this proves the rule is
		// about ROLE and not merely about being authenticated.
		ResponseEntity<String> response = post("/api/telemetry", "{\"placeId\":\"x\",\"jobId\":\"j\",\"playerCount\":1,\"serverFps\":60.0}", "dev-dash-key");
		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "a valid but wrong-role key must be forbidden, got " + response.getStatusCode());
	}
}
