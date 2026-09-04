package ascore.realtime;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// R2 - GET /api/snapshot (DASH): current full state for dashboard reconnect-hydration.
// Assembled ONLY through other modules' interfaces (NodeService, JobStore, ConfigStore) - realtime owns no data.
// R3 AlertBuffer (ours, last 50 in memory) feeds the alerts section. Consumes: Spring Web; SecurityConfig -> DASH.
@RestController
public class SnapshotController {
	@GetMapping("/api/snapshot")
	public Object snapshot() {throw new UnsupportedOperationException("TODO(averi): R2");}
}
