package ascore.overrides;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

// package-private so only MongoConfigStore reaches it
interface ConfigVersionRepository extends MongoRepository<ConfigVersion, String> {

	Optional<ConfigVersion> findByPlaceIdAndNamespaceAndVersion(String placeId, String namespace, int version);

	Optional<ConfigVersion> findTopByPlaceIdAndNamespaceOrderByVersionDesc(String placeId, String namespace);

	List<ConfigVersion> findByPlaceIdOrderByNamespaceAscVersionDesc(String placeId);
}
