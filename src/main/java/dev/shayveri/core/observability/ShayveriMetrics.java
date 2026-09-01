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
 *
 * TODO(shahyar): inject MeterRegistry; implement:
 *   telemetryAccepted()      -> counter shayveri_telemetry_ingest_total
 *   openCloudOutcome(ok)     -> counter shayveri_opencloud_calls_total{outcome}
 *   jobTransition(from,to)   -> counter shayveri_job_transitions_total{from,to}
 *   register gauges shayveri_queue_depth{type} and shayveri_ws_sessions in the constructor
 *   from Suppliers (polled on scrape, ~free between scrapes).
 * Call sites (added when this module lands): TelemetryService, EgressService, JobService - one line each.
 */
@Component
public class ShayveriMetrics {
	// TODO(shahyar): constructor(MeterRegistry) + the wrappers above.
}
