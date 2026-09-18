package ascore.overrides;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// ActiveConfigCache without Redis
public class InMemoryActiveConfigCache implements ActiveConfigCache {

	private final Map<String, ActiveConfig> entries = new ConcurrentHashMap<>();

	@Override
	public void put(String placeId, ActiveConfig config) {entries.put(placeId, config);}

	@Override
	public Optional<ActiveConfig> get(String placeId) {return Optional.ofNullable(entries.get(placeId));}

	@Override
	public void evictAll() {entries.clear();}

}
