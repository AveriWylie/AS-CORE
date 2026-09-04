package ascore.nodes;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * N7 - the Redis adapter that MAKES key-expiry the failure detector.
 *
 * Keys: node:hb:{id} (value = timestamp, TTL 45s), node:load:{id} (value = load, TTL 45s).
 * The 45s TTL is chosen against the plan's 60s detection bound: 45s expiry + 15s sweep (N9) =
 * worst-case 60s from last heartbeat to DOWN broadcast. The number is derived from the bound, not
 * picked arbitrarily.
 *
 * Consumes: StringRedisTemplate -
 *   opsForValue().set(key, value, Duration.ofSeconds(45))  - write AND TTL in one atomic call
 *   hasKey(key)                                            - liveness check (key present = alive)
 *   keys("node:hb:*")                                      - aliveNodeIds (fine at 9 nodes; use
 *                                                            SCAN if the fleet ever grows large)
 */
@Component
public class RedisHeartbeatStore implements HeartbeatStore {
	@Override public void recordHeartbeat(String nodeId, int load) {
		throw new UnsupportedOperationException("TODO(averi): N7");
	}
	@Override public boolean isAlive(String nodeId) {
		throw new UnsupportedOperationException("TODO(averi): N7");
	}
	@Override public Set<String> aliveNodeIds() {
		throw new UnsupportedOperationException("TODO(averi): N7");
	}
	@Override public Optional<Integer> loadOf(String nodeId) {
		throw new UnsupportedOperationException("TODO(averi): N7");
	}
}
