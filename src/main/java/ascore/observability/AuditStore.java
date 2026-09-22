package ascore.observability;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// the storage seam for the audit trail
public interface AuditStore {

	void record(AuditRecord record);

	// newest first, between from and to, optionally one action only
	List<AuditRecord> query(Instant from, Instant to, Optional<String> action);
}
