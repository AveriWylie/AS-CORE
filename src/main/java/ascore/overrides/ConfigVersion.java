package ascore.overrides;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
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
public class ConfigVersion {
	@Id
	private String id;
	// TODO(averi): final fields + all-args constructor + getters per blueprint O2.
}
