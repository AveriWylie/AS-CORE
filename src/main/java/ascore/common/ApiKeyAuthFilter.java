package ascore.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Api-Key";

	private final ApiKeyProperties apk;

	public ApiKeyAuthFilter(ApiKeyProperties apk) {this.apk = apk;}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String providedKey = request.getHeader(HEADER);

		if (providedKey != null) {
			for (Map.Entry<String, String> entry : apk.getApiKeys().entrySet()) {
				if (entry.getValue().equals(providedKey)) {
					/*
					 * valueOf cannot fail here: ApiKeyProperties.validate()
					 * rejects an unknown role name at startup, so by the time a
					 * request arrives every configured name is a real role.
					 *
					 * That ordering is the point. This runs inside a servlet
					 * filter, before DispatcherServlet, so anything thrown here
					 * bypasses GlobalExceptionHandler entirely and reaches the
					 * caller as a raw container error. Configuration is checked
					 * where a human is watching instead.
					 */
					ApiKeyRole role = ApiKeyRole.valueOf(entry.getKey().toUpperCase());
					var authentication = new UsernamePasswordAuthenticationToken(role,
							null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
					SecurityContextHolder.getContext().setAuthentication(authentication);
					break;
				}
			}
		}
		filterChain.doFilter(request, response);
	}
}
