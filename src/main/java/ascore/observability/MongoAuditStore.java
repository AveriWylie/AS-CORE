package ascore.observability;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// V5 - the Mongo adapter behind AuditStore
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "mongo", matchIfMissing = true)
public class MongoAuditStore implements AuditStore {

	private final AuditRepository repository;

	public MongoAuditStore(AuditRepository repository) {this.repository = repository;}

	@Override
	public void record(AuditRecord record) {repository.insert(record);}

	@Override
	public List<AuditRecord> query(Instant from, Instant to, Optional<String> action) {
		return action.map(a -> repository.findByActionAndAtBetweenOrderByAtDesc(a, from, to))
				.orElseGet(() -> repository.findByAtBetweenOrderByAtDesc(from, to));
	}

}
