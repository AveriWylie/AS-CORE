package dev.shayveri.core.nodes;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * N3 - the durable node registry document. Deliberately holds NO liveness - liveness is Redis's job
 * (N6/N7), because it is hot, expiring state and this collection is the permanent record. Keeping
 * the two apart is a design decision: the durable truth survives restarts, the liveness truth
 * self-expires.
 *
 * THE IDEMPOTENCY KEY: @Id is nodeId, the agent's OWN id, NOT a generated one. That single choice
 * is what makes re-registration idempotent - a second register with the same nodeId is a Mongo
 * upsert (overwrite), never a duplicate row. The acceptance criterion "re-registration is
 * idempotent" is satisfied by the id choice, not by logic.
 *
 * Consumes: @Document("nodes"), @Id. No TTL anywhere here.
 */
@Document("nodes")
public class Node {
	@Id
	private String nodeId;
	// TODO(averi): fields + constructor + getters + static factory per blueprint N3.
}
