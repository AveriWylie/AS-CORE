package ascore.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import ascore.egress.EgressService;
import ascore.overrides.ConfigSaveRequest;
import ascore.overrides.ConfigSchemaRegistry;
import ascore.overrides.ConfigService;
import ascore.overrides.InMemoryActiveConfigCache;
import ascore.overrides.InMemoryConfigStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// T2 through ConfigService, and the unit half of T3
class AuditServiceTest {

	private ConfigService config(AuditStore audit) {
		return new ConfigService(new ConfigSchemaRegistry(), new InMemoryConfigStore(), new InMemoryActiveConfigCache(),
				mock(EgressService.class), (topic, payload) -> { }, new ObjectMapper(), TestObservability.audit(audit));
	}

	// T2
	@Test
	void activationWritesOneRecordWithBeforeAndAfter() {
		InMemoryAuditStore audit = new InMemoryAuditStore();
		ConfigService service = config(audit);
		service.save(new ConfigSaveRequest("p1", "spawns", Map.of("zombieSpeed", 16)), "dash");
		service.save(new ConfigSaveRequest("p1", "spawns", Map.of("zombieSpeed", 20)), "dash");
		service.activate("p1", "spawns", 1, "dash");

		service.activate("p1", "spawns", 2, "dash");

		List<AuditRecord> activations = audit.withAction("config.activate");
		assertEquals(2, activations.size());
		AuditRecord last = activations.getLast();
		assertEquals("dash", last.getWho());
		assertEquals("p1/spawns", last.getTarget());
		assertEquals(Map.of("version", 1), last.getBefore());
		assertEquals(Map.of("version", 2), last.getAfter());
	}

	// T3, unit half: the store throws and the caller never sees it
	@Test
	void failingStoreNeverReachesTheCaller() {
		AuditStore broken = new AuditStore() {

			@Override
			public void record(AuditRecord record) {throw new IllegalStateException("audit store down");}

			@Override
			public List<AuditRecord> query(java.time.Instant from, java.time.Instant to, java.util.Optional<String> action) {return List.of();}
		};

		assertDoesNotThrow(() -> config(broken).save(new ConfigSaveRequest("p1", "spawns", Map.of("zombieSpeed", 16)), "dash"));
	}

}
