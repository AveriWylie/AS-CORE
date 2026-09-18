package ascore.observability;

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
	@Indexed
	private final Instant at;
	private final String who;
	private final String action;
	private final String target;
	private final Map<String, Object> before;
	private final Map<String, Object> after;

	public AuditRecord(Instant at, String who, String action, String target, Map<String, Object> before, Map<String, Object> after) {
		this.at = at;
		this.who = who;
		this.action = action;
		this.target = target;
		this.before = before == null ? Map.of() : Map.copyOf(before);
		this.after = after == null ? Map.of() : Map.copyOf(after);
	}

	public String getId() {return id;}

	public Instant getAt() {return at;}

	public String getWho() {return who;}

	public String getAction() {return action;}

	public String getTarget() {return target;}

	public Map<String, Object> getBefore() {return before;}

	public Map<String, Object> getAfter() {return after;}

}
