package ascore.egress;

import ascore.realtime.RealtimePublisher;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// E4 - internal API Module 4 calls. 3 attempts, backoff; final failure -> DEGRADED alert, RETURN NORMALLY (never throws).
// Consumes: @Service, RealtimePublisher, E2, E3 token bucket.
@Service
public class EgressService {

	private static final Logger log = LoggerFactory.getLogger(EgressService.class);
	private static final int ATTEMPTS = 3;

	private final OpenCloudClient client;
	private final RealtimePublisher publisher;
	private final OpenCloudProperties props;
	private final TokenBucket bucket;

	@Autowired
	public EgressService(OpenCloudClient client, RealtimePublisher publisher, OpenCloudProperties props) {
		this(client, publisher, props, new TokenBucket(props.getBucketCapacity(), props.getRefillPerSecond(), System::nanoTime));
	}

	// tests hand in a bucket on a fake clock
	EgressService(OpenCloudClient client, RealtimePublisher publisher, OpenCloudProperties props, TokenBucket bucket) {
		this.client = client;
		this.publisher = publisher;
		this.props = props;
		this.bucket = bucket;
	}

	/**
	 * Up to three attempts with backoff base, 2x base, 4x base between them. An empty bucket
	 * uses up an attempt without sending anything, so the limit is never exceeded, only
	 * waited on. An oversized message is not retried, since sending it again cannot help.
	 * Whatever happens, this returns normally.
	 */
	public void publishConfigActivated(String placeId, int version) {
		if (!props.isConfigured()) {
			degraded(placeId, version, "opencloud api-key or universe-id not set");
			return;
		}

		String reason = "no attempt made";
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			if (attempt > 0) sleep(props.getBackoffBaseMs() << (attempt - 1));

			if (!bucket.tryAcquire()) {
				reason = "rate limited locally";
				continue;
			}
			try {
				client.publish(placeId, version);
				publisher.publish("/topic/config", Map.of("placeId", placeId, "version", version, "delivery", "PUSHED"));
				return;
			} catch (IllegalStateException e) {
				reason = e.getMessage();
				break;
			} catch (RuntimeException e) {
				reason = e.getMessage();
				log.warn("open cloud push attempt {} for {} v{} failed: {}", attempt + 1, placeId, version, reason);
			}
		}
		degraded(placeId, version, reason);
	}

	// the poll path still delivers the version; this only says the fast path did not
	private void degraded(String placeId, int version, String reason) {
		log.error("open cloud push DEGRADED for {} v{}: {}", placeId, version, reason);
		publisher.publish("/topic/config", Map.of("placeId", placeId, "version", version, "delivery", "DEGRADED"));
		publisher.publish("/topic/alerts", Map.of("event", "opencloud-degraded", "placeId", placeId, "version", version, "reason", String.valueOf(reason)));
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
