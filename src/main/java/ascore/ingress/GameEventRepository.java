package ascore.ingress;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Consumed-API declaration for A6, same story as TelemetrySnapshotRepository:
 * Spring Data generates the implementation. saveAll(List) is the call A6
 * will want for event batches. Package-private on purpose.
 *
 * At compile time, TelemetrySnapshotRepository is just an interface, nothing is
 * generated, there's no implementation class on disk. It compiles as a bare contract.
 *
 * At startup, when the Spring application context boots, Spring Data scans for every
 * interface extending MongoRepository, and builds a proxy implementation in memory for
 * each (using reflection), registering it as a bean. That's the object that actually
 * gets injected into your MongoTelemetryStore constructor.
 */
interface GameEventRepository extends MongoRepository<GameEvent, String> { }
