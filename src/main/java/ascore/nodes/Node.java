package ascore.nodes;

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
	private String hostname;
	private Map<String, Object> capabilities;
	private Integer maxConcurrentJobs;
	private Instant registeredAt;
	private Instant lastRegisteredAt;

	public Node(String nodeId, String hostname, Map<String, Object> capabilities, Integer maxConcurrentJobs,
			Instant registeredAt, Instant lastRegisteredAt) {
		this.nodeId = nodeId;
		this.hostname = hostname;
		this.capabilities = capabilities;
		this.maxConcurrentJobs = maxConcurrentJobs;
		this.registeredAt = registeredAt;
		this.lastRegisteredAt = lastRegisteredAt;
	}

	// registeredAt is carried over on re-registration; lastRegisteredAt is always the current call
	public static Node from(NodeRegisterRequest request, Instant registeredAt, Instant lastRegisteredAt) {
		return new Node(request.nodeId(), request.hostname(), request.capabilities(),
				request.maxConcurrentJobs(), registeredAt, lastRegisteredAt);
	}

	public String getNodeId() {return nodeId;}

	public String getHostname() {return hostname;}

	public Map<String, Object> getCapabilities() {return Map.copyOf(capabilities);}

	public Integer getMaxConcurrentJobs() {return maxConcurrentJobs;}

	public Instant getRegisteredAt() {return registeredAt;}

	public Instant getLastRegisteredAt() {return lastRegisteredAt;}

}
