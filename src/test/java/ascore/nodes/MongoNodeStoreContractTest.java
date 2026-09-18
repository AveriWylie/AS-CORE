package ascore.nodes;

import ascore.asdb.AsdbTestServer;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

// the NodeStore contract against mongo; skips without a server
@EnabledIf("ascore.asdb.AsdbTestServer#mongoReachable")
@SpringBootTest(properties = {"shayveri.store=mongo"})
class MongoNodeStoreContractTest extends NodeStoreContract { }
