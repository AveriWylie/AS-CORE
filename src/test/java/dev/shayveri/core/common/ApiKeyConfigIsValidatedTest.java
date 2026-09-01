package dev.shayveri.core.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A broken api-key configuration must stop the application, not survive to
 * surprise one request.
 *
 * <p>Plain unit tests against the properties object: no Spring context, because
 * the whole point is that the check runs before anything is serving. Booting a
 * context per case would be slower and would prove less.
 */
class ApiKeyConfigIsValidatedTest {

	private static ApiKeyProperties withKeys(Map<String, String> keys) {
		ApiKeyProperties properties = new ApiKeyProperties();
		properties.setApiKeys(keys);
		return properties;
	}

	private static Map<String, String> valid() {
		Map<String, String> keys = new LinkedHashMap<>();
		keys.put("roblox", "key-a");
		keys.put("node", "key-b");
		keys.put("dash", "key-c");
		return keys;
	}

	@Test
	@DisplayName("the shipped configuration shape is accepted")
	void validConfigurationPasses() {
		assertDoesNotThrow(() -> withKeys(valid()).validate());
	}

	@Test
	@DisplayName("role names are case-insensitive, since YAML keys are lowercase")
	void roleNamesAreCaseInsensitive() {
		// application.yml writes "roblox"; the enum is ROBLOX
		assertDoesNotThrow(() -> withKeys(Map.of("ROBLOX", "key-a")).validate());
		assertDoesNotThrow(() -> withKeys(Map.of("roblox", "key-a")).validate());
	}

	@Test
	@DisplayName("an unknown role name stops startup and is named in the message")
	void unknownRoleIsRejected() {
		/*
		 * The failure this exists to prevent. Left unchecked, an entry like
		 * admin: produces a ROLE_ADMIN authority that no SecurityConfig rule
		 * mentions, which then satisfies .anyRequest().authenticated() and
		 * reaches everything except telemetry. A YAML typo should not widen
		 * access.
		 */
		Map<String, String> keys = valid();
		keys.put("admin", "key-d");

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> withKeys(keys).validate());
		assertTrue(thrown.getMessage().contains("admin"), thrown.getMessage());
		// and it says what IS allowed, so the fix does not need the source
		assertTrue(thrown.getMessage().contains("roblox"), thrown.getMessage());
	}

	@Test
	@DisplayName("an empty key is rejected, since it would authorise a blank header")
	void blankKeyIsRejected() {
		Map<String, String> keys = valid();
		keys.put("dash", "  ");
		assertThrows(IllegalStateException.class, () -> withKeys(keys).validate());
	}

	@Test
	@DisplayName("two roles sharing one secret is rejected as ambiguous")
	void duplicateSecretsAreRejected() {
		/*
		 * The filter finds a role by scanning for a matching secret, so one
		 * secret on two roles makes the answer depend on map iteration order
		 * rather than on configuration.
		 */
		Map<String, String> keys = new LinkedHashMap<>();
		keys.put("roblox", "same");
		keys.put("dash", "same");

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> withKeys(keys).validate());
		assertTrue(thrown.getMessage().contains("reuses"), thrown.getMessage());
	}

	@Test
	@DisplayName("the failure message never contains the secret itself")
	void secretsAreNotLeakedIntoTheMessage() {
		// this message lands in startup logs, which are far less protected than
		// the configuration the secret came from.
		Map<String, String> keys = new LinkedHashMap<>();
		keys.put("roblox", "super-secret-value");
		keys.put("dash", "super-secret-value");

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> withKeys(keys).validate());
		assertTrue(!thrown.getMessage().contains("super-secret-value"), thrown.getMessage());
	}

	@Test
	@DisplayName("no keys at all is allowed, so the app can run fully locked down")
	void emptyMapIsAllowed() {
		// every route then falls to .anyRequest().authenticated() with nobody
		// able to authenticate, which is a coherent state rather than an error.
		assertDoesNotThrow(() -> withKeys(Map.of()).validate());
	}
}
