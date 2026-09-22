package ascore.observability;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import ascore.ingress.GameEvent;
import ascore.ingress.TelemetrySnapshot;
import ascore.ingress.TelemetryStore;
import ascore.jobs.InMemoryJobStore;
import ascore.jobs.InMemoryQueueStore;
import ascore.jobs.JobStore;
import ascore.jobs.QueueStore;
import ascore.overrides.ActiveConfigCache;
import ascore.overrides.ConfigStore;
import ascore.overrides.InMemoryActiveConfigCache;
import ascore.overrides.InMemoryConfigStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Metrics over HTTP, and a mutation surviving a broken audit store. Every store is in memory and the audit store always
 * throws, so each mutation here also proves a lost audit write does not fail it.
 *
 * AutoConfigureObservability is needed because Spring Boot tests leave the Prometheus
 * registry off by default.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ObservabilityHttpTest {


	@TestConfiguration
	static class InMemory {

		@Bean
		@Primary
		TelemetryStore discardingTelemetryStore() {
			return new TelemetryStore() {

				@Override
				public void saveSnapshot(TelemetrySnapshot snapshot) { }

				@Override
				public void saveEvents(List<GameEvent> events) { }
			};
		}

		@Bean
		@Primary
		JobStore inMemoryJobStore() {return new InMemoryJobStore();}

		@Bean
		@Primary
		QueueStore inMemoryQueueStore() {return new InMemoryQueueStore();}

		@Bean
		@Primary
		ConfigStore inMemoryConfigStore() {return new InMemoryConfigStore();}

		@Bean
		@Primary
		ActiveConfigCache inMemoryActiveConfigCache() {return new InMemoryActiveConfigCache();}

		@Bean
		@Primary
		AuditStore brokenAuditStore() {
			return new AuditStore() {

				@Override
				public void record(AuditRecord record) {throw new IllegalStateException("audit store down");}

				@Override
				public List<AuditRecord> query(Instant from, Instant to, Optional<String> action) {return List.of();}
			};
		}
	}


	@Autowired
	private MockMvc mockMvc;

	// the acceptance
	@Test
	void prometheusServesOurMetrics() throws Exception {
		mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", "dev-roblox-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"placeId\":\"p\",\"jobId\":\"j\",\"playerCount\":1,\"serverFps\":60.0}"))
				.andExpect(status().isAccepted());
		mockMvc.perform(post("/api/jobs")
						.header("X-Api-Key", "dev-dash-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"CUSTOM\",\"mapId\":\"m\"}"))
				.andExpect(status().isAccepted());

		mockMvc.perform(get("/actuator/prometheus").header("X-Api-Key", "dev-dash-key"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("shayveri_telemetry_ingest_total")))
				.andExpect(content().string(containsString("shayveri_queue_depth{type=\"CUSTOM\"} 1.0")))
				.andExpect(content().string(containsString("shayveri_job_transitions_total")));
	}

	@Test
	void configSaveSucceedsWhileAuditIsDown() throws Exception {
		mockMvc.perform(put("/api/config")
						.header("X-Api-Key", "dev-dash-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"placeId\":\"t3\",\"namespace\":\"spawns\",\"values\":{\"zombieSpeed\":16}}"))
				.andExpect(status().isOk());
	}

}
