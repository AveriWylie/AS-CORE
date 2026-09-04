package ascore.ingress;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Consumed-API declaration for A6 - this interface has NO implementation
 * written by anyone. Spring Data sees it at startup and GENERATES the
 * implementation: save(), findById(), findAll(), deleteAll(), and derived
 * queries from method names you add (e.g. List<TelemetrySnapshot>
 * findByPlaceId(String placeId) - Spring parses the method name and writes
 * the query).
 *
 * Type parameters: <the document class, the type of its @Id field>.
 * Package-private on purpose: only MongoTelemetryStore may touch it.
 *
 * Given complete - there is nothing to implement, that is the feature.
 */

interface TelemetrySnapshotRepository extends MongoRepository<TelemetrySnapshot, String> { }
