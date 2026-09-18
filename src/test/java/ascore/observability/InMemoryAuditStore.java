package ascore.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// AuditStore without Mongo
public class InMemoryAuditStore implements AuditStore {

	private final List<AuditRecord> records = new ArrayList<>();

	@Override
	public synchronized void record(AuditRecord record) {records.add(record);}

	@Override
	public synchronized List<AuditRecord> query(Instant from, Instant to, Optional<String> action) {
		return records.stream()
				.filter(r -> !r.getAt().isBefore(from) && !r.getAt().isAfter(to))
				.filter(r -> action.isEmpty() || r.getAction().equals(action.get()))
				.sorted(Comparator.comparing(AuditRecord::getAt).reversed())
				.toList();
	}

	public synchronized List<AuditRecord> withAction(String action) {return records.stream().filter(r -> r.getAction().equals(action)).toList();}

}
