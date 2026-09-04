package ascore.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * B2 - the STOMP adapter behind B1.
 *
 * Consumes (not ours):
 *
 *   SimpMessagingTemplate - Spring's one entry point for STOMP broadcast.
 *       The bean exists because B3 turns on @EnableWebSocketMessageBroker.
 *       The one method that matters:
 *           convertAndSend(String destination, Object payload)
 *       "convert" = Jackson serializes payload to JSON; "send" = every
 *       client subscribed to that destination receives it.
 *
 *   {@code @Component} - Spring DI, as in A6.
 *
 * Object is Java's universal type. Every class in Java implicitly extends
 * Object, TelemetrySnapshot, GameEvent, a node-status object, a job-progress
 * object, all of them are Objects. So a parameter typed Object accepts any
 * object you hand it.
 *
 * Why the publisher uses it: RealtimePublisher is described as "the one facade
 * every module uses to broadcast." It has to carry every kind of payload, snapshots
 * from ingress, node status from Module 2, job progress from Module 3, config events
 * from Module 4. If publish took TelemetrySnapshot:
 *
 * void publish(String topic, TelemetrySnapshot payload); // could ONLY ever send
 * snapshots
 * ...
 * it'd be useless to every other module. Typing it Object means one method handles
 * all payload types:
 *
 * void publish(String topic, Object payload); // sends anything, from any module
 * So when A7 calls publisher.publish("/topic/telemetry/123", snapshot), your
 * TelemetrySnapshot slots into the Object parameter automatically (this is upcasting,
 * a TelemetrySnapshot is-a Object).
 *
 * "Payload" is standard messaging/networking vocabulary for the actual content being
 * carried
 *
 * - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
 *
 * This is the implementation A7 (TelemetryService) actually gets when it
 * depends on the RealtimePublisher interface - Spring injects this class.
 *
 * Done when: D4 passes - a subscribed test client receives what was
 * published, and only on the topic it subscribed to.
 */

@Component
public class StompRealtimePublisher implements RealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    // See spring simp messaging template
    public StompRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String topic, Object payload) {
        messagingTemplate.convertAndSend(topic, payload);
    }

}
