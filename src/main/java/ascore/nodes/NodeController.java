package ascore.nodes;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * N10 - the HTTP edge. Thin delegation and no identity checks: the NODE and DASH
 * rules live in SecurityConfig.
 *
 * An unknown node on heartbeat is a 404 thrown as ResponseStatusException, so it
 * reaches the client in the same ApiError shape as every other failure.
 */
@RestController
public class NodeController {

	private final NodeService ns;

	public NodeController(NodeService ns) {this.ns = ns;}

	@PostMapping("/api/nodes/register")
	public ResponseEntity<Node> register(@Valid @RequestBody NodeRegisterRequest request) {
		return ResponseEntity.ok(ns.register(request));
	}

	@PostMapping("/api/nodes/{id}/heartbeat")
	public ResponseEntity<Void> heartbeat(@PathVariable String id, @Valid @RequestBody HeartbeatRequest request) {
		if (!ns.heartbeat(id, request)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown node " + id);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/api/nodes")
	public List<NodeView> list() {return ns.listWithStatus();}

}
