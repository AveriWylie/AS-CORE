package ascore.overrides;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

// ConfigStore without Mongo
public class InMemoryConfigStore implements ConfigStore {

	private final List<ConfigVersion> versions = new ArrayList<>();
	private final Map<String, Integer> pointers = new HashMap<>();

	@Override
	public synchronized void saveVersion(ConfigVersion version) {versions.add(version);}

	@Override
	public synchronized Optional<ConfigVersion> findVersion(String placeId, String namespace, int version) {
		return versions.stream()
				.filter(v -> v.getPlaceId().equals(placeId) && v.getNamespace().equals(namespace) && v.getVersion() == version)
				.findFirst();
	}

	@Override
	public synchronized int latestVersionNumber(String placeId, String namespace) {
		return versions.stream()
				.filter(v -> v.getPlaceId().equals(placeId) && v.getNamespace().equals(namespace))
				.mapToInt(ConfigVersion::getVersion)
				.max().orElse(0);
	}

	@Override
	public synchronized List<ConfigVersion> history(String placeId) {return versions.stream().filter(v -> v.getPlaceId().equals(placeId)).toList();}

	@Override
	public synchronized void setActivePointer(String placeId, String namespace, int version) {pointers.put(placeId + ":" + namespace, version);}

	@Override
	public synchronized Optional<Integer> getActivePointer(String placeId, String namespace) {return Optional.ofNullable(pointers.get(placeId + ":" + namespace));}

	@Override
	public synchronized Map<String, Map<String, Integer>> activePointers() {
		Map<String, Map<String, Integer>> active = new TreeMap<>();
		pointers.forEach((key, version) -> {
			String[] parts = key.split(":", 2);
			active.computeIfAbsent(parts[0], k -> new TreeMap<>()).put(parts[1], version);
		});
		return active;
	}

	public synchronized int versionCount() {return versions.size();}

}
