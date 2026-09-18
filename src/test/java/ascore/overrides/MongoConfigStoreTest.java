package ascore.overrides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * T1 against real Mongo. ConfigVersion has no setters, so this is also the proof that
 * Spring Data can read one back through its constructor. Skips without Mongo on 27017;
 * each run uses its own placeId.
 */
@EnabledIf("mongoAvailable")
@SpringBootTest
class MongoConfigStoreTest {

	static boolean mongoAvailable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", 27017), 300);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Autowired
	private MongoConfigStore store;

	@Test
	void versionsAndPointerRoundTrip() {
		String place = "place-" + System.nanoTime();
		Map<String, Object> values = Map.of("zombieSpeed", 16, "spawnRate", 0.5);
		store.saveVersion(new ConfigVersion(place, "spawns", 1, values, "dash", Instant.now()));
		store.saveVersion(new ConfigVersion(place, "spawns", 2, Map.of("zombieSpeed", 20), "dash", Instant.now()));

		assertEquals(values, store.findVersion(place, "spawns", 1).orElseThrow().getValues());
		assertEquals(2, store.latestVersionNumber(place, "spawns"));
		assertEquals(2, store.history(place).size());

		store.setActivePointer(place, "spawns", 1);
		store.setActivePointer(place, "spawns", 2);
		assertEquals(Optional.of(2), store.getActivePointer(place, "spawns"));
	}

}
