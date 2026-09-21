package ascore.config;

import ascore.common.ApiKeyAuthFilter;
import ascore.common.ApiKeyProperties;
import ascore.common.ApiKeyRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authorisation: role plus path in, allow or deny out. Authentication happens before this in
 * ApiKeyAuthFilter, which never rejects anyone, it only sets a role or leaves the caller
 * anonymous. That split is why an unknown key gets 403 rather than 401.
 *
 * Rules are matched in order, first match wins, so every specific rule goes above
 * anyRequest(). Adding an endpoint means adding a line here; there is no role check in any
 * controller.
 *
 * /ws/** is permitAll on purpose. The handshake is HTTP, but STOMP frames ride inside the
 * socket after the upgrade, where no servlet filter runs, so realtime/StompAuthInterceptor
 * is the check for those. See documentation/architecture/security.md.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ApiKeyProperties.class)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyProperties apiKeyProperties) throws Exception {
		// CSRF protects a session held in a COOKIE, which a browser attaches to a forged request
		// on its own; the token is what a forger cannot supply. Nothing here uses cookies. Every
		// caller sends X-Api-Key itself, which a cross-site request cannot do, so the token would
		// guard nothing and break every client.
		http.csrf(csrf -> csrf.disable())
				// No session is created and none is looked for: each request re-authenticates from
				// its own header. That is why a revoked key takes effect immediately, and why
				// nothing would have to be shared if this ran as more than one instance.
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(auth ->
						// an orchestrator probes health before it holds any key, and it reveals nothing
						auth.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/ws/**").permitAll()
						// A8 step 4: telemetry is ROBLOX-only. Rule order matters,
						// first match wins, so specific rules go above anyRequest().
						// This line is why the controller never checks identity.
						.requestMatchers("/api/telemetry/**").hasRole(ApiKeyRole.ROBLOX.name())
						.requestMatchers("/api/nodes/register", "/api/nodes/*/heartbeat")
								.hasRole(ApiKeyRole.NODE.name())
						.requestMatchers("/api/nodes").hasRole(ApiKeyRole.DASH.name())
						.requestMatchers("/api/jobs/claim", "/api/jobs/*/progress",
								"/api/jobs/*/complete", "/api/jobs/*/fail").hasRole(ApiKeyRole.NODE.name())
						.requestMatchers("/api/jobs").hasRole(ApiKeyRole.DASH.name())
						.requestMatchers("/api/config/active").hasRole(ApiKeyRole.ROBLOX.name())
						.requestMatchers("/api/config/**").hasRole(ApiKeyRole.DASH.name())
						.requestMatchers("/api/snapshot").hasRole(ApiKeyRole.DASH.name())
						.requestMatchers("/api/audit").hasRole(ApiKeyRole.DASH.name())
						// default deny: anything not named above still needs a recognised key, so a new
						// endpoint is unreachable rather than unprotected until a rule is added for it
						.anyRequest().authenticated())
						// added BEFORE the rules above are evaluated: identity first, then permission
						.addFilterBefore(new ApiKeyAuthFilter(apiKeyProperties), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

}
