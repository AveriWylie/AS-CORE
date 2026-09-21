package ascore.common;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Secret -> role, the same reverse lookup ApiKeyAuthFilter does inline. It exists for the
 * STOMP side, which has no servlet filter chain and so cannot reuse the filter.
 *
 * ApiKeyProperties.validate() has already rejected unknown role names at startup, so
 * valueOf cannot fail here either.
 */
@Component
public class ApiKeyResolver {

	private final ApiKeyProperties apk;

	public ApiKeyResolver(ApiKeyProperties apk) {this.apk = apk;}

	public Optional<ApiKeyRole> resolve(String providedKey) {
		if (providedKey == null) return Optional.empty();

		for (Map.Entry<String, String> entry : apk.getApiKeys().entrySet()) {
			if (entry.getValue().equals(providedKey)) return Optional.of(ApiKeyRole.valueOf(entry.getKey().toUpperCase()));
		}

		return Optional.empty();
	}

}
