package ascore.egress;

import org.springframework.boot.context.properties.ConfigurationProperties;

// E1 - Open Cloud config from application.yml. Consumes: @ConfigurationProperties (ApiKeyProperties pattern).
@ConfigurationProperties(prefix = "shayveri.opencloud")
public class OpenCloudProperties {
	// TODO(shahyar): fields + getters/setters per blueprint E1.
}
