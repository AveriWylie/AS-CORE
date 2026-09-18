package ascore.overrides;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

// O5 - the Mongo adapter behind ConfigStore, the A6/N5/J5 pattern
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "mongo", matchIfMissing = true)
public class MongoConfigStore implements ConfigStore {

	private static final Logger log = LoggerFactory.getLogger(MongoConfigStore.class);

	private final ConfigVersionRepository versions;
	private final ActivePointerRepository pointers;

	public MongoConfigStore(ConfigVersionRepository versions, ActivePointerRepository pointers, MongoTemplate mongo) {
		this.versions = versions;
		this.pointers = pointers;
		ensureIndexes(mongo);
	}

	/**
	 * Builds ConfigVersion's unique compound index, which is what refuses a second save of
	 * the same version. Spring only builds annotated indexes itself with auto-index-creation
	 * on, and that would connect to Mongo at startup even when asdb is the store. Here it
	 * runs only when this class exists, and an unreachable Mongo is logged, not fatal.
	 */
	private static void ensureIndexes(MongoTemplate mongo) {
		try {
			IndexOperations ops = mongo.indexOps(ConfigVersion.class);
			new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext())
					.resolveIndexFor(ConfigVersion.class)
					.forEach(ops::createIndex);
		} catch (DataAccessException e) {
			log.warn("config_versions indexes not built, Mongo unreachable: {}", e.getMessage());
		}
	}

	@Override
	public void saveVersion(ConfigVersion version) {versions.insert(version);}

	@Override
	public Optional<ConfigVersion> findVersion(String placeId, String namespace, int version) {
		return versions.findByPlaceIdAndNamespaceAndVersion(placeId, namespace, version);
	}

	@Override
	public int latestVersionNumber(String placeId, String namespace) {
		return versions.findTopByPlaceIdAndNamespaceOrderByVersionDesc(placeId, namespace).map(ConfigVersion::getVersion).orElse(0);
	}

	@Override
	public List<ConfigVersion> history(String placeId) {return versions.findByPlaceIdOrderByNamespaceAscVersionDesc(placeId);}

	@Override
	public void setActivePointer(String placeId, String namespace, int version) {pointers.save(ActivePointer.of(placeId, namespace, version));}

	@Override
	public Optional<Integer> getActivePointer(String placeId, String namespace) {
		return pointers.findById(ActivePointer.idFor(placeId, namespace)).map(ActivePointer::getVersion);
	}

	@Override
	public Map<String, Map<String, Integer>> activePointers() {
		Map<String, Map<String, Integer>> active = new TreeMap<>();
		for (ActivePointer p : pointers.findAll()) active.computeIfAbsent(p.getPlaceId(), k -> new TreeMap<>()).put(p.getNamespace(), p.getVersion());
		return active;
	}

}
