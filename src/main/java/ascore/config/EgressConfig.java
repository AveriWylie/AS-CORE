package ascore.config;

import ascore.egress.OpenCloudProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds shayveri.opencloud, the same way SecurityConfig binds ApiKeyProperties. A
 * @ConfigurationProperties class does nothing until something enables it, and
 * SecurityConfig does that in its own annotation because it is already a @Configuration.
 * Egress has no configuration class of its own, so this holds that one line.
 *
 * WHAT IS BOUND: api-key and universe-id, blank until the environment supplies them so no
 * real key is ever committed; topic, the MessagingService topic the game subscribes to;
 * base-url, moved only by tests; bucket-capacity and refill-per-second for the token
 * bucket; backoff-base-ms for the spacing between the three attempts.
 *
 * Blank credentials mean push is disabled: EgressService reports DEGRADED without making a
 * request, so a dev machine never fires at Roblox by accident.
 *
 * universe-id is ONE value, so activations reach one experience. That is enough for one
 * game. A second would need a placeId to universe map, and a key with access to each.
 */
@Configuration
@EnableConfigurationProperties(OpenCloudProperties.class)
public class EgressConfig { }
