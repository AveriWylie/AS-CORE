package ascore.observability;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

// package-private so only MongoAuditStore reaches it
interface AuditRepository extends MongoRepository<AuditRecord, String> {

	List<AuditRecord> findByAtBetweenOrderByAtDesc(Instant from, Instant to);

	List<AuditRecord> findByActionAndAtBetweenOrderByAtDesc(String action, Instant from, Instant to);
}
