package ascore;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import ascore.ingress.MongoTelemetryStore;
import ascore.ingress.TelemetryStore;

/**
 * The fallback still works: shayveri.store=mongo selects the Mongo store and
 * the context still starts, with no asdb server involved.
 *
 * <p>Guards the escape hatch. asdb is the default now, so the value of that
 * default rests entirely on being able to switch back in one flag when
 * something is wrong with it.
 */
@SpringBootTest
@TestPropertySource(properties = "shayveri.store=mongo")
class MongoFallbackStillWorksTest {

	@Autowired
	private TelemetryStore store;

	@Test
	@DisplayName("shayveri.store=mongo selects the Mongo implementation")
	void mongoIsSelectable() {
		assertInstanceOf(MongoTelemetryStore.class, store, "the fallback must still resolve, got "
				+ store.getClass().getName());
	}
}
