package ascore.realtime;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

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
 * TODO(averi): after B2+B3+A7 are implemented, build the test:
 *   1. connect, subscribe to /topic/telemetry/123
 *   2. POST a snapshot with placeId 123 (RestClient/TestRestTemplate with
 *      the dev-roblox-key header)
 *   3. assert the frame arrives within 3s
 *   4. POST one with placeId 456, assert NOTHING arrives on .../123
 *      (queue.poll times out null) - proves topic isolation.
 */
class RealtimeBroadcastTest {

	@Disabled("TODO(averi): implement per the class comment once B2, B3, and A7 exist")
	@Test
	void snapshotArrivesOnItsPlaceTopicOnly() { }

}
