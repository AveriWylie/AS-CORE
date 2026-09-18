package ascore.observability;

import ascore.asdb.AsdbTestServer;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

// the AuditStore contract against mongo; skips without a server
@EnabledIf("ascore.asdb.AsdbTestServer#mongoReachable")
@SpringBootTest(properties = {"shayveri.store=mongo"})
class MongoAuditStoreContractTest extends AuditStoreContract { }
