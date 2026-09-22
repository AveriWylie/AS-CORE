package ascore.realtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// only a DASH key in the CONNECT frame gets a session; the handshake itself stays open
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompAuthTest {

	@LocalServerPort
	private int port;

	private StompSession connect(String key) throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		StompHeaders headers = new StompHeaders();
		if (key != null) headers.add("X-Api-Key", key);
		return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() { })
				.get(5, TimeUnit.SECONDS);
	}

	@Test
	void noKeyIsRefused() {assertThrows(ExecutionException.class, () -> connect(null));}

	@Test
	void robloxKeyIsRefused() {assertThrows(ExecutionException.class, () -> connect("dev-roblox-key"));}

	@Test
	void dashKeyConnects() throws Exception {
		StompSession session = connect("dev-dash-key");
		assertTrue(session.isConnected());
		session.disconnect();
	}

}
