package ascore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does {@code /actuator/health} report on the store we are actually using?
 *
 * <p>WHY THIS EXISTS. It did not. Spring Boot contributes a health indicator per
 * auto-configured datastore, so Mongo and Redis both appeared while asdb, the
 * store actually serving telemetry, did not. Worse, Mongo's indicator reported
 * DOWN for a database with no remaining consumers, taking the whole endpoint
 * DOWN with it. A liveness probe on that would restart a working application.
 *
 * <p>Two fixes are asserted here: asdb now contributes an indicator, and
 * Mongo's is disabled while nothing uses it (management.health.mongo.enabled).
 *
 * <p>Deliberately does NOT assert the overall status is UP. Redis is a real
 * dependency and is usually not running locally, so DOWN is the honest answer
 * on a dev machine. The point is WHICH components are reported, not that
 * everything is green.
 */
@SpringBootTest
class HealthReportsTheActiveStoreTest {

	@Autowired
	private HealthEndpoint health;

	@Test
	@DisplayName("asdb appears in health, and Mongo does not while unused")
	void healthReportsTheStoreInUse() {

		String components = health.health().toString() + " " + String.join(",", healthComponentNames());

		assertTrue(components.toLowerCase().contains("asdb"),
				"the active store must appear in /actuator/health, got: " + components);

		assertFalse(components.toLowerCase().contains("mongo"),
				"Mongo has no consumers under shayveri.store=asdb and must not be " + "reported, got: " + components);
	}

	private java.util.Set<String> healthComponentNames() {

		var h = health.health();

		if (h instanceof org.springframework.boot.actuate.health.CompositeHealth composite) {
			return composite.getComponents().keySet();
		}

		return java.util.Set.of();
	}
}
