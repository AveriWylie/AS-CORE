package ascore.observability;

import ascore.jobs.JobType;
import ascore.jobs.QueueStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * V2 - the single metrics entry point. Modules call these named wrappers, never the raw registry
 * (same discipline as RealtimePublisher: ours in front of the framework).
 *
 * Consumes: MeterRegistry (Micrometer, already on classpath via Actuator since Phase 0) -
 *   registry.counter(name, tags...).increment(); Gauge.builder(name, supplier).register(registry).
 * Needs V1 dependency micrometer-registry-prometheus for /actuator/prometheus to serve.
 */
@Component
public class AsCoreMetrics {

	/*
	Names are Micrometer's dotted form. The Prometheus registry rewrites them on scrape:
	dots become underscores and counters gain _total, so shayveri.telemetry.ingest is
	served as shayveri_telemetry_ingest_total.

	The gauges hold a supplier, not a value. Micrometer calls it on each scrape, so queue
	depth costs one Redis read per scrape and nothing in between.
	*/
	private final MeterRegistry registry;

	public AsCoreMetrics(MeterRegistry registry, QueueStore queue, SimpUserRegistry sessions) {
		this.registry = registry;
		for (JobType type : JobType.values()) {
			Gauge.builder("shayveri.queue.depth", () -> queue.depth(type)).tag("type", type.name()).register(registry);
		}
		Gauge.builder("shayveri.ws.sessions", sessions::getUserCount).register(registry);
	}

	public void telemetryAccepted() {registry.counter("shayveri.telemetry.ingest").increment();}

	public void openCloudOutcome(boolean ok) {registry.counter("shayveri.opencloud.calls", "outcome", ok ? "ok" : "error").increment();}

	public void jobTransition(String from, String to) {registry.counter("shayveri.job.transitions", "from", from, "to", to).increment();}

}
