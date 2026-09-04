package ascore.realtime;

/**
 * B1 - the one facade every module uses to broadcast. No module ever
 * touches a WebSocket API directly (plan, Module 6); they call this.
 *
 * Consumes: nothing. Given complete (contract interface, like A5).
 * Implementing it is B2.
 */
public interface RealtimePublisher {
	void publish(String topic, Object payload);
}
