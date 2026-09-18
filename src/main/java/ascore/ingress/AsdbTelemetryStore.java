package ascore.ingress;

import java.time.Duration;
import ascore.asdb.AsdbBinaryClient;
import ascore.asdb.AsdbClient;
import ascore.asdb.AsdbEntityMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A TelemetryStore backed by asdb instead of MongoDB. See
 * documentation/architecture/asdb.md for where this sits, the two transports,
 * and where asdb does not match Mongo.
 *
 * SELECTING IT. This and MongoTelemetryStore implement the same interface, so
 * exactly one must be active or Spring fails to start with an ambiguous-bean
 * error. That is what the @ConditionalOnProperty pair does.
 *
 * application.yml sets shayveri.store: asdb, so this is active unless
 * overridden. Mongo is one flag away:
 *
 *   ./gradlew bootRun --args='--shayveri.store=mongo'
 *
 * An ABSENT property still means Mongo (matchIfMissing = true on
 * MongoTelemetryStore). The default lives in configuration, where it is
 * visible and overridable, rather than compiled in.
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbTelemetryStore implements TelemetryStore {

	private static final Logger log = LoggerFactory.getLogger(AsdbTelemetryStore.class);
	private final Transport transport;
	private final String endpoint;

	public AsdbTelemetryStore(
			@Value("${shayveri.store.asdb.url:http://127.0.0.1:7070}") String url,
			@Value("${shayveri.store.asdb.abp-host:127.0.0.1}") String abpHost,
			@Value("${shayveri.store.asdb.abp-port:7071}") int abpPort,
			@Value("${shayveri.store.asdb.protocol:binary}") String protocol,
			@Value("${shayveri.store.asdb.max-idle-connections:8}") int maxIdle,
			@Value("${shayveri.store.asdb.connect-timeout-ms:2000}") long connectTimeoutMs,
			@Value("${shayveri.store.asdb.request-timeout-ms:5000}") long requestTimeoutMs) {

		Duration connectTimeout = Duration.ofMillis(connectTimeoutMs);
		Duration requestTimeout = Duration.ofMillis(requestTimeoutMs);

		if ("http".equalsIgnoreCase(protocol)) {
			this.endpoint = url;
			this.transport = new HttpTransport(new AsdbClient(url, connectTimeout, requestTimeout));
		} else {
			// Anything not explicitly "http" is binary. An unrecognised value
			// choosing the FASTER path rather than silently degrading is the
			// safe direction to be wrong in: a typo costs nothing, whereas a
			// typo that quietly halved throughput would go unnoticed for months.
			this.endpoint = "abp://" + abpHost + ":" + abpPort;
			this.transport = new BinaryTransport(new AsdbBinaryClient(abpHost, abpPort, connectTimeout, requestTimeout, maxIdle));
		}

		ensureSchema();
	}

	@Override
	public void saveSnapshot(TelemetrySnapshot snapshot) {
		transport.insert(AsdbEntityMapper.collectionOf(TelemetrySnapshot.class), List.of(snapshot));
	}

	@Override
	public void saveEvents(List<GameEvent> events) {
		// An empty batch is a no-op rather than a malformed statement. The
		// service layer can hand over whatever the request contained without
		// having to check first.
		if (events == null || events.isEmpty()) return;
		transport.insert(AsdbEntityMapper.collectionOf(GameEvent.class), events);
	}

	/**
	 * The two ways to reach asdb, behind one method each.
	 *
	 * <p>Kept as a seam for the same reason {@code TelemetryStore} is one: the
	 * code above it should not know or care which wire format is in use, and
	 * having both paths implement one interface is what stops them drifting
	 * into different behaviour. Both must accept the same entities and store
	 * the same documents; {@code AsdbTransportParityTest} is what enforces it.
	 */
	private interface Transport {
		void insert(String collection, List<?> entities);
		// One ASL statement, used for startup DDL.
		void ddl(String statement);
		boolean healthy();
	}

	// ASL text over HTTP. The original path, kept as the fallback and for curl parity
	private record HttpTransport(AsdbClient client) implements Transport {
		@Override
		public void insert(String collection, List<?> entities) {
			client.execute(AsdbEntityMapper.insertStatement(collection, entities));
		}

		@Override
		public void ddl(String statement) {client.execute(statement);}

		@Override
		public boolean healthy() {return client.isHealthy();}
	}

	// Binary documents over ABP/1. Values are never lexed, so nothing needs escaping
	private record BinaryTransport(AsdbBinaryClient client) implements Transport {
		@Override
		public void insert(String collection, List<?> entities) {

			List<Map<String, Object>> docs = entities.stream()
					.map(AsdbEntityMapper::toMap)
					.map(m -> (Map<String, Object>) m)
					.toList();

			client.insert(collection, docs);
		}

		@Override
		public void ddl(String statement) {client.execute(statement);}

		@Override
		public boolean healthy() {return client.isHealthy();}
	}


	/**
	 * Creates the collections and indexes this store needs, once, at startup.
	 *
	 * <p>Mongo creates a collection implicitly on first write and builds
	 * {@code @Indexed} indexes itself. asdb does neither: an insert into an
	 * unknown collection is an error, and indexes are explicit DDL. So the
	 * annotations have to be applied by something, and startup is the right
	 * place. Doing it per-write would mean a redundant statement on the hot
	 * path.
	 *
	 * <p>Failures are logged, not thrown. "Already exists" is the normal case on
	 * every restart after the first, and asdb reports it as an error rather than
	 * a no-op, so treating it as fatal would mean the application only ever
	 * starts once. The cost is that a genuinely broken database is not caught
	 * here; it surfaces on the first write instead.
	 */
	private void ensureSchema() {
		if (!transport.healthy()) {
			log.error("asdb is UNREACHABLE at {}. Telemetry writes will fail until it is running. "
					+ "Start it with: asdb telemetry.db --port 7070 --abp-port 7071 "
					+ "--ttl telemtry_snapshots.receivedAt=7d   "
					+ "(or set shayveri.store=mongo to use MongoDB instead)", endpoint);
			return;
		}

		for (Class<?> entity : List.of(TelemetrySnapshot.class, GameEvent.class)) {
			String collection = AsdbEntityMapper.collectionOf(entity);
			attempt("create " + collection + " {}");
			for (String field : AsdbEntityMapper.indexedFieldsOf(entity)) {
				attempt("create index on " + collection + "." + field);
			}
		}

		log.info("asdb schema ready at {}", endpoint);
	}


	/**
	 * Startup DDL is not fatal, because "already exists" is the normal case on
	 * every restart after the first and asdb reports it as an error rather than
	 * a no-op. Logged at DEBUG for that reason: by the time this runs the
	 * server is known reachable, so a failure here is almost always benign.
	 */
	private void attempt(String statement) {
		try {
			transport.ddl(statement);
		} catch (AsdbClient.AsdbException e) {
			log.debug("asdb schema step skipped ({}): {}", statement, e.getMessage());
		}
	}

	// Exposed so a health indicator or a test can check the server is reachable
	public boolean isHealthy() {return transport.healthy();}
}
