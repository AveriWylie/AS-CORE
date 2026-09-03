package dev.shayveri.core.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

// R1 - closes the Phase-1 open-handshake hole: reject CONNECT frames without a DASH key.
// Consumes: ChannelInterceptor (override preSend); StompHeaderAccessor.wrap(msg) -> getCommand()==CONNECT,
//   getFirstNativeHeader("X-Api-Key"); ApiKeyResolver seam (not raw map). Register via B3 configureClientInboundChannel.
// NOTE: HTTP SecurityConfig does NOT cover STOMP frames - this is their equivalent of ApiKeyAuthFilter.
@Component
public class StompAuthInterceptor implements ChannelInterceptor {
	@Override
	public Message<?> preSend(Message<?> message, org.springframework.messaging.MessageChannel channel) {
		return message; // TODO(averi): preSend per blueprint R1.
	}
}
