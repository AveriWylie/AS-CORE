package ascore.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * V6 - fire-and-forget audit writes on the virtual executor (an audit write must never slow or fail
 * a mutation; on failure it logs loudly instead). Call sites (the plan's law - EVERY DASH mutation):
 * ConfigService save+activate, JobService create, future key management.
 * Consumes: @Service, Executor (AsyncConfig bean), AuditStore (V4, rule-5 seam).
 */
@Service
public class AuditService {

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);

	private final AuditStore store;
	private final Executor executor;

	public AuditService(AuditStore store, @Qualifier("telemetryExecutor") Executor executor) {
		this.store = store;
		this.executor = executor;
	}

	/**
	 * Returns at once; the write happens on a virtual thread. before and after may be a map,
	 * which is stored as given, a single value, stored as {"value": it}, or null for none.
	 */
	public void audit(String who, String action, String target, Object before, Object after) {
		AuditRecord record = new AuditRecord(Instant.now(), who, action, target, asMap(before), asMap(after));
		executor.execute(() -> {
			try {
				store.record(record);
			} catch (RuntimeException e) {
				log.error("AUDIT WRITE LOST: {} {} on {} by {}", action, after, target, who, e);
			}
		});
	}

	public List<AuditRecord> query(Instant from, Instant to, Optional<String> action) {return store.query(from, to, action);}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		if (value == null) return Map.of();
		if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
		return Map.of("value", value);
	}

}
