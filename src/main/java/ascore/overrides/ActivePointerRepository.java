package ascore.overrides;

import org.springframework.data.mongodb.repository.MongoRepository;

// O5 - package-private, keyed by ActivePointer.idFor
interface ActivePointerRepository extends MongoRepository<ActivePointer, String> { }
