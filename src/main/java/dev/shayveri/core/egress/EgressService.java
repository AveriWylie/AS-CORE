package dev.shayveri.core.egress;

import dev.shayveri.core.realtime.RealtimePublisher;

import org.springframework.stereotype.Service;

// E4 - internal API Module 4 calls. 3 attempts, backoff; final failure -> DEGRADED alert, RETURN NORMALLY (never throws).
// Consumes: @Service, RealtimePublisher, E2, E3 token bucket.
// TODO(shahyar): publishConfigActivated(placeId, version) per blueprint E4.
@Service
public class EgressService {
	public void publishConfigActivated(String placeId, int version) {
		throw new UnsupportedOperationException("TODO(shahyar): E4");
	}
}
