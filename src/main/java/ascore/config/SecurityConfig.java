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

				.authorizeHttpRequests(auth ->
						auth.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/ws/**").permitAll()
						// A8 step 4: telemetry is ROBLOX-only. Rule order matters,
						// first match wins, so specific rules go above anyRequest().
						// This line is why the controller never checks identity.
						.requestMatchers("/api/telemetry/**").hasRole(ApiKeyRole.ROBLOX.name())
						.anyRequest().authenticated())
						.addFilterBefore(new ApiKeyAuthFilter(apiKeyProperties), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

}
