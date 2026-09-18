package ascore.overrides;

import ascore.egress.EgressService;
import ascore.observability.AuditService;
import ascore.realtime.RealtimePublisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * O8 - the logic. Two acts kept SEPARATE (this separation is a design decision, not incidental):
 *   save(req, who): O3.validate -> version = latest+1 -> new immutable O2 -> O4.saveVersion.
 *                   Returns the version number. NOTHING is activated by saving.
 *   activate(placeId, ns, version, who): O4.setActivePointer -> reassemble merged config +
 *                   O6.put(new etag = hash of body) -> EgressService.publishConfigActivated
 *                   (Module 5) -> /topic/config -> AuditService hook (before/after = version
 *                   numbers). ROLLBACK IS activate(oldVersion) - no separate code path, so T3
 *                   proves rollback for free.
 *   getActive(placeId): from O6 cache, fall through to O4 + repopulate on miss.
 * Consumes: @Service; depends on ConfigStore (O4), ActiveConfigCache (O6), EgressService (M5),
 * RealtimePublisher, AuditService - all through their interfaces (rule 5 across modules).
 */
@Service
public class ConfigService {

	// the placeId of the config every place falls back to, per namespace
	public static final String GLOBAL = "global";

	private final ConfigSchemaRegistry schema;
	private final ConfigStore store;
	private final ActiveConfigCache cache;
	private final EgressService egress;
	private final RealtimePublisher publisher;
	private final ObjectMapper json;
	private final AuditService audit;

	public ConfigService(ConfigSchemaRegistry schema, ConfigStore store, ActiveConfigCache cache,
			EgressService egress, RealtimePublisher publisher, ObjectMapper json, AuditService audit) {
		this.schema = schema;
		this.store = store;
		this.cache = cache;
		this.egress = egress;
		this.publisher = publisher;
		this.json = json;
		this.audit = audit;
	}

	public int save(ConfigSaveRequest req, String who) {
		Map<String, String> problems = schema.validate(req.namespace(), req.values());
		if (!problems.isEmpty()) throw new ConfigRejectedException(problems);
		int version = store.latestVersionNumber(req.placeId(), req.namespace()) + 1;
		store.saveVersion(new ConfigVersion(req.placeId(), req.namespace(), version, req.values(), who, Instant.now()));
		audit.audit(who, "config.save", req.placeId() + "/" + req.namespace(), null, Map.of("version", version));
		return version;
	}

	/**
	 * The cache is rebuilt before egress runs, so a place that receives the push and fetches
	 * straight away already sees the new version. Egress never throws; by the time it runs the
	 * pointer has moved and activation has succeeded.
	 */
	public void activate(String placeId, String namespace, int version, String who) {
		if (store.findVersion(placeId, namespace, version).isEmpty()) {
			throw new NoSuchElementException("no version " + version + " of " + namespace + " for " + placeId);
		}
		Optional<Integer> previous = store.getActivePointer(placeId, namespace);
		store.setActivePointer(placeId, namespace, version);

		if (placeId.equals(GLOBAL)) cache.evictAll();
		cache.put(placeId, assemble(placeId));

		egress.publishConfigActivated(placeId, version);
		publisher.publish("/topic/config", Map.of("placeId", placeId, "namespace", namespace, "version", version));
		audit.audit(who, "config.activate", placeId + "/" + namespace,
				previous.map(v -> Map.<String, Object>of("version", v)).orElse(null), Map.of("version", version));
	}

	public ActiveConfig getActive(String placeId) {
		return cache.get(placeId).orElseGet(() -> {
			ActiveConfig built = assemble(placeId);
			cache.put(placeId, built);
			return built;
		});
	}

	public List<ConfigVersion> history(String placeId) {return store.history(placeId);}

	/**
	 * Every namespace's active values for this place, falling back to the global version where
	 * the place has none. Sorted maps throughout, so the same config always serialises to the
	 * same bytes and therefore the same etag.
	 */
	private ActiveConfig assemble(String placeId) {
		Map<String, Object> merged = new TreeMap<>();

		for (String namespace : schema.namespaces()) {
			Optional<ConfigVersion> active = activeVersion(placeId, namespace);
			if (active.isEmpty() && !placeId.equals(GLOBAL)) active = activeVersion(GLOBAL, namespace);
			active.ifPresent(v -> merged.put(namespace, new TreeMap<>(v.getValues())));
		}

		try {
			String body = json.writeValueAsString(merged);
			return new ActiveConfig(body, etagOf(body));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("could not serialise config for " + placeId, e);
		}
	}

	private Optional<ConfigVersion> activeVersion(String placeId, String namespace) {
		return store.getActivePointer(placeId, namespace).map(version -> store.findVersion(placeId, namespace, version)
				.orElseThrow(() -> new IllegalStateException("pointer to missing version " + version + " of " + namespace + " for " + placeId)));
	}

	private static String etagOf(String body) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
