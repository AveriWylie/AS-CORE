package dev.shayveri.core.egress;

import org.springframework.web.client.RestClient;

import org.springframework.stereotype.Component;

// E2 - the one HTTP call. POST to messaging-service; send {"v":version} pointer, guard < 1KB.
// Consumes: RestClient (post().uri().header().body().retrieve()); non-2xx throws (retry trigger).
@Component
public class OpenCloudClient {
	public void publish(String placeId, int version) {
		throw new UnsupportedOperationException("TODO(shahyar): E2");
	}
}
