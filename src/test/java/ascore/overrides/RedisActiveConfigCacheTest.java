package ascore.overrides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

// the body and etag survive the JSON round trip, and evictAll clears every place. Database 15, skips without Redis
@EnabledIf("redisAvailable")
@SpringBootTest(properties = "spring.data.redis.database=15")
class RedisActiveConfigCacheTest {

	static boolean redisAvailable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", 6379), 300);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Autowired
	private RedisActiveConfigCache cache;

	@Autowired
	private StringRedisTemplate redis;

	@BeforeEach
	void emptyDatabase() {redis.getConnectionFactory().getConnection().serverCommands().flushDb();}

	@Test
	void roundTripsAndEvicts() {
		ActiveConfig config = new ActiveConfig("{\"spawns\":{\"zombieSpeed\":16}}", "abc123");
		cache.put("p1", config);
		cache.put("p2", config);

		assertEquals(Optional.of(config), cache.get("p1"));

		cache.evictAll();
		assertTrue(cache.get("p1").isEmpty());
		assertTrue(cache.get("p2").isEmpty());
	}

}
