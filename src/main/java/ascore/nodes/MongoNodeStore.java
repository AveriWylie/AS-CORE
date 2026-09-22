package ascore.nodes;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The Mongo adapter behind NodeStore: it delegates to a
 * package-private repository and nothing above the seam sees Mongo.
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "mongo", matchIfMissing = true)
public class MongoNodeStore implements NodeStore {

	private final NodeRepository repository;

	public MongoNodeStore(NodeRepository repository) {this.repository = repository;}

	@Override
	public void save(Node node) {repository.save(node);}

	@Override
	public List<Node> findAll() {return repository.findAll();}

	@Override
	public Optional<Node> findById(String nodeId) {return repository.findById(nodeId);}

}
