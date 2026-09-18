package ascore.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D5 - the 202 must not wait on storage.
 *
 * Lives outside Module1IntegrationTest because it needs no container: the store
 * here is a stub that blocks until the test releases it, so nothing is persisted
 * anywhere. That class is gated on Docker for its Mongo assertions.
 *
 * A latch rather than a sleep: the test asserts storage has NOT finished at the
 * moment the 202 arrives, which proves the write is off the request thread
 * without depending on how fast the machine is.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TelemetryAcceptIsAsyncTest {

	static final CountDownLatch release = new CountDownLatch(1);
	static final CountDownLatch finished = new CountDownLatch(1);


	@TestConfiguration
	static class BlockingStore {

		@Bean
		@Primary
		TelemetryStore blockingStore() {
			return new TelemetryStore() {

				@Override
				public void saveSnapshot(TelemetrySnapshot snapshot) {
					try {
						release.await(10, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					finished.countDown();
				}

				@Override
				public void saveEvents(List<GameEvent> events) { }
			};
		}
	}


	@Autowired
	private MockMvc mockMvc;

	@Test
	void acceptedBeforeStorageFinishes() throws Exception {
		mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", "dev-roblox-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"placeId\":\"p\",\"jobId\":\"j\",\"playerCount\":1,\"serverFps\":60.0}"))
				.andExpect(status().isAccepted());

		// the store is still blocked, so the 202 cannot have waited for it
		assertEquals(1, finished.getCount(), "storage finished before the response, so it ran on the request thread");

		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS), "storage never completed once released");
	}
}
