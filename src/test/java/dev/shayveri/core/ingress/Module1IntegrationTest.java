package dev.shayveri.core.ingress;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
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
/*
 * SKIP, do not fail, when there is no container runtime.
 *
 * Testcontainers starts a real MongoDB in Docker, so without a daemon this
 * whole class dies with initializationError and the suite is permanently red.
 * A suite that always fails trains you to ignore it, which costs more than the
 * coverage this class provides.
 *
 * @EnabledIf rather than an assumption in @BeforeAll: the Testcontainers
 * extension starts @Container fields in its own beforeAll callback, which runs
 * BEFORE any @BeforeAll method, so an assumption there never gets the chance to
 * fire. @EnabledIf is an ExecutionCondition, evaluated before extensions
 * initialise anything.
 *
 * Nothing is disabled permanently: install Docker (or `colima start`) and these
 * run again on the next `./gradlew test` with no code change. This is also the
 * ONLY thing in the project needing Docker; the asdb tests talk to a native
 * binary and need no container runtime.
 */
@EnabledIf("dockerAvailable")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class Module1IntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

	static boolean dockerAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		} catch (Throwable t) {
			return false;
		}
	}


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

	/*
	 * C2 has landed, so these two run.
	 *
	 * The same two assertions also live in
	 * dev.shayveri.core.common.ErrorsAreUniformTest, which is NOT gated on
	 * Docker. That is deliberate rather than duplication: error handling
	 * touches no database, so gating it behind a container runtime would mean
	 * the error contract is only verified on machines that happen to have
	 * Docker. Here the same requests run against the full stack with a real
	 * Mongo behind it, which is what this class is for.
	 */
	@Test
	void missingPlaceIdGives400WithFieldError() throws Exception {
		mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", "dev-roblox-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"jobId\":\"job-a\",\"playerCount\":42,\"serverFps\":58.5}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.placeId").exists());
	}

	@Test
	void garbageBodyGives400() throws Exception {
		mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", "dev-roblox-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("not json{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("malformed request body"));
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
