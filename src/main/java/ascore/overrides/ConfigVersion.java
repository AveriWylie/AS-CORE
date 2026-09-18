package ascore.overrides;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * O2 - a config version. THE core invariant of the whole module: IMMUTABLE. Every save is a new
 * version; activation moves a pointer elsewhere; rollback = repoint. This class has NO setters at
 * all - the class itself enforces the invariant, which is what makes "rollback restores prior
 * values exactly" a one-line proof (T3) instead of a hope.
 *
 * Consumes: @Document("config_versions"), @Id, @Indexed on (placeId, namespace).
 */
@Document("config_versions")
@CompoundIndex(def = "{'placeId': 1, 'namespace': 1, 'version': 1}", unique = true)
public class ConfigVersion {

	/*
	id is the one field left non-final: Mongo assigns it on insert and Spring Data sets it
	then. Nothing in this class can change it afterwards.

	The index is unique so two saves racing to the same next number cannot both land; the
	loser fails instead of silently sharing a version.
	*/
	@Id
	private String id;
	private final String placeId;
	private final String namespace;
	private final int version;
	private final Map<String, Object> values;
	private final String savedBy;
	private final Instant savedAt;

	// also the path Spring Data reads documents back through, matched by parameter name
	public ConfigVersion(String placeId, String namespace, int version, Map<String, Object> values, String savedBy, Instant savedAt) {
		this.placeId = placeId;
		this.namespace = namespace;
		this.version = version;
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		this.savedBy = savedBy;
		this.savedAt = savedAt;
	}

	public String getId() {return id;}

	public String getPlaceId() {return placeId;}

	public String getNamespace() {return namespace;}

	public int getVersion() {return version;}

	public Map<String, Object> getValues() {return values;}

	public String getSavedBy() {return savedBy;}

	public Instant getSavedAt() {return savedAt;}

}
