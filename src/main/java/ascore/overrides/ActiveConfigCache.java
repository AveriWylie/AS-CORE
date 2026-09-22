package ascore.overrides;

import java.util.Optional;

/**
 * The storage seam for the hot path. Holds each place's assembled config so a poll
 * never reaches Mongo.
 *
 * evictAll exists for global activations: every place's assembly includes the global
 * namespaces, so all cached entries go stale at once and are rebuilt on their next read.
 */
public interface ActiveConfigCache {

	void put(String placeId, ActiveConfig config);

	Optional<ActiveConfig> get(String placeId);

	void evictAll();
}
