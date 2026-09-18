package ascore.config;

import ascore.egress.OpenCloudProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// binds shayveri.opencloud, the same way SecurityConfig binds ApiKeyProperties
@Configuration
@EnableConfigurationProperties(OpenCloudProperties.class)
public class EgressConfig { }
