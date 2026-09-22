package ascore.overrides;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The pointer document: which version is live for one placeId and namespace. The id is
 * built from both, so moving the pointer is a save over the same document rather than a
 * new one, and the version rows are never touched.
 */
@Document("config_active")
class ActivePointer {

	@Id
	private final String id;
	private final String placeId;
	private final String namespace;
	private final int version;

	ActivePointer(String id, String placeId, String namespace, int version) {
		this.id = id;
		this.placeId = placeId;
		this.namespace = namespace;
		this.version = version;
	}

	static ActivePointer of(String placeId, String namespace, int version) {return new ActivePointer(idFor(placeId, namespace), placeId, namespace, version);}

	static String idFor(String placeId, String namespace) {return placeId + ":" + namespace;}

	String getPlaceId() {return placeId;}

	String getNamespace() {return namespace;}

	int getVersion() {return version;}

}
