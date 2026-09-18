package ascore.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// what every AuditStore must do, run against asdb and Mongo. Each test uses its own action names
abstract class AuditStoreContract {

	@Autowired
	protected AuditStore store;

	private static final Instant BASE = Instant.ofEpochMilli(1_700_000_000_000L);

	@Test
	void rangeAndActionFilterNewestFirst() {
		String tag = UUID.randomUUID().toString();
		String activate = "config.activate." + tag;
		store.record(new AuditRecord(BASE, "dash", activate, "p1/spawns", Map.of("version", 1), Map.of("version", 2)));
		store.record(new AuditRecord(BASE.plusSeconds(10), "dash", activate, "p1/spawns", Map.of("version", 2), Map.of("version", 3)));
		store.record(new AuditRecord(BASE.plusSeconds(20), "dash", "job.create." + tag, "j1", null, Map.of("type", "CUSTOM")));
		store.record(new AuditRecord(BASE.plusSeconds(3600), "dash", activate, "p1/spawns", null, null));

		List<AuditRecord> hits = store.query(BASE, BASE.plusSeconds(60), Optional.of(activate));
		assertEquals(2, hits.size());
		assertEquals(BASE.plusSeconds(10), hits.get(0).getAt());
		assertEquals(Map.of("version", 2), hits.get(0).getBefore());
		assertEquals(Map.of("version", 3), hits.get(0).getAfter());
	}

	@Test
	void nullBeforeAndAfterComeBackEmpty() {
		String action = "job.create." + UUID.randomUUID();
		store.record(new AuditRecord(BASE, "dash", action, "j1", null, null));

		AuditRecord read = store.query(BASE, BASE, Optional.of(action)).getFirst();
		assertEquals(Map.of(), read.getBefore());
		assertEquals(Map.of(), read.getAfter());
	}

}
