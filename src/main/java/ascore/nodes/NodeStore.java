package ascore.nodes;

import java.util.List;
import java.util.Optional;

/**
 * The storage seam for the durable registry. Services depend on this, never on
 * MongoNodeStore or its repository.
 */
public interface NodeStore {

	void save(Node node);

	List<Node> findAll();

	Optional<Node> findById(String nodeId);
}
