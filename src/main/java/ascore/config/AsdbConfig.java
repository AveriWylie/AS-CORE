package ascore.config;

import ascore.asdb.AsdbBinaryClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The binary client the node, job, config and audit adapters share, read from the
 * same shayveri.store.asdb properties AsdbTelemetryStore uses. One pool for all four,
 * since the server serialises statements anyway and more sockets would only queue.
 *
 * AsdbTelemetryStore keeps its own client, and with it the HTTP option.
 */
@Configuration
@ConditionalOnProperty(name = "shayveri.store", havingValue = "asdb")
public class AsdbConfig {

	@Bean(destroyMethod = "close")
	public AsdbBinaryClient asdbClient(
			@Value("${shayveri.store.asdb.abp-host:127.0.0.1}") String host,
			@Value("${shayveri.store.asdb.abp-port:7071}") int port,
			@Value("${shayveri.store.asdb.max-idle-connections:8}") int maxIdle,
			@Value("${shayveri.store.asdb.connect-timeout-ms:2000}") long connectTimeoutMs,
			@Value("${shayveri.store.asdb.request-timeout-ms:5000}") long requestTimeoutMs) {
		return new AsdbBinaryClient(host, port, Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(requestTimeoutMs), maxIdle);
	}

}
