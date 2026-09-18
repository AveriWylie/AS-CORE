package ascore.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import ascore.jobs.InMemoryJobStore;
import ascore.jobs.InMemoryQueueStore;
import ascore.jobs.JobStore;
import ascore.jobs.QueueStore;
import ascore.nodes.HeartbeatRequest;
import ascore.nodes.HeartbeatStore;
import ascore.nodes.InMemoryHeartbeatStore;
import ascore.nodes.InMemoryNodeStore;
import ascore.nodes.NodeRegisterRequest;
import ascore.nodes.NodeService;
import ascore.nodes.NodeStatusSweep;
import ascore.nodes.NodeStore;
import ascore.overrides.ConfigStore;
import ascore.overrides.InMemoryConfigStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * T2 and T3 on a real server. Every store is in memory, so the snapshot and the sweep
 * run without Mongo or Redis, and a node is made DOWN by expiring its heartbeat by hand.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SnapshotTest {


	@TestConfiguration
	static class InMemory {

		@Bean
		@Primary
		NodeStore inMemoryNodeStore() {return new InMemoryNodeStore();}

		@Bean
		@Primary
		InMemoryHeartbeatStore inMemoryHeartbeatStore() {return new InMemoryHeartbeatStore();}

		@Bean
		@Primary
		JobStore inMemoryJobStore() {return new InMemoryJobStore();}

		@Bean
		@Primary
		QueueStore inMemoryQueueStore() {return new InMemoryQueueStore();}

		@Bean
		@Primary
		ConfigStore inMemoryConfigStore() {return new InMemoryConfigStore();}
	}


	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private NodeService nodes;

	@Autowired
	private NodeStatusSweep sweep;

	@Autowired
	private InMemoryHeartbeatStore heartbeats;

	private final ObjectMapper json = new ObjectMapper();

	private int snapshotStatus(String key) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Api-Key", key);
		return rest.exchange("http://localhost:" + port + "/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), String.class)
				.getStatusCode().value();
	}

	private String nodeStatus(String nodeId) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Api-Key", "dev-dash-key");
		String body = rest.exchange("http://localhost:" + port + "/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
		for (JsonNode node : json.readTree(body).get("nodes")) {
			if (node.get("node").get("nodeId").asText().equals(nodeId)) return node.get("status").asText();
		}
		return null;
	}

	private StompSession subscribe(BlockingQueue<Object> received) throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new MappingJackson2MessageConverter());
		StompHeaders connect = new StompHeaders();
		connect.add("X-Api-Key", "dev-dash-key");
		StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect, new StompSessionHandlerAdapter() { })
				.get(5, TimeUnit.SECONDS);

		session.subscribe("/topic/nodes", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {return Object.class;}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {received.add(payload);}
		});
		return session;
	}

	// T3
	@Test
	void onlyDashReadsTheSnapshot() {
		assertEquals(403, snapshotStatus("dev-node-key"));
		assertEquals(403, snapshotStatus("dev-roblox-key"));
		assertEquals(200, snapshotStatus("dev-dash-key"));
	}

	/**
	 * T2, the acceptance. The DOWN transition happens while the dashboard is disconnected,
	 * so it misses that delta; the snapshot after reconnecting must show it, and the new
	 * subscription must not replay it.
	 */
	@Test
	void reconnectHydratesWhatWasMissed() throws Exception {
		BlockingQueue<Object> first = new LinkedBlockingQueue<>();
		StompSession session = subscribe(first);
		// the subscription is registered asynchronously; give it a moment to reach the broker
		Thread.sleep(300);

		nodes.register(new NodeRegisterRequest("t2-node", "host", Map.of(), 1));
		nodes.heartbeat("t2-node", new HeartbeatRequest(0, List.of()));
		sweep.sweep();
		assertNotNull(first.poll(3, TimeUnit.SECONDS), "the UP delta never arrived");

		session.disconnect();
		heartbeats.expire("t2-node");
		sweep.sweep();

		BlockingQueue<Object> second = new LinkedBlockingQueue<>();
		StompSession again = subscribe(second);
		assertEquals("DOWN", nodeStatus("t2-node"));
		assertNull(second.poll(1, TimeUnit.SECONDS), "a delta from the disconnect window was replayed");
		again.disconnect();
	}

}
