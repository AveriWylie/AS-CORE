package ascore.overrides;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * O7 - key config:active:{placeId}, holding the body and etag together as one JSON
 * value so they can never be read out of step. No TTL: an entry is only ever replaced or
 * evicted, since an expired one would quietly send the hottest ROBLOX path to Mongo.
 */
@Component
public class RedisActiveConfigCache implements ActiveConfigCache {

	private static final String KEY = "config:active:";

	private final StringRedisTemplate redis;
	private final ObjectMapper json;

	public RedisActiveConfigCache(StringRedisTemplate redis, ObjectMapper json) {
		this.redis = redis;
		this.json = json;
	}

	@Override
	public void put(String placeId, ActiveConfig config) {
		try {
			redis.opsForValue().set(KEY + placeId, json.writeValueAsString(config));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("could not cache config for " + placeId, e);
		}
	}

	@Override
	public Optional<ActiveConfig> get(String placeId) {
		String cached = redis.opsForValue().get(KEY + placeId);
		if (cached == null) return Optional.empty();
		try {
			return Optional.of(json.readValue(cached, ActiveConfig.class));
		} catch (JsonProcessingException e) {
			// unreadable counts as a miss; the caller rebuilds and overwrites it
			return Optional.empty();
		}
	}

	@Override
	public void evictAll() {
		Set<String> keys = redis.keys(KEY + "*");
		if (keys != null && !keys.isEmpty()) redis.delete(keys);
	}

}
