package ascore.observability;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

// the AuditStore contract against asdb; skips without a server
@EnabledIf("ascore.asdb.AsdbTestServer#asdbReachable")
@SpringBootTest(properties = {"shayveri.store=asdb", "shayveri.store.asdb.abp-port=${ASDB_TEST_ABP_PORT:7071}"})
class AsdbAuditStoreContractTest extends AuditStoreContract { }
