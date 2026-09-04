package ascore.ingress;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.MongoRepository;


interface TelemetrySnapshotRepository extends MongoRepository<TelemetrySnapshot, String> { }
