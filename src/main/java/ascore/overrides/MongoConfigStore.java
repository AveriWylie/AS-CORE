package ascore.overrides;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// O5 - the Mongo adapter behind ConfigStore, the A6/N5/J5 pattern
@Component
public class MongoConfigStore implements ConfigStore {

	private final ConfigVersionRepository versions;
	private final ActivePointerRepository pointers;

	public MongoConfigStore(ConfigVersionRepository versions, ActivePointerRepository pointers) {
		this.versions = versions;
		this.pointers = pointers;
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

}
