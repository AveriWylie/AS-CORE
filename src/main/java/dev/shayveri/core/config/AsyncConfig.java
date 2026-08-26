package dev.shayveri.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The virtual-thread executor A7 runs on. GIVEN COMPLETE (exception to the
 * scaffold rule): a @Bean method that throws would stop the whole app from
 * booting, so this one cannot ship as a stub. Read it instead - it is two
 * lines consuming two APIs:
 *
 *   {@code @Bean} (Spring DI) - "call this method once at startup, keep the result,
 *       inject it wherever a constructor asks for an Executor."
 *   Executors.newVirtualThreadPerTaskExecutor() (Java 21) - an Executor that
 *       runs EVERY submitted task on a fresh virtual thread. Virtual threads
 *       are so cheap (thousands per second is fine) that "one per telemetry
 *       POST" is a sane design - this is why the plan locked Java 21. The
 *       yml flag spring.threads.virtual.enabled=true already puts Tomcat's
 *       request handling on virtual threads; this bean gives OUR async work
 *       (A7's fire-and-forget persistence) the same treatment.
 */
@Configuration
public class AsyncConfig {

	@Bean
	public Executor telemetryExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
