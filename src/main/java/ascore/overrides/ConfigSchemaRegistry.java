package ascore.overrides;

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
	public java.util.List<String> validate(String namespace, java.util.Map<String, Object> values) {
		throw new UnsupportedOperationException("TODO(averi): O3");
	}
}
