package ascore.overrides;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * O3 - the typo firewall, and the reason a dashboard edit cannot brick a live game. Validates a
 * save's values against a per-namespace map of known keys -> expected primitive type, registered
 * in code (v1 deliberately simple; full JSON Schema is a v2 swap BEHIND this same unit). Unknown
 * key or wrong type -> reject BEFORE any version is created (T2 asserts no row is written on
 * rejection). This is the plan's explicit "a typo'd JSON key can't brick a live game."
 *
 * Consumes: nothing - entirely ours.
 */
@Component
public class ConfigSchemaRegistry {

	enum Kind { NUMBER, BOOLEAN, STRING }

	// v1 schema. A key missing here cannot be saved, so a new setting starts with a line here
	private static final Map<String, Map<String, Kind>> SCHEMA = Map.of(
			"weapons", Map.of(
					"damageMultiplier", Kind.NUMBER,
					"fireRate", Kind.NUMBER,
					"friendlyFire", Kind.BOOLEAN),
			"spawns", Map.of(
					"zombieSpeed", Kind.NUMBER,
					"spawnRate", Kind.NUMBER,
					"maxZombies", Kind.NUMBER),
			"graphics", Map.of(
					"preset", Kind.STRING,
					"shadows", Kind.BOOLEAN,
					"renderDistance", Kind.NUMBER));

	/**
	 * Returns key -> what is wrong with it, empty when the values are acceptable. Keyed by
	 * name so the rejection lands in ApiError.fieldErrors and names the typo directly.
	 */
	public Map<String, String> validate(String namespace, Map<String, Object> values) {
		Map<String, Kind> known = SCHEMA.get(namespace);
		if (known == null) return Map.of("namespace", "unknown namespace '" + namespace + "'");
		Map<String, String> problems = new LinkedHashMap<>();

		values.forEach((key, value) -> {
			Kind kind = known.get(key);
			if (kind == null) problems.put(key, "unknown key in " + namespace);
			else if (!matches(kind, value)) problems.put(key, "expected " + kind.name().toLowerCase());
		});

		return problems;
	}

	public Set<String> namespaces() {return SCHEMA.keySet();}

	private static boolean matches(Kind kind, Object value) {
		return switch (kind) {
			case NUMBER -> value instanceof Number;
			case BOOLEAN -> value instanceof Boolean;
			case STRING -> value instanceof String;
		};
	}

}
