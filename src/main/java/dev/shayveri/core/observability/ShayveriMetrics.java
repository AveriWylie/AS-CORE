package dev.shayveri.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
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
public class ShayveriMetrics {
	// TODO(shahyar): constructor + metric wrappers per blueprint V2.
}
