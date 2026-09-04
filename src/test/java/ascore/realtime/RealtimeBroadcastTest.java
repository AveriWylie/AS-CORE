package ascore.realtime;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import ascore.asdb.AsdbBinaryClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * D4 - proves the broadcast path: subscribe a real STOMP client, publish,
 * assert arrival (and NON-arrival on other topics).
 *
 * Consumes (not ours) - Spring's STOMP client, the same stack the React
 * dashboard will use:
 *   WebSocketStompClient(new StandardWebSocketClient())
 *       - the client object; call setMessageConverter(
 *         new MappingJackson2MessageConverter()) so payloads deserialize.
 *   stompClient.connectAsync("ws://localhost:" + port + "/ws", sessionHandler)
 *       - returns CompletableFuture<StompSession>; .get(3, SECONDS) it.
 *   session.subscribe("/topic/telemetry/123", frameHandler)
 *       - frameHandler receives frames; hand payloads to the test thread
 *         via a BlockingQueue and assert with queue.poll(3, SECONDS).
 * Test class setup: @SpringBootTest(webEnvironment = RANDOM_PORT) +
 * @LocalServerPort int port - a real server socket this time (WebSockets
 * need one; MockMvc cannot carry a handshake).
 *
 * Needs asdb running, and skips rather than fails without it. TelemetryService
 * saves before it publishes, so a store that throws means no broadcast ever
 * happens and this would fail for a reason that has nothing to do with D4.
 */
@EnabledIf("asdbReachable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeBroadcastTest {

	static boolean asdbReachable() {
		try (AsdbBinaryClient probe = new AsdbBinaryClient("127.0.0.1", 7071, Duration.ofSeconds(2), Duration.ofSeconds(5), 2)) {
			return probe.isHealthy();
		} catch (Exception e) {
			return false;
		}
	}

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	private static final String BODY = """
			{"placeId":"%s","jobId":"job-1","playerCount":12,"serverFps":58.5,"round":"round-4"}""";

	private void post(String placeId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Api-Key", "dev-roblox-key");
		rest.postForEntity("http://localhost:" + port + "/api/telemetry",
				new HttpEntity<>(BODY.formatted(placeId), headers), Void.class);
	}

	@Test
	void snapshotArrivesOnItsPlaceTopicOnly() throws Exception {

		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new MappingJackson2MessageConverter());

		StompSession session = client
				.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { })
				.get(5, TimeUnit.SECONDS);

		BlockingQueue<Object> received = new LinkedBlockingQueue<>();

		session.subscribe("/topic/telemetry/123", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {return Object.class;}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {received.add(payload);}
		});

		// the snapshot this subscriber asked for
		post("123");
		Object frame = received.poll(5, TimeUnit.SECONDS);
		assertNotNull(frame, "no frame arrived on /topic/telemetry/123");

		// a different place must NOT reach this subscription. Topic isolation is
		// the half that a naive broadcast still passes without.
		post("456");
		assertNull(received.poll(2, TimeUnit.SECONDS), "a snapshot for 456 leaked onto the 123 topic");

		session.disconnect();
	}

}
