package ascore.nodes;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
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

	private static final String HB = "node:hb:";
	private static final String LOAD = "node:load:";

	private final StringRedisTemplate redis;
	private final Duration ttl;

	// the TTL is a property so T3 can shorten it; 45s is the production value
	public RedisHeartbeatStore(StringRedisTemplate redis, @Value("${shayveri.nodes.heartbeat-ttl-seconds:45}") long ttlSeconds) {
		this.redis = redis;
		this.ttl = Duration.ofSeconds(ttlSeconds);
	}

	@Override public void recordHeartbeat(String nodeId, int load) {
		redis.opsForValue().set(HB + nodeId, Instant.now().toString(), ttl);
		redis.opsForValue().set(LOAD + nodeId, Integer.toString(load), ttl);
	}

	@Override public boolean isAlive(String nodeId) {return Boolean.TRUE.equals(redis.hasKey(HB + nodeId));}

	@Override public Set<String> aliveNodeIds() {
		Set<String> keys = redis.keys(HB + "*");
		if (keys == null) return Set.of();
		return keys.stream().map(k -> k.substring(HB.length())).collect(Collectors.toUnmodifiableSet());
	}

	@Override public Optional<Integer> loadOf(String nodeId) {
		String load = redis.opsForValue().get(LOAD + nodeId);
		return load == null ? Optional.empty() : Optional.of(Integer.parseInt(load));
	}

}
