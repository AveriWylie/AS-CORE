package ascore.observability;

import static ascore.asdb.AsdbDocumentStore.atLeast;
import static ascore.asdb.AsdbDocumentStore.atMost;
import static ascore.asdb.AsdbDocumentStore.eq;
import ascore.asdb.AsdbBinaryClient;
import ascore.asdb.AsdbDocumentStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// AuditStore on asdb. at is stored as epoch millis, so the range is a plain integer comparison on its index
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbAuditStore implements AuditStore {

	private final AsdbDocumentStore<AuditRecord> audit;

	public AsdbAuditStore(AsdbBinaryClient client) {
		this.audit = new AsdbDocumentStore<>(client, AuditRecord.class);
		audit.ensureSchema("action");
	}

	@Override
	public void record(AuditRecord record) {audit.insert(record);}

	@Override
	public List<AuditRecord> query(Instant from, Instant to, Optional<String> action) {
		String range = "where " + atLeast("at", from) + " and " + atMost("at", to);
		String filter = action.map(a -> range + " and " + eq("action", a)).orElse(range);
		return audit.find(filter + " order at desc");
	}

}
