package dev.shayveri.core.egress;

import org.springframework.boot.context.properties.ConfigurationProperties;

// E1 - Open Cloud config from application.yml. Consumes: @ConfigurationProperties (ApiKeyProperties pattern).
// TODO(shahyar): fields apiKey, universeId, topic + getters/setters.
@ConfigurationProperties(prefix = "shayveri.opencloud")
public class OpenCloudProperties {
}
