package ascore.overrides;

import org.springframework.data.mongodb.repository.MongoRepository;

// package-private, keyed by ActivePointer.idFor
interface ActivePointerRepository extends MongoRepository<ActivePointer, String> { }
