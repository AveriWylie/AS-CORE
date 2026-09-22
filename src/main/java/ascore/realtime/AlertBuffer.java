package ascore.realtime;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The last 50 alerts, newest first, for the snapshot. Fed by StompRealtimePublisher
 * as it sends each /topic/alerts payload. Memory only: a restart loses them, which is fine
 * for alerts, and lasting history is the audit trail. See documentation/architecture/realtime.md.
 */
@Component
public class AlertBuffer {

	static final int CAPACITY = 50;

	private final Deque<Map<String, Object>> alerts = new ArrayDeque<>();

	public synchronized void add(Object alert) {
		alerts.addFirst(Map.of("at", Instant.now(), "alert", alert));
		if (alerts.size() > CAPACITY) alerts.removeLast();
	}

	public synchronized List<Map<String, Object>> recent() {return List.copyOf(alerts);}

}
