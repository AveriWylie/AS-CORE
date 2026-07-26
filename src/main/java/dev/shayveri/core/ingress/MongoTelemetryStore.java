package dev.shayveri.core.ingress;

import java.util.List;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * A6 - the Mongo adapter behind the A5 seam. The only class in the module
 * that knows Mongo exists.
 *
 * Consumes (not ours):
 *   @Component  - Spring DI: "construct me at startup and inject me where a
 *                 TelemetryStore is asked for" (it is the only implementation,
 *                 so Spring picks it automatically).
 *   The two repository interfaces - whose implementations Spring GENERATED
 *                 (see their files). repo.save(x) inserts or updates;
 *                 repo.saveAll(list) batches.
 *
 * TODO(averi):
 *   1. Declare two private final fields (the two repositories) and a
 *      constructor taking both. Spring fills constructor parameters
 *      automatically - no annotation needed on the constructor.
 *   2. saveSnapshot: one line - delegate to the snapshot repository.
 *   3. saveEvents: one line - saveAll on the event repository.
 *
 * Done when: D3 passes - a saved snapshot appears in telemetry_snapshots,
 * a batch of 3 events becomes 3 documents in game_events.
 */

@Component
public class MongoTelemetryStore implements TelemetryStore {

	private final TelemetrySnapshotRepository SR;
	private final GameEventRepository ER;

	public MongoTelemetryStore(TelemetrySnapshotRepository SR, GameEventRepository ER) {

		this.SR = SR;
		this.ER = ER;

	}

	@Override
	public void saveSnapshot(TelemetrySnapshot snapshot) {SR.save(snapshot);}

	@Override
	public void saveEvents(List<GameEvent> events) {ER.saveAll(events);}

}
