package ascore.overrides;

import java.util.List;
import java.util.Optional;

/**
 * O4 - the rule-5 seam for config persistence. The active POINTER is a tiny separate durable doc
 * (Mongo is the truth; Redis O6 is only its cache) - keeping the pointer out of the version
 * documents is what preserves version immutability (activating never touches a version row).
 * Consumes: nothing - ours.
 * TODO(averi): implement via O5 MongoConfigStore + repositories.
 */
public interface ConfigStore {
	void saveVersion(ConfigVersion version);
	Optional<ConfigVersion> findVersion(String placeId, String namespace, int version);
	int latestVersionNumber(String placeId, String namespace);
	List<ConfigVersion> history(String placeId);
	void setActivePointer(String placeId, String namespace, int version);
	Optional<Integer> getActivePointer(String placeId, String namespace);
}
