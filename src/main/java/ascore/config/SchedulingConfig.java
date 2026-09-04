package ascore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled processing for the whole app. GIVEN COMPLETE (like AsyncConfig): without
 * {@code @EnableScheduling} somewhere, every @Scheduled method (NodeStatusSweep N9, OrphanRequeueSweep J9)
 * is silently inert - the annotation compiles and does nothing. One class, once, enables all of them.
 * Consumes: @EnableScheduling (Spring).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig { }
