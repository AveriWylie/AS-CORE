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

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ApiKeyProperties.class)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyProperties apiKeyProperties) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health").permitAll()
						/*
						 * /error must be permitted or EVERY error response becomes a
						 * 403. When a request fails validation, Spring forwards it
						 * internally to /error to render the body, and that forward
						 * arrives WITHOUT the original authentication. anyRequest()
						 * then rejects it, so a missing field returned 403 FORBIDDEN
						 * instead of the 400 this module's spec calls for. Measured,
						 * not theorised: an empty body returned 403 before this line.
						 *
						 * This does not widen access. The real request has already
						 * been authenticated and rejected on its own merits by the
						 * time the forward happens; permitting /error only lets the
						 * explanation through.
						 */
						.requestMatchers("/error").permitAll()
						// A8 step 4: telemetry is ROBLOX-only. Rule order matters,
						// first match wins, so specific rules go above anyRequest().
						// This line is why the controller never checks identity.
						.requestMatchers("/api/telemetry/**").hasRole(ApiKeyRole.ROBLOX.name())
						.anyRequest().authenticated())
							.addFilterBefore(new ApiKeyAuthFilter(apiKeyProperties), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

}
