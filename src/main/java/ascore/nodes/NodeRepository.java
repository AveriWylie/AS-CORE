package ascore.nodes;

import org.springframework.data.mongodb.repository.MongoRepository;

// N5 - package-private so only MongoNodeStore can reach it; Spring Data generates the implementation
interface NodeRepository extends MongoRepository<Node, String> { }
