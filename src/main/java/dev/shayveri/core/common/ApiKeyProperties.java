package dev.shayveri.core.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "shayveri.security")
public class ApiKeyProperties {

	private Map<String, String> apiKeys = Map.of();

	public Map<String, String> getApiKeys() {
		return apiKeys;
	}

	public void setApiKeys(Map<String, String> apiKeys) {
		this.apiKeys = apiKeys;
	}

}
