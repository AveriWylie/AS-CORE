package dev.shayveri.core.ingress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The same contract, run against MongoDB.
 *
 * <p>WHY KEEP MONGO AT ALL now that asdb is the default. This class is the
 * reference. Mongo is a database whose behaviour nobody in this project has to
 * reason about, so when the two implementations disagree, this side is the one
 * that defines what the contract SHOULD have said. Without it, "asdb behaves
 * correctly" would mean "asdb behaves the way asdb behaves".
 *
 * <p>Needs Docker, and skips rather than fails without it. Testcontainers
 * starts a throwaway Mongo; nothing is installed and nothing persists.
 */
@EnabledIf("dockerAvailable")
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "shayveri.store=mongo")
class MongoStoreContractTest extends TelemetryStoreContract {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

	static boolean dockerAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		} catch (Throwable t) {
			return false;
		}
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Override
	protected String storeName() {
		return "mongo";
	}

	@Override
	protected List<Map<String, Object>> findByPlaceId(String collection, String placeId) {
		Query query = Query.query(Criteria.where("placeId").is(placeId));
		List<Map<String, Object>> out = new ArrayList<>();
		// read as raw documents rather than as the entity: the contract is
		// about what is STORED, and mapping back through the entity would hide
		// a field the store never wrote.
		for (org.bson.Document doc : mongoTemplate.find(query, org.bson.Document.class, collection)) {
			doc.remove("_id");
			doc.remove("_class");
			out.add(doc);
		}
		return out;
	}
}
