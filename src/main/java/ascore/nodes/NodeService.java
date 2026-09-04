package ascore.nodes;

import org.springframework.stereotype.Service;

/**
 * N8 - node logic.
 *   register(req): upsert via NodeStore (N4), stamp lastRegisteredAt; returns the SAME result for
 *       first and repeat registration - idempotency is inherited from the natural-key document
 *       (N3), this method just must not add non-idempotent side effects.
 *   heartbeat(id, req): HeartbeatStore.recordHeartbeat; UNKNOWN id -> reject (agents must register
 *       before heartbeating - a heartbeat for an unregistered node is a bug on the agent side).
 *   listWithStatus(): NodeStore.findAll() merged with HeartbeatStore liveness -> per node
 *       {node, status UP|DOWN, load}. Status is COMPUTED AT READ TIME from key existence, never
 *       stored - so it can never be stale.
 * Consumes: @Service; NodeStore (N4), HeartbeatStore (N6) - interfaces only.
 */
@Service
public class NodeService {
	// TODO(averi): constructor + register/heartbeat/listWithStatus per blueprint N8.
}
