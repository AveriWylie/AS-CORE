package ascore.ingress;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import ascore.asdb.AsdbBinaryClient;

/**
 * The store contract, run against asdb.
 *
 * <p>Needs an asdb server with both listeners, and skips rather than fails
 * without one:
 *
 * <pre>
 *   asdb telemetry.db --port 7070 --abp-port 7071 --ttl telemtry_snapshots.receivedAt=7d
 * </pre>
 *
 * <p>No Docker. That is the point of asdb: it is a native binary, so the same
 * contract that needs a container to run against Mongo runs here against a
 * process you already have.
 */
@EnabledIf("asdbReachable")
@SpringBootTest
@TestPropertySource(properties = {
		"shayveri.store=asdb",
		"shayveri.store.asdb.protocol=binary",
		"shayveri.store.asdb.abp-port=7071"
})

class AsdbStoreContractTest extends TelemetryStoreContract {

	private static final String HOST = "127.0.0.1";
	private static final int ABP_PORT = 7071;

	static boolean asdbReachable() {
		try (AsdbBinaryClient probe = client()) {
			return probe.isHealthy();
		} catch (Exception e) {
			return false;
		}
	}

	private static AsdbBinaryClient client() {
		return new AsdbBinaryClient(HOST, ABP_PORT, Duration.ofSeconds(2), Duration.ofSeconds(5), 4);
	}

	@Override
	protected String storeName() {
		return "asdb";
	}

	@Override
	protected List<Map<String, Object>> findByPlaceId(String collection, String placeId) {
		try (AsdbBinaryClient client = client()) {
			/*
			 * Reads the collection and filters in Java rather than pushing the
			 * marker into a WHERE clause.
			 *
			 * Deliberate: one of the contract tests stores a placeId containing
			 * ASL syntax, and a filter built by string concatenation would be
			 * testing the harness's own escaping rather than the store's
			 * behaviour. Comparing in Java has no escaping to get wrong.
			 */
			return client.query("from " + collection).stream()
					.filter(doc -> placeId.equals(doc.get("placeId")))
					.toList();
		}
	}
}
