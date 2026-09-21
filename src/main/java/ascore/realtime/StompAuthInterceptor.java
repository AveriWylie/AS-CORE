package ascore.realtime;

import ascore.common.ApiKeyAuthFilter;
import ascore.common.ApiKeyResolver;
import ascore.common.ApiKeyRole;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** R1 - closes the Phase-1 open-handshake hole: reject CONNECT frames without a DASH key.
 *Consumes: ChannelInterceptor (override preSend); StompHeaderAccessor.wrap(msg) -> getCommand()==CONNECT,
 *  getFirstNativeHeader("X-Api-Key"); ApiKeyResolver seam (not raw map). Register via B3 configureClientInboundChannel.
 * NOTE: HTTP SecurityConfig does NOT cover STOMP frames - this is their equivalent of ApiKeyAuthFilter.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

	private final ApiKeyResolver keys;

	public StompAuthInterceptor(ApiKeyResolver keys) {this.keys = keys;}

	 // Throwing here kills the CONNECT before the broker sees it; Spring answers the client
	 // with an ERROR frame and closes the socket. An accepted session gets the role as its
	 // user, which is also what makes it count in SimpUserRegistry.
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) return message;

		ApiKeyRole role = keys.resolve(accessor.getFirstNativeHeader(ApiKeyAuthFilter.HEADER))
				.filter(r -> r == ApiKeyRole.DASH)
				.orElseThrow(() -> new AccessDeniedException("a DASH key is required to connect"));

		accessor.setUser(new UsernamePasswordAuthenticationToken(role, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));

		return message;
	}

}
