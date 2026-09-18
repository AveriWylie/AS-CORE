package ascore.overrides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// what every ConfigStore must do, run against asdb and Mongo. Each test uses its own placeId
abstract class ConfigStoreContract {

	@Autowired
	protected ConfigStore store;

	private static final Instant AT = Instant.ofEpochMilli(1_700_000_000_000L);

	private static String place() {return "place-" + UUID.randomUUID();}

	private void save(String place, String namespace, int version, Map<String, Object> values) {
		store.saveVersion(new ConfigVersion(place, namespace, version, values, "dash", AT));
	}

	// T1: what was saved is what comes back
	@Test
	void versionReadsBackExactly() {
		String place = place();
		Map<String, Object> values = Map.of("zombieSpeed", 16, "spawnRate", 0.5, "preset", "high", "shadows", true);
		save(place, "spawns", 1, values);

		ConfigVersion read = store.findVersion(place, "spawns", 1).orElseThrow();
		assertEquals(values, read.getValues());
		assertEquals("dash", read.getSavedBy());
		assertEquals(AT, read.getSavedAt());
	}

	@Test
	void latestAndHistory() {
		String place = place();
		save(place, "spawns", 1, Map.of("zombieSpeed", 1));
		save(place, "spawns", 2, Map.of("zombieSpeed", 2));
		save(place, "graphics", 1, Map.of("shadows", true));

		assertEquals(2, store.latestVersionNumber(place, "spawns"));
		assertEquals(0, store.latestVersionNumber(place, "weapons"));
		List<String> order = store.history(place).stream().map(v -> v.getNamespace() + v.getVersion()).toList();
		assertEquals(List.of("graphics1", "spawns2", "spawns1"), order);
	}

	@Test
	void duplicateVersionIsRefused() {
		String place = place();
		save(place, "spawns", 1, Map.of("zombieSpeed", 1));
		assertThrows(RuntimeException.class, () -> save(place, "spawns", 1, Map.of("zombieSpeed", 2)));
	}

	@Test
	void pointerMovesAndIsListed() {
		String place = place();
		store.setActivePointer(place, "spawns", 1);
		store.setActivePointer(place, "spawns", 2);

		assertEquals(Optional.of(2), store.getActivePointer(place, "spawns"));
		assertEquals(Map.of("spawns", 2), store.activePointers().get(place));
	}

}
