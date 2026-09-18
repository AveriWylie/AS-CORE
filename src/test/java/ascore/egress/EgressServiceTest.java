package ascore.egress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * T1 to T5. The RestClient is bound to MockRestServiceServer, so every request is
 * scripted and nothing leaves the machine. Backoff is zero, so retries are instant.
 */
class EgressServiceTest {

	private static final String URL = "https://apis.roblox.com/messaging-service/v1/universes/123/topics/shayveri-config";

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final OpenCloudProperties props = properties();
	private final OpenCloudClient client = new OpenCloudClient(builder, props, new ObjectMapper());
	private final List<Map<?, ?>> published = new ArrayList<>();
	private final AtomicLong clock = new AtomicLong();

	private EgressService service(int capacity) {
		return new EgressService(client, (topic, payload) -> published.add(Map.of("topic", topic, "payload", payload)),
				props, new TokenBucket(capacity, 1, clock::get));
	}

	private static OpenCloudProperties properties() {
		OpenCloudProperties props = new OpenCloudProperties();
		props.setApiKey("test-key");
		props.setUniverseId("123");
		props.setBackoffBaseMs(0);
		return props;
	}

	private boolean publishedDelivery(String delivery) {
		return published.stream().anyMatch(p -> p.get("payload") instanceof Map<?, ?> m && delivery.equals(m.get("delivery")));
	}

	private boolean alerted() {return published.stream().anyMatch(p -> p.get("topic").equals("/topic/alerts"));}

	// T1
	@Test
	void sendsOnlyThePointerUnder1KB() {
		String body = client.body(7);

		assertEquals("{\"message\":\"{\\\"v\\\":7}\"}", body);
		assertTrue(body.length() < OpenCloudClient.MAX_BYTES);
	}

	// T2
	@Test
	void successIsOneRequestAndPushed() {
		server.expect(times(1), requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-api-key", "test-key"))
				.andExpect(content().json("{\"message\":\"{\\\"v\\\":3}\"}"))
				.andRespond(withSuccess());

		service(10).publishConfigActivated("p1", 3);

		server.verify();
		assertTrue(publishedDelivery("PUSHED"));
	}

	// T3
	@Test
	void twoFailuresThenSuccessIsThreeRequests() {
		server.expect(times(2), requestTo(URL)).andRespond(withServerError());
		server.expect(times(1), requestTo(URL)).andRespond(withSuccess());

		service(10).publishConfigActivated("p1", 3);

		server.verify();
		assertTrue(publishedDelivery("PUSHED"));
		assertFalse(alerted());
	}

	// T4, the acceptance: a bad key degrades loudly and never throws into Module 4
	@Test
	void badKeyDegradesWithoutThrowing() {
		server.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertDoesNotThrow(() -> service(10).publishConfigActivated("p1", 3));

		server.verify();
		assertTrue(publishedDelivery("DEGRADED"));
		assertTrue(alerted());
	}

	// T5. The clock never moves, so the second push finds the bucket empty and never reaches the wire
	@Test
	void emptyBucketNeverFiresEarly() {
		server.expect(times(1), requestTo(URL)).andRespond(withSuccess());
		EgressService egress = service(1);

		egress.publishConfigActivated("p1", 1);
		egress.publishConfigActivated("p1", 2);

		server.verify();
		assertTrue(publishedDelivery("DEGRADED"));
	}

	@Test
	void unconfiguredPushDegradesWithoutARequest() {
		props.setApiKey("");
		server.expect(never(), requestTo(URL));

		service(10).publishConfigActivated("p1", 1);

		server.verify();
		assertTrue(alerted());
	}

	@Test
	void bucketRefillsWithTime() {
		TokenBucket bucket = new TokenBucket(1, 2, clock::get);

		assertTrue(bucket.tryAcquire());
		assertFalse(bucket.tryAcquire());
		clock.addAndGet(500_000_000L);
		assertTrue(bucket.tryAcquire());
	}

}
