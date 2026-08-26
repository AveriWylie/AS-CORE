package dev.shayveri.core.overrides;

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
 *
 * TODO(averi): fields (all final, constructor-only):
 *   {@code @Id} String id; String placeId (null = global); String namespace; int version (monotonic per
 *   placeId+namespace); Map<String,Object> values; String savedBy (api key LABEL - the audit seed);
 *   Instant savedAt. Constructor sets everything; getters only; NO setter, ever.
 */
@Document("config_versions")
public class ConfigVersion {
	@Id
	private String id;
	// TODO(averi): remaining final fields + all-args constructor + getters. No setters.
}
