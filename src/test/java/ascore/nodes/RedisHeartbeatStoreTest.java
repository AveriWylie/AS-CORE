package ascore.nodes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Writing a heartbeat and letting it expire, which needs a real Redis. Skips rather
 * than fails without one on 6379; brew install redis is enough, no Docker needed.
 *
 * The TTL is shortened to 2s here so the expiry test can watch a key die without waiting 45s.
 */
@EnabledIf("redisAvailable")
@SpringBootTest(properties = "shayveri.nodes.heartbeat-ttl-seconds=2")
class RedisHeartbeatStoreTest {

	static boolean redisAvailable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", 6379), 300);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Autowired
	private RedisHeartbeatStore store;

	@Autowired
	private StringRedisTemplate redis;

	@Test
	void heartbeatWritesKeyWithTtl() {
		String id = "t2-" + System.nanoTime();
		store.recordHeartbeat(id, 1);

		Long ttl = redis.getExpire("node:hb:" + id);
		assertTrue(ttl != null && ttl > 0 && ttl <= 2, "unexpected ttl " + ttl);
	}

	@Test
	void livenessExpires() throws InterruptedException {
		String id = "t3-" + System.nanoTime();
		store.recordHeartbeat(id, 0);
		assertTrue(store.isAlive(id));

		Thread.sleep(2500);
		assertFalse(store.isAlive(id));
	}

}
