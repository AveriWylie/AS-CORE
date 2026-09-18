package ascore.nodes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// NodeStore without Mongo, so NodeService is testable on any machine
public class InMemoryNodeStore implements NodeStore {

	private final Map<String, Node> nodes = new LinkedHashMap<>();

	@Override
	public void save(Node node) {nodes.put(node.getNodeId(), node);}

	@Override
	public List<Node> findAll() {return List.copyOf(nodes.values());}

	@Override
	public Optional<Node> findById(String nodeId) {return Optional.ofNullable(nodes.get(nodeId));}

}
