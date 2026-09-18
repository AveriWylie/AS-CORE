package ascore.observability;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// V4 - the rule-5 seam for the audit trail
public interface AuditStore {

	void record(AuditRecord record);

	// newest first, between from and to, optionally one action only
	List<AuditRecord> query(Instant from, Instant to, Optional<String> action);
}
