package ascore.overrides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import ascore.egress.EgressService;
import ascore.observability.InMemoryAuditStore;
import ascore.observability.TestObservability;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

// saving, rejecting, activating and rolling back, against the in-memory store and cache; egress is a mock
class ConfigServiceTest {

	private final InMemoryConfigStore store = new InMemoryConfigStore();
	private final InMemoryActiveConfigCache cache = new InMemoryActiveConfigCache();
	private final EgressService egress = mock(EgressService.class);
	private final List<String> topics = new ArrayList<>();
	private final ConfigService service = new ConfigService(new ConfigSchemaRegistry(), store, cache, egress,
			(topic, payload) -> topics.add(topic), new ObjectMapper(), TestObservability.audit(new InMemoryAuditStore()));

	private int save(String placeId, Map<String, Object> values) {return service.save(new ConfigSaveRequest(placeId, "spawns", values), "dash");}

	@Test
	void everySaveIsANewRetrievableVersion() {
		Map<String, Object> first = Map.of("zombieSpeed", 16, "spawnRate", 0.5);
		Map<String, Object> second = Map.of("zombieSpeed", 20, "spawnRate", 0.5);

		assertEquals(1, save("p1", first));
		assertEquals(2, save("p1", second));

		assertEquals(first, store.findVersion("p1", "spawns", 1).orElseThrow().getValues());
		assertEquals(second, store.findVersion("p1", "spawns", 2).orElseThrow().getValues());
	}

	@Test
	void typoIsRejectedBeforeAnyVersionExists() {
		ConfigRejectedException e = assertThrows(ConfigRejectedException.class, () -> save("p1", Map.of("zombeSpeed", 16)));

		assertTrue(e.getProblems().containsKey("zombeSpeed"));
		assertEquals(0, store.versionCount());
	}

	@Test
	void wrongTypeIsRejected() {
		ConfigRejectedException e = assertThrows(ConfigRejectedException.class, () -> save("p1", Map.of("zombieSpeed", "fast")));
		assertTrue(e.getProblems().containsKey("zombieSpeed"));
	}

	// the acceptance: a rollback serves exactly the bytes v1 served the first time
	@Test
	void rollbackRestoresV1Exactly() {
		save("p1", Map.of("zombieSpeed", 16));
		save("p1", Map.of("zombieSpeed", 20));

		service.activate("p1", "spawns", 1, "dash");
		ActiveConfig v1 = service.getActive("p1");
		service.activate("p1", "spawns", 2, "dash");
		ActiveConfig v2 = service.getActive("p1");
		service.activate("p1", "spawns", 1, "dash");

		assertNotEquals(v1.etag(), v2.etag());
		assertEquals(v1, service.getActive("p1"));
	}

	@Test
	void placeFallsBackToGlobalAndGlobalActivationReachesIt() {
		save(ConfigService.GLOBAL, Map.of("zombieSpeed", 16));
		save(ConfigService.GLOBAL, Map.of("zombieSpeed", 18));
		service.activate(ConfigService.GLOBAL, "spawns", 1, "dash");
		ActiveConfig before = service.getActive("p1");

		service.activate(ConfigService.GLOBAL, "spawns", 2, "dash");

		assertTrue(service.getActive("p1").body().contains("18"), "p1 kept serving the stale global version");
		assertNotEquals(before.etag(), service.getActive("p1").etag());
	}

	@Test
	void activationPushesAndBroadcasts() {
		save("p1", Map.of("zombieSpeed", 16));

		service.activate("p1", "spawns", 1, "dash");

		verify(egress).publishConfigActivated("p1", 1);
		assertTrue(topics.contains("/topic/config"));
	}

	@Test
	void activatingAMissingVersionIsNotFound() {
		assertThrows(NoSuchElementException.class, () -> service.activate("p1", "spawns", 9, "dash"));
	}

}
