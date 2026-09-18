package ascore.overrides;

import static ascore.asdb.AsdbDocumentStore.eq;
import ascore.asdb.AsdbBinaryClient;
import ascore.asdb.AsdbDocumentStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * O5 on asdb: config_versions and config_active, as MongoConfigStore has them.
 *
 * Mongo's unique index on (placeId, namespace, version) is what stops two saves racing
 * to the same number. asdb parses unique but does not enforce it yet, so saveVersion
 * checks and inserts under a lock instead. That holds for one AS-CORE process only.
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbConfigStore implements ConfigStore {

	private final AsdbDocumentStore<ConfigVersion> versions;
	private final AsdbDocumentStore<ActivePointer> pointers;

	public AsdbConfigStore(AsdbBinaryClient client) {
		this.versions = new AsdbDocumentStore<>(client, ConfigVersion.class);
		this.pointers = new AsdbDocumentStore<>(client, ActivePointer.class);
		versions.ensureSchema("placeId");
		pointers.ensureSchema();
	}

	@Override
	public synchronized void saveVersion(ConfigVersion version) {
		if (findVersion(version.getPlaceId(), version.getNamespace(), version.getVersion()).isPresent()) {
			throw new IllegalStateException("version " + version.getVersion() + " of " + version.getNamespace() + " for " + version.getPlaceId() + " already exists");
		}
		versions.insert(version);
	}

	@Override
	public Optional<ConfigVersion> findVersion(String placeId, String namespace, int version) {
		return versions.find("where " + eq("placeId", placeId) + " and " + eq("namespace", namespace) + " and " + eq("version", version))
				.stream().findFirst();
	}

	@Override
	public int latestVersionNumber(String placeId, String namespace) {
		return versions.find("where " + eq("placeId", placeId) + " and " + eq("namespace", namespace) + " order version desc limit 1")
				.stream().findFirst().map(ConfigVersion::getVersion).orElse(0);
	}

	@Override
	public List<ConfigVersion> history(String placeId) {return versions.find("where " + eq("placeId", placeId) + " order namespace asc, version desc");}

	@Override
	public void setActivePointer(String placeId, String namespace, int version) {pointers.upsert(ActivePointer.of(placeId, namespace, version));}

	@Override
	public Optional<Integer> getActivePointer(String placeId, String namespace) {
		return pointers.find("where " + eq("id", ActivePointer.idFor(placeId, namespace))).stream().findFirst().map(ActivePointer::getVersion);
	}

	@Override
	public Map<String, Map<String, Integer>> activePointers() {
		Map<String, Map<String, Integer>> active = new TreeMap<>();
		for (ActivePointer p : pointers.find()) active.computeIfAbsent(p.getPlaceId(), k -> new TreeMap<>()).put(p.getNamespace(), p.getVersion());
		return active;
	}

}
