package dev.shayveri.core.ingress;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1 (web layer) + D2 + D3 + D5 - the integration suite: boots the REAL
 * application against a REAL MongoDB (dev rule 2: no mocks). Requires
 * Docker running (Testcontainers starts/stops the Mongo container itself;
 * docker-compose is not involved here).
 *
 * Consumes (not ours):
 *   @SpringBootTest        - boots the full application context for the test.
 *   @AutoConfigureMockMvc + MockMvc
 *                          - drive HTTP requests through the real filter
 *                            chain + controllers WITHOUT a network socket:
 *       mockMvc.perform(post("/api/telemetry")
 *                   .header("X-Api-Key", "dev-roblox-key")
 *                   .contentType(MediaType.APPLICATION_JSON)
 *                   .content("{...json...}"))
 *              .andExpect(status().isAccepted());
 *       (static imports: MockMvcRequestBuilders.post,
 *        MockMvcResultMatchers.status/jsonPath)
 *   Testcontainers:
 *       @Testcontainers    - JUnit extension managing container lifecycle.
 *       @Container         - this field is a managed container.
 *       MongoDBContainer   - a throwaway real Mongo in Docker.
 *       @ServiceConnection - Spring Boot reads the container's host/port and
 *                            wires spring.data.mongodb.uri automatically -
 *                            no manual property plumbing.
 *   For D3 assertions, inject MongoTemplate and query the collections
 *   directly (mongoTemplate.findAll(TelemetrySnapshot.class),
 *   mongoTemplate.indexOps("telemetry_snapshots").getIndexInfo() for the
 *   TTL check).
 *
 * Enable each test (remove @Disabled) as its units land. Suggested order
 * follows the blueprint's build order.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class Module1IntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

	@Autowired
	MockMvc mockMvc;

	// ---- D2: security --------------------------------------------------

	@Disabled("TODO(averi): after A8 - no X-Api-Key header -> expect 4xx (unauthenticated)")
	@Test
	void telemetryWithoutKeyIsRejected() {
		// TODO(averi): perform the POST with no header; andExpect 401/403.
	}

	@Disabled("TODO(averi): after A8 step 4 - dev-dash-key on /api/telemetry -> 403 (wrong role)")
	@Test
	void telemetryWithDashKeyIsRejected() {
	}

	@Disabled("TODO(averi): after A8 - dev-roblox-key + valid body -> 202")
	@Test
	void telemetryWithRobloxKeyIsAccepted() {
	}

	// ---- D1 (web layer): validation through the real pipeline ----------

	@Disabled("TODO(averi): after C2+A8 - body missing placeId -> 400, jsonPath $.fieldErrors.placeId exists, and NOT a 500")
	@Test
	void missingPlaceIdGives400WithFieldError() {
	}

	@Disabled("TODO(averi): after C2+A8 - garbage body 'not json{' -> clean 400 ApiError")
	@Test
	void garbageBodyGives400() {
	}

	// ---- D3: persistence ------------------------------------------------

	@Disabled("TODO(averi): after A6 - accepted snapshot appears in telemetry_snapshots with receivedAt set. Async tip: poll briefly (e.g. Awaitility or a small retry loop) - the write happens on another thread")
	@Test
	void acceptedSnapshotIsPersisted() {
	}

	@Disabled("TODO(averi): after A3 + auto-index-creation - telemetry_snapshots has a TTL index of 7 days; game_events has none")
	@Test
	void ttlIndexExistsOnlyOnSnapshots() {
	}

	@Disabled("TODO(averi): after A6 - batch of 3 events -> 3 documents in game_events")
	@Test
	void eventBatchPersistsAllEvents() {
	}

	// ---- D5: async behavior ----------------------------------------------

	@Disabled("TODO(averi): after A7 - the 202 must not wait on storage. One approach: a test TelemetryStore bean whose saveSnapshot sleeps 5s; assert the mockMvc call returns in well under 1s")
	@Test
	void acceptReturnsBeforePersistenceCompletes() {
	}

}
