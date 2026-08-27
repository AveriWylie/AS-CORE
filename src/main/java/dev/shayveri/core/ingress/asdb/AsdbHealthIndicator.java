package dev.shayveri.core.ingress.asdb;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reports asdb's reachability to {@code /actuator/health}.
 *
 * <p>WHY THIS WAS NEEDED. Spring Boot contributes a health indicator for every
 * datastore it autoconfigures, so Mongo and Redis both appeared in
 * {@code /actuator/health} while asdb, the store actually serving telemetry,
 * did not. The endpoint reported on two backends the app was not using and
 * stayed silent about the one it was.
 *
 * <p>Registered only when asdb is the selected store, matching
 * {@link AsdbTelemetryStore}. A health check for a backend that is switched off
 * would be the same mistake in the other direction.
 *
 * <p>The check is a GET on asdb's own {@code /health}, which is cheap and does
 * not touch storage. It deliberately does not run a query: a health probe that
 * writes, or that takes the server's global mutex, turns a monitoring endpoint
 * into a source of load.
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbHealthIndicator implements HealthIndicator {

	private final AsdbTelemetryStore store;

	public AsdbHealthIndicator(AsdbTelemetryStore store) {this.store = store;}

	@Override
	public Health health() {
		return store.isHealthy() ? Health.up().withDetail("backend", "asdb").build() : Health.down()
				.withDetail("backend", "asdb")
				.withDetail("hint", "is the asdb server running?")
				.build();
	}
}
