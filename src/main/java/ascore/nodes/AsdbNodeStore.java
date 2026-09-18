package ascore.nodes;

import static ascore.asdb.AsdbDocumentStore.eq;
import ascore.asdb.AsdbBinaryClient;
import ascore.asdb.AsdbDocumentStore;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// N5 on asdb. save is an upsert on nodeId, which is what makes re-registering idempotent
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbNodeStore implements NodeStore {

	private final AsdbDocumentStore<Node> nodes;

	public AsdbNodeStore(AsdbBinaryClient client) {
		this.nodes = new AsdbDocumentStore<>(client, Node.class);
		nodes.ensureSchema();
	}

	@Override
	public void save(Node node) {nodes.upsert(node);}

	@Override
	public List<Node> findAll() {return nodes.find();}

	@Override
	public Optional<Node> findById(String nodeId) {return nodes.find("where " + eq("nodeId", nodeId)).stream().findFirst();}

}
