package dev.shayveri.core.ingress;

import dev.shayveri.core.realtime.RealtimePublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A7 - the module's logic: accept -> stamp receivedAt -> persist (via the
 * A5 seam) -> broadcast (via the B1 facade). Runs async so the controller
 * returns 202 in microseconds regardless of Mongo's mood.
 *
 * Consumes (not ours):
 *   @Service - Spring DI (same as @Component, the name signals "logic lives
 *       here").
 *   Executor (java.util.concurrent) - one method: execute(Runnable). The
 *       bean injected is AsyncConfig's virtual-thread executor. Calling
 *       executor.execute(() -> { ... }) returns IMMEDIATELY; the lambda
 *       runs on its own virtual thread.
 *
 * Depends on (ours): TelemetryStore (A5), RealtimePublisher (B1) - note
 * this class imports the INTERFACES, never MongoTelemetryStore or
 * StompRealtimePublisher. Spring injects the implementations.
 *
 * TODO(averi):
 *   1. Three private final fields (store, publisher, executor) + constructor.
 *   2. accept(request):
 *        a. Instant receivedAt = Instant.now();   <- stamp BEFORE going async,
 *           so queue delay never skews the timestamp
 *        b. TelemetrySnapshot snapshot = TelemetrySnapshot.from(request, receivedAt);
 *        c. executor.execute(() -> {
 *               store.saveSnapshot(snapshot);
 *               publisher.publish("/topic/telemetry/" + snapshot's placeId, snapshot);
 *           });
 *      Topic name comes from the plan: /topic/telemetry/{placeId}.
 *   3. acceptEvents(requests): same shape - stamp once, map the list through
 *      GameEvent.from(...), then async store.saveEvents(list). (No broadcast
 *      for events in Phase 1 - snapshots only, per the plan's "emits".)
 *
 * Done when: D5 passes - the controller's 202 does not wait on storage.
 */

@Service
public class TelemetryService {

	private final TelemetryStore TS;
	private final RealtimePublisher RP;
	private final Executor EX;

	public TelemetryService(TelemetryStore TS, RealtimePublisher RP, Executor EX) {

		this.TS = TS;
		this .RP = RP;
		this.EX = EX;

	}

	public void accept(TelemetrySnapshotRequest request) {

		Instant recievedAt = Instant.now();
		TelemetrySnapshot snapshot = TelemetrySnapshot.from(request, recievedAt);

		EX.execute(() -> {
			TS.saveSnapshot(snapshot);
			RP.publish("/topic/telemetry/" + snapshot.getPlaceId(), snapshot);
		});

	}

	public void acceptEvents(List<GameEventRequest> requests) {
		throw new UnsupportedOperationException("TODO(averi): implement per A7 step 3");
	}

}
