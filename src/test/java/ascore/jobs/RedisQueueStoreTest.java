package ascore.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * T1 and T6, which are about Redis itself: that LMOVE is atomic, and how a claim
 * waits. Skips without a Redis on 6379.
 *
 * Runs against logical database 15 and flushes it before each test, so it never
 * touches dev data in database 0.
 */
@EnabledIf("redisAvailable")
@SpringBootTest(properties = "spring.data.redis.database=15")
class RedisQueueStoreTest {

	static boolean redisAvailable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", 6379), 300);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Autowired
	private RedisQueueStore queue;

	@Autowired
	private StringRedisTemplate redis;

	@BeforeEach
	void emptyDatabase() {redis.getConnectionFactory().getConnection().serverCommands().flushDb();}

	/**
	 * T1, the acceptance test. One job, two nodes released at the same instant, fifty
	 * times. Exactly one claim may succeed each round.
	 */
	@Test
	void concurrentClaimsNeverShareAJob() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);

		try {
			for (int round = 0; round < 50; round++) {
				String id = "race-" + round;
				queue.enqueue(JobType.CUSTOM, id, 0);
				CountDownLatch go = new CountDownLatch(1);

				Future<Optional<String>> a = pool.submit(() -> claimAfter(go, "node-a"));
				Future<Optional<String>> b = pool.submit(() -> claimAfter(go, "node-b"));
				go.countDown();

				Optional<String> won = a.get();
				Optional<String> other = b.get();
				assertEquals(1, Stream.of(won, other).filter(Optional::isPresent).count(), "round " + round);

				String winner = won.isPresent() ? "node-a" : "node-b";
				queue.ack(winner, id);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	// T6. With nothing queued the claim waits; work arriving mid-wait is returned at once
	@Test
	void claimReturnsAsSoonAsWorkArrives() throws Exception {
		CompletableFuture<Optional<String>> claim = CompletableFuture.supplyAsync(
				() -> queue.claimOne(List.of(JobType.CUSTOM), "poller", Duration.ofSeconds(5)));

		Thread.sleep(500);
		assertFalse(claim.isDone(), "the claim returned before any work existed");

		queue.enqueue(JobType.CUSTOM, "late", 0);
		assertEquals(Optional.of("late"), claim.get(2, TimeUnit.SECONDS));
	}

	@Test
	void claimOnAnEmptyQueueGivesUpAtTheTimeout() {
		long start = System.nanoTime();

		assertTrue(queue.claimOne(List.of(JobType.CUSTOM), "idle", Duration.ofMillis(500)).isEmpty());
		assertTrue(System.nanoTime() - start >= 450_000_000L, "returned before the timeout");
	}

	private Optional<String> claimAfter(CountDownLatch go, String nodeId) throws InterruptedException {
		go.await();
		return queue.claimOne(List.of(JobType.CUSTOM), nodeId, Duration.ofMillis(300));
	}

}
