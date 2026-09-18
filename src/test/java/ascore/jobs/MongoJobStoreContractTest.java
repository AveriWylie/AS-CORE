package ascore.jobs;

import ascore.asdb.AsdbTestServer;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

// the JobStore contract against mongo; skips without a server
@EnabledIf("ascore.asdb.AsdbTestServer#mongoReachable")
@SpringBootTest(properties = {"shayveri.store=mongo"})
class MongoJobStoreContractTest extends JobStoreContract { }
