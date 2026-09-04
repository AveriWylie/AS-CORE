package ascore.realtime;


public interface RealtimePublisher {
	void publish(String topic, Object payload);
}
