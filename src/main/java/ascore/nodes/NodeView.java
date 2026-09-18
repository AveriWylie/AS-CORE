package ascore.nodes;

/**
 * N8's read-time answer: a node, whether it is live, and its load. Status is
 * computed from heartbeat key existence each time it is read and never stored, so
 * it cannot go stale. load is null when the node is DOWN, since its load key has
 * expired with its heartbeat.
 */
public record NodeView(Node node, Status status, Integer load) {

	public enum Status {UP, DOWN}

}
