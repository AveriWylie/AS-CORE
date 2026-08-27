package dev.shayveri.core.ingress;

import dev.shayveri.core.realtime.RealtimePublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * A7 - the module's logic: accept -> stamp receivedAt -> persist (via the
 * A5 seam) -> broadcast (via the B1 facade). Runs async so the controller
 * returns 202 in microseconds regardless of Mongo's mood.
 *
 * Consumes (not ours):
 *   {@code @Service} - Spring DI (same as @Component, the name signals "logic lives
 *       here").
 *   Executor (java.util.concurrent), one method: execute(Runnable). The
 *       bean injected is AsyncConfig's virtual-thread executor. Calling
 *       executor.execute(() -> { ... }) returns IMMEDIATELY; the lambda
 *       runs on its own virtual thread.
 *
 * Depends on (ours): TelemetryStore (A5), RealtimePublisher (B1), note
 * this class imports the INTERFACES, never MongoTelemetryStore or
 * StompRealtimePublisher. Spring injects the implementations.
 *
 * Done when: D5 passes - the controller's 202 does not wait on storage.
 */
@Service
public class TelemetryService {

	private final TelemetryStore ts;
	private final RealtimePublisher rp;
	private final Executor ex;

	/*
	 * {@code @Qualifier} is REQUIRED here, not decoration. Enabling WebSocket/STOMP
	 * publishes three Executor beans of its own (clientInboundChannelExecutor,
	 * clientOutboundChannelExecutor, brokerChannelExecutor), so by type alone
	 * there are four candidates and Spring refuses to guess. Without this the
	 * whole application fails to start with NoUniqueBeanDefinitionException,
	 * on any storage backend.
	 */
	public TelemetryService(TelemetryStore ts, RealtimePublisher rp, @Qualifier("telemetryExecutor") Executor ex) {
		this.ts = ts;
		this.rp = rp;
		this.ex = ex;
	}

	public void acceptSnapshot(TelemetrySnapshotRequest request) {

		Instant recievedAt = Instant.now();
		TelemetrySnapshot snapshot = TelemetrySnapshot.from(request, recievedAt);
		// Executor                              ← the interface: one method, execute(Runnable)
		//   ↑ implemented by
		// newVirtualThreadPerTaskExecutor()     ← the implementation your @Bean returns
		// ex.execute(work) is the call, newVirtualThreadPerTaskExecutor() is what receives it
		ex.execute(() -> {
			ts.saveSnapshot(snapshot);
			rp.publish("/topic/telemetry/" + snapshot.getPlaceId(), snapshot);
		});

	}

	/*
	 * A7 step 3. Mirrors acceptSnapshot with two deliberate differences, both
	 * from the spec above:
	 *
	 *   ONE receivedAt for the whole batch, stamped before mapping. Calling
	 *   Instant.now() per event would spread one request across several
	 *   milliseconds and make "which events arrived together" unanswerable.
	 *
	 *   NO broadcast. Snapshots feed the live dashboard; events are the
	 *   permanent research dataset and have no realtime consumer.
	 *
	 * The list is mapped BEFORE handing off to the executor, so the request
	 * objects are not read from another thread after the HTTP request has been
	 * recycled.
	 */
	public void acceptEvents(List<GameEventRequest> request) {
		Instant receivedAt = Instant.now();
		List<GameEvent> events = request.stream().map(r -> GameEvent.from(r, receivedAt)).toList();
		ex.execute(() -> ts.saveEvents(events));
	}

}
