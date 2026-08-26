package dev.shayveri.core.ingress;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A6 - the Mongo adapter behind the A5 seam. The only class in the module
 * that knows Mongo exists.
 *
 * Consumes (not ours):
 *   {@code @Component}  - Spring DI: "construct me at startup and inject me where a
 *                 TelemetryStore is asked for" (it is the only implementation,
 *                 so Spring picks it automatically).
 *   The two repository interfaces - whose implementations Spring GENERATED
 *                 (see their files). repo.save(x) inserts or updates;
 *                 repo.saveAll(list) batches.
 *
 * Done when: D3 passes - a saved snapshot appears in telemetry_snapshots,
 * a batch of 3 events becomes 3 documents in game_events.
 */

/*
 * matchIfMissing = true keeps Mongo the DEFAULT. Both this and
 * AsdbTelemetryStore implement TelemetryStore, so exactly one must be active or
 * Spring fails to start with an ambiguous-bean error. Setting
 * shayveri.store=asdb switches; anything else, including the property being
 * absent entirely, leaves this one in place. That ordering matters: a
 * deployment that has never heard of asdb must keep working untouched.
 */
@Component
@ConditionalOnProperty(name = "shayveri.store", havingValue = "mongo", matchIfMissing = true)
public class MongoTelemetryStore implements TelemetryStore {

	private final TelemetrySnapshotRepository sr;
	private final GameEventRepository er;

	// code readability does not apply to variable names.
	public MongoTelemetryStore(TelemetrySnapshotRepository sr, GameEventRepository er) {
		this.sr = sr;
		this.er = er;
	}

	@Override
	public void saveSnapshot(TelemetrySnapshot snapshot) {sr.save(snapshot);}

	@Override
	public void saveEvents(List<GameEvent> events) {er.saveAll(events);}

}
