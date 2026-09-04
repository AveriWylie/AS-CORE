package ascore.overrides;

import ascore.egress.EgressService;
import ascore.observability.AuditService;
import ascore.realtime.RealtimePublisher;

import org.springframework.stereotype.Service;

/**
 * O8 - the logic. Two acts kept SEPARATE (this separation is a design decision, not incidental):
 *   save(req, who): O3.validate -> version = latest+1 -> new immutable O2 -> O4.saveVersion.
 *                   Returns the version number. NOTHING is activated by saving.
 *   activate(placeId, ns, version, who): O4.setActivePointer -> reassemble merged config +
 *                   O6.put(new etag = hash of body) -> EgressService.publishConfigActivated
 *                   (Module 5) -> /topic/config -> AuditService hook (before/after = version
 *                   numbers). ROLLBACK IS activate(oldVersion) - no separate code path, so T3
 *                   proves rollback for free.
 *   getActive(placeId): from O6 cache, fall through to O4 + repopulate on miss.
 * Consumes: @Service; depends on ConfigStore (O4), ActiveConfigCache (O6), EgressService (M5),
 * RealtimePublisher, AuditService - all through their interfaces (rule 5 across modules).
 */
@Service
public class ConfigService {
	public int save(ConfigSaveRequest req, String who) {
		throw new UnsupportedOperationException("TODO(averi): O8");
	}
	public void activate(String placeId, String namespace, int version, String who) {
		throw new UnsupportedOperationException("TODO(averi): O8");
	}
}
