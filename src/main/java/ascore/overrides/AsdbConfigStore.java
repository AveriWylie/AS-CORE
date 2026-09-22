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
 * ConfigStore on asdb: config_versions and config_active, as MongoConfigStore has them.
 *
 * A save racing another to the same version number is refused by the database itself:
 * both backends carry a unique index over (placeId, namespace, version), so the second
 * insert fails rather than landing. Nothing here has to check first.
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
		versions.uniqueConstraint("placeId", "namespace", "version");
		pointers.ensureSchema();
	}

	@Override
	public void saveVersion(ConfigVersion version) {
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
