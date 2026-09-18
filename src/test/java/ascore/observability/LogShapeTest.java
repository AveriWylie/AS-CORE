package ascore.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

// T4. One event through the encoder logback-spring.xml uses outside dev must be one JSON object
class LogShapeTest {

	@Test
	void encodedLineIsJsonWithTheExpectedKeys() throws Exception {
		// the live context rather than a new one, which would lack the MDC adapter the encoder reads
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger logger = context.getLogger("ascore.test");
		LogstashEncoder encoder = new LogstashEncoder();
		encoder.setContext(context);
		encoder.start();

		byte[] line = encoder.encode(new LoggingEvent(Logger.class.getName(), logger, Level.WARN, "queue depth {}", null, new Object[] {7}));
		JsonNode json = new ObjectMapper().readTree(new String(line, StandardCharsets.UTF_8));

		assertEquals("WARN", json.get("level").asText());
		assertEquals("ascore.test", json.get("logger_name").asText());
		assertEquals("queue depth 7", json.get("message").asText());
		assertTrue(json.has("@timestamp"));
	}

}
