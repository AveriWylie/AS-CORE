package ascore.egress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;

// E2 - the one HTTP call. POST to messaging-service; send {"v":version} pointer, guard < 1KB.
// Consumes: RestClient (post().uri().header().body().retrieve()); non-2xx throws (retry trigger).
@Component
public class OpenCloudClient {

	// Open Cloud rejects a message over 1KB; the pointer is ~20 bytes, so tripping this is our bug
	static final int MAX_BYTES = 1024;

	private final RestClient http;
	private final OpenCloudProperties props;
	private final ObjectMapper json;

	// Spring Boot's RestClient.Builder, so tests can bind MockRestServiceServer to the same builder
	public OpenCloudClient(RestClient.Builder builder, OpenCloudProperties props, ObjectMapper json) {
		this.http = builder.baseUrl(props.getBaseUrl()).build();
		this.props = props;
		this.json = json;
	}

	/**
	 * Sends only the version. The config itself is fetched by the game from
	 * /api/config/active on receipt, which is also the path it polls if this never arrives.
	 * placeId is not in the message: the topic is universe-wide and each server refetches
	 * its own place.
	 */
	public void publish(String placeId, int version) {
		String body = body(version);
		http.post()
				.uri("/messaging-service/v1/universes/{universeId}/topics/{topic}", props.getUniverseId(), props.getTopic())
				.header("x-api-key", props.getApiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
	}

	String body(int version) {
		try {
			String message = json.writeValueAsString(Map.of("v", version));
			String body = json.writeValueAsString(Map.of("message", message));
			if (body.getBytes(StandardCharsets.UTF_8).length >= MAX_BYTES) {
				throw new IllegalStateException("Open Cloud message is " + body.length() + " bytes, limit " + MAX_BYTES);
			}
			return body;
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

}
