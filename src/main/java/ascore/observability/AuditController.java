package ascore.observability;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/audit (DASH), so the trail can be read without a Mongo shell. Not in the
 * blueprint. from and to are ISO-8601 instants and default to the last 24 hours.
 */
@RestController
public class AuditController {

	private final AuditService as;

	public AuditController(AuditService as) {this.as = as;}

	@GetMapping("/api/audit")
	public List<AuditRecord> query(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<Instant> from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<Instant> to,
			@RequestParam Optional<String> action) {
		Instant end = to.orElseGet(Instant::now);
		return as.query(from.orElse(end.minus(1, ChronoUnit.DAYS)), end, action);
	}

}
