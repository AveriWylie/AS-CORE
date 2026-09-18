package ascore.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * T7 filters. The filtering lives in MongoJobStore's choice of derived query, so it
 * needs a real Mongo; skips without one on 27017. Each run uses its own mapId, so
 * earlier runs' documents never match.
 */
@EnabledIf("mongoAvailable")
@SpringBootTest
class MongoJobStoreTest {

	static boolean mongoAvailable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", 27017), 300);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Autowired
	private MongoJobStore store;

	@Test
	void findFiltersByStatusAndMapId() {
		String map = "map-" + System.nanoTime();
		String other = map + "-other";
		store.save(Job.from(new JobCreateRequest(JobType.CUSTOM, map, 0, null, 3), Instant.now()));
		store.save(Job.from(new JobCreateRequest(JobType.CUSTOM, other, 0, null, 3), Instant.now()));

		assertEquals(1, store.find(Optional.of(JobStatus.QUEUED), Optional.of(map)).size());
		assertEquals(1, store.find(Optional.empty(), Optional.of(other)).size());
		assertEquals(0, store.find(Optional.of(JobStatus.DONE), Optional.of(map)).size());
	}

}
