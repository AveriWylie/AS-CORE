package ascore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Does the application start at all, on whatever application.yml selects?
 *
 * <p>WHY THIS EXISTS. Until this was written, SHAYVERI had never been booted,
 * and it did not boot. Two separate startup failures were sitting in the code,
 * neither of which any existing test could see, because every other test either
 * exercises a class in isolation or needs Docker and gets skipped:
 *
 * <ol>
 * <li>{@code TelemetryService} injects a bare {@code Executor}, and enabling
 * WebSocket/STOMP publishes three more Executor beans, so Spring found four
 * candidates and refused to guess. Fixed with {@code @Qualifier}.</li>
 * <li>{@code WebSocketConfig} had empty override bodies, so no STOMP endpoint
 * was registered and {@code subProtocolWebSocketHandler} failed on startup
 * with "No handlers".</li>
 * </ol>
 *
 * <p>Both were cheap to fix and expensive to find, and both would have hit on
 * the first {@code bootRun}. Neither had anything to do with storage: the app
 * failed identically on Mongo and on asdb.
 *
 * <p>The default is now {@code shayveri.store: asdb}, so this covers the asdb
 * path. It deliberately does NOT require a running asdb server: an unreachable
 * one logs an error and the context still starts, matching how Spring Data
 * Mongo tolerates a down database at boot. That tolerance is why the error had
 * to be made loud, see AsdbTelemetryStore.ensureSchema.
 *
 * <p>Needs no Docker, no database and no network. It only asserts the context
 * loads, which is the cheapest possible guard against the whole application
 * being unable to start.
 */
@SpringBootTest
class DefaultContextStartsTest {

	@Test
	@DisplayName("the Spring context loads on the default configuration")
	void contextLoads() {
		// The assertion IS the startup. If the context cannot be built,
		// @SpringBootTest fails before reaching this body.
	}
}
