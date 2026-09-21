package ascore.config;

import ascore.realtime.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * B3 - turns on the STOMP broker (Phase-1-minimal slice of Module 6).
 *
 * Consumes (not ours):
 *   @EnableWebSocketMessageBroker - activates Spring's whole STOMP stack,
 *       including the SimpMessagingTemplate bean that B2 injects.
 *   WebSocketMessageBrokerConfigurer - the configuration interface; we
 *       override exactly two of its methods:
 *       registerStompEndpoints(StompEndpointRegistry) - WHERE clients connect
 *       configureMessageBroker(MessageBrokerRegistry) - WHAT destinations exist
 *
 * SECURITY NOTE (per the plan's Module 6 design): handshake auth for the
 * DASH key in the CONNECT frame is deliberately NOT here yet - it comes
 * when Module 6 is built properly. Phase-1-minimal means open handshake;
 * leave the seam obvious.
 *
 * Done when: the app boots, and a raw STOMP client can connect to /ws and
 * subscribe to /topic/anything without error (D4 automates this).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthInterceptor auth;

	public WebSocketConfig(StompAuthInterceptor auth) {this.auth = auth;}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
	}

	// R1: every frame a client sends passes the interceptor before the broker sees it
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(auth);
	}

}
