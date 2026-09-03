package dev.shayveri.core.observability;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * V3 - audit document. Every DASH mutation lands here; no TTL ("who changed the spawn rate" must
 * have an answer months later). who = the ApiKeyResolver principal LABEL (per-person once keys split).
 * Consumes: @Document, @Id, @Indexed(at).
 */
@Document("audit")
public class AuditRecord {
	@Id
	private String id;
	// TODO(shahyar): fields + constructor + getters per blueprint V3.
}
