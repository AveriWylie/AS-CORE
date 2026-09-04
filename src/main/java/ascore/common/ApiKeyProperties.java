package ascore.common;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The configured API keys, bound from {@code shayveri.security.api-keys}.
 *
 * <p>The map is stored role-first because that is how it reads in YAML, and
 * searched value-first by {@link ApiKeyAuthFilter}: a caller presents a secret
 * and the matching entry's key names who they are.
 *
 * <p>WHY THIS CLASS VALIDATES ITSELF. The YAML keys are arbitrary strings;
 * nothing in the binding constrains them to real roles. An unrecognised name
 * used to fail in the worst available place, and in two different ways
 * depending on where you looked:
 *
 * <ul>
 * <li>{@code ApiKeyAuthFilter} called {@code ApiKeyRole.valueOf} on it, which
 * throws inside a servlet filter. Filters run before DispatcherServlet, so
 * {@link GlobalExceptionHandler} never sees it and the caller gets a raw
 * container error instead of an ApiError.
 * <li>It only fired when somebody actually presented that key. A broken
 * configuration therefore survived every restart and every test, then failed on
 * one live request.
 * </ul>
 *
 * <p>Checking at startup moves both problems to the one moment somebody is
 * watching: deployment. A bad key name now stops the application with a message
 * naming it, rather than authorising an unknown role at 3am.
 */
@ConfigurationProperties(prefix = "shayveri.security")
public class ApiKeyProperties {
	/*
	 * The map is stored as role → secret:
	 *
	 * "roblox" → "dev-roblox-key"
	 *	"node"   → "dev-node-key"
	 *	"dash"   → "dev-dash-key"
	 *	But the filter searches it backwards. It doesn't look anything up by what was sent — it scans every entry comparing the value against the header, and when one matches it takes that entry's key as the answer:
	 *
	 *	for (Map.Entry<String, String> entry : apiKeyProperties.getApiKeys().entrySet()) {
	 *		if (entry.getValue().equals(providedKey)) {        // match on the SECRET
	 *			ApiKeyRole role = ApiKeyRole.valueOf(entry.getKey()...);   // take the ROLE
	 */
	private Map<String, String> apiKeys = Map.of();

	public Map<String, String> getApiKeys() {
		return apiKeys;
	}

	public void setApiKeys(Map<String, String> apiKeys) {
		// see application.yml
		this.apiKeys = apiKeys;
	}

	/**
	 * Rejects a configuration that could not work, at startup.
	 *
	 * <p>Two things are checked, and both are failures the running application
	 * could not report clearly on its own.
	 *
	 * <p>UNKNOWN ROLE NAMES. Without this, an entry such as {@code admin:} is
	 * not merely useless: with the filter written defensively it authenticates
	 * nobody, and written naively it produces a {@code ROLE_ADMIN} authority
	 * that no rule in SecurityConfig mentions. That principal then satisfies
	 * {@code .anyRequest().authenticated()} and reaches everything except
	 * telemetry, silently. A typo in YAML should not widen access.
	 *
	 * <p>DUPLICATE SECRETS. Two roles sharing one secret makes the filter's
	 * reverse lookup ambiguous: it scans and takes whichever entry the map
	 * happens to yield first, so the caller's role depends on hash ordering
	 * rather than on configuration. Unlikely with generated keys and cheap to
	 * rule out.
	 */
	@PostConstruct
	void validate() {

		List<String> problems = new ArrayList<>();
		Set<String> seenSecrets = new HashSet<>();

		for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
			String name = entry.getKey();

			if (!isKnownRole(name)) {
				problems.add("unknown role '" + name + "'");
			}

			String secret = entry.getValue();

			if (secret == null || secret.isBlank()) {
				problems.add("role '" + name + "' has an empty key");
			} else if (!seenSecrets.add(secret)) {
				// the secret itself is never named in the message
				problems.add("role '" + name + "' reuses a key already assigned to another role");
			}
		}

		if (!problems.isEmpty()) {
			throw new IllegalStateException(
					"shayveri.security.api-keys is invalid: " + String.join("; ", problems)
							+ ". Valid roles are " + validRoleNames() + " (case-insensitive).");
		}
	}

	private static boolean isKnownRole(String name) {
		for (ApiKeyRole role : ApiKeyRole.values()) {
			if (role.name().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	private static String validRoleNames() {
		List<String> names = new ArrayList<>();
		for (ApiKeyRole role : ApiKeyRole.values()) {
			names.add(role.name().toLowerCase());
		}
		return String.join(", ", names);
	}
}
