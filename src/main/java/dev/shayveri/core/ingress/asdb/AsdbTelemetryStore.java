package dev.shayveri.core.ingress.asdb;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import dev.shayveri.core.ingress.GameEvent;
import dev.shayveri.core.ingress.TelemetrySnapshot;
import dev.shayveri.core.ingress.TelemetryStore;

/**
 * A {@link TelemetryStore} backed by asdb instead of MongoDB.
 *
 * <p>THIS IS WHAT RULE 5 BOUGHT. {@code TelemetryStore} is the seam, so nothing
 * above it changes: {@code TelemetryService} still calls {@code saveSnapshot}
 * and {@code saveEvents}, the controller is untouched, and the entity classes
 * keep their Spring Data annotations. Swapping the whole storage engine is one
 * property.
 *
 * <p>SELECTING IT. Both this and {@code MongoTelemetryStore} implement the same
 * interface, so exactly one must be active or Spring fails to start with an
 * ambiguous-bean error. That is what the {@code @ConditionalOnProperty} pair
 * does.
 *
 * <p>ASDB IS NOW THE DEFAULT: application.yml sets {@code shayveri.store: asdb},
 * so this is the active implementation unless something overrides it. Mongo is
 * the fallback rather than the other way round, and is one flag away:
 *
 * <pre>
 *   ./gradlew bootRun --args='--shayveri.store=mongo'
 * </pre>
 *
 * <p>Note the code still treats an ABSENT property as Mongo
 * ({@code matchIfMissing = true} on MongoTelemetryStore). That is deliberate:
 * the default lives in configuration, where it is visible and overridable,
 * rather than being compiled in.
 *
 * <p>WHAT IS NOT EQUIVALENT TO MONGO, stated here rather than discovered later:
 *
 * <ul>
 * <li><b>TTL is configured on the server, not by the annotation.</b>
 * {@code @Indexed(expireAfter = "7d")} on {@code TelemetrySnapshot.receivedAt}
 * is read by Spring Data and would be read by Mongo. asdb has no TTL index; its
 * server runs a sweeper configured with a command-line flag:
 * <pre>asdb telemetry.db --ttl telemtry_snapshots.receivedAt=7d</pre>
 * So the annotation stays true as documentation but stops being the thing that
 * enforces it. If the flag is missing, the collection grows forever and nothing
 * fails. That is the sharpest edge in this whole adapter.</li>
 *
 * <li><b>No generated ids.</b> Mongo fills a null {@code @Id} with an ObjectId.
 * asdb does not, and the mapper omits the field instead. Nothing in the ingress
 * path reads ids back, so this is currently invisible, but a read path would
 * have to deal with it.</li>
 *
 * <li><b>Writes are not transactional.</b> {@code saveEvents} sends one batched
 * statement, so it is one request, but asdb has no transactions: a failure
 * partway through leaves the earlier documents written. Mongo's
 * {@code saveAll} is not atomic across documents either, so this is a match in
 * practice rather than a regression.</li>
 *
 * <li><b>One writer at a time.</b> The asdb server serializes every statement
 * behind a mutex, so concurrent telemetry posts queue rather than run in
 * parallel.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbTelemetryStore implements TelemetryStore {

	private static final Logger log = LoggerFactory.getLogger(AsdbTelemetryStore.class);
	private final AsdbClient client;
	private final String url;

	public AsdbTelemetryStore(@Value("${shayveri.store.asdb.url:http://127.0.0.1:7070}") String url,
							  @Value("${shayveri.store.asdb.connect-timeout-ms:2000}") long connectTimeoutMs,
							  @Value("${shayveri.store.asdb.request-timeout-ms:5000}") long requestTimeoutMs) {

		this.url = url;
		this.client = new AsdbClient(url, Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(requestTimeoutMs));
		ensureSchema();
	}

	@Override
	public void saveSnapshot(TelemetrySnapshot snapshot) {client.execute(AsdbEntityMapper.insertStatement(snapshot));}

	@Override
	public void saveEvents(List<GameEvent> events) {
		// An empty batch is a no-op rather than a malformed statement. The
		// service layer can hand over whatever the request contained without
		// having to check first.
		if (events == null || events.isEmpty()) {
			return;
		}

		client.execute(AsdbEntityMapper.insertStatement(events));
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
		/*
		 * Health is checked FIRST so an unreachable server is reported as
		 * exactly that. Without this, every schema step fails and logs
		 * "skipped", which is the same message a healthy server produces on
		 * every restart after the first (because the collection already
		 * exists). A dead database and a normal restart looked identical,
		 * which is the worst possible pair of things to conflate.
		 */
		if (!client.isHealthy()) {
			log.error("asdb is UNREACHABLE. Telemetry writes will fail until it is running. "
					+ "Start it with: asdb telemetry.db --port 7070 "
					+ "--ttl telemtry_snapshots.receivedAt=7d   "
					+ "(or set shayveri.store=mongo to use MongoDB instead)");
			return;
		}

		for (Class<?> entity : List.of(TelemetrySnapshot.class, GameEvent.class)) {
			String collection = AsdbEntityMapper.collectionOf(entity);
			attempt("create " + collection + " {}");
			for (String field : AsdbEntityMapper.indexedFieldsOf(entity)) {
				attempt("create index on " + collection + "." + field);
			}
		}

		log.info("asdb schema ready at {}", url);
	}

	/*
	 * Startup DDL is not fatal, because "already exists" is the normal case on
	 * every restart after the first and asdb reports it as an error rather than
	 * a no-op. Logged at DEBUG for that reason: by the time this runs the
	 * server is known reachable, so a failure here is almost always benign.
	 */
	private void attempt(String statement) {
		try {
			client.execute(statement);
		} catch (AsdbClient.AsdbException e) {
			log.debug("asdb schema step skipped ({}): {}", statement, e.getMessage());
		}
	}

	/** Exposed so a health indicator or a test can check the server is reachable. */
	public boolean isHealthy() {return client.isHealthy();}
}
