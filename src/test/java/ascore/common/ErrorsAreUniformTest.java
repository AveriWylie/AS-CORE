package ascore.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C1 + C2 through the real filter chain and the real controllers.
 *
 * <p>WHY THIS EXISTS SEPARATELY FROM {@code Module1IntegrationTest}. That class
 * is gated on Docker, because Testcontainers starts a real MongoDB for its
 * persistence assertions. Error handling touches no database at all: a body
 * that fails validation never reaches a store. Gating these assertions behind a
 * container runtime would mean the error contract is only checked on machines
 * that happen to have Docker, which is the same as not checking it.
 *
 * <p>So the two overlap on purpose. The Docker suite proves the whole stack
 * including persistence; this proves the error shape on every machine, every
 * run.
 *
 * <p>MockMvc rather than a real socket: these assertions are about status codes
 * and JSON bodies produced by the filter chain and the controller advice, and
 * none of that needs a network.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorsAreUniformTest {

	private static final String ROBLOX_KEY = "dev-roblox-key";

	@Autowired
	private MockMvc mockMvc;

	/** Valid except that placeId is missing, so exactly one field fails. */
	private static final String MISSING_PLACE_ID = """
			{"jobId":"job-a","playerCount":42,"serverFps":58.5,"receivedAt":"2026-08-01T00:00:00Z"}""";

	@Test
	@DisplayName("a missing required field gives 400 naming that field, never a 500")
	void missingFieldGives400WithFieldError() throws Exception {
		/*
		 * The acceptance criterion from the plan, stated as a test: "malformed
		 * payloads return 400 with field errors, never 500."
		 *
		 * Before C2 this returned neither. The handler threw
		 * UnsupportedOperationException from inside the exception handler, so
		 * the failure escaped into Spring's /error forward.
		 */
		mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", ROBLOX_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(MISSING_PLACE_ID))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("validation failed"))
				.andExpect(jsonPath("$.fieldErrors.placeId").exists())
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	@DisplayName("a body that is not JSON gives a clean 400, not a parser dump")
	void garbageBodyGives400() throws Exception {
		mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", ROBLOX_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("not json{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("malformed request body"))
				// no fields to blame when the whole body is unreadable
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	@DisplayName("the parser's own message never reaches the client")
	void parserDetailIsNotLeaked() throws Exception {
		/*
		 * Jackson's message quotes the offending input and names internal
		 * classes and line numbers. Returning it hands an unauthenticated
		 * caller a probe into the server, so the message is fixed text and the
		 * detail goes to the log instead.
		 */
		String body = mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", ROBLOX_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"placeId\": [[[ }"))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		org.junit.jupiter.api.Assertions.assertFalse(body.contains("com.fasterxml"), body);
		org.junit.jupiter.api.Assertions.assertFalse(body.toLowerCase().contains("jackson"), body);
	}

	@Test
	@DisplayName("an unknown route is still a 404, not a 500")
	void unknownRouteIsNotA500() throws Exception {
		/*
		 * THE TRAP THIS PROJECT WOULD OTHERWISE HAVE SHIPPED.
		 *
		 * Since Spring Boot 3.2 an unmatched request raises
		 * NoResourceFoundException, and GlobalExceptionHandler is a plain
		 * @RestControllerAdvice rather than a ResponseEntityExceptionHandler
		 * subclass. Its @ExceptionHandler(Exception.class) therefore catches
		 * that too, and without a more specific handler every 404 in the
		 * application comes back as 500.
		 *
		 * A monitored service that reports server errors for ordinary missing
		 * paths is one that gets paged for nothing.
		 */
		mockMvc.perform(get("/no/such/route").header("X-Api-Key", ROBLOX_KEY))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("an invalid element inside a batch is a 400, not a 500")
	void invalidBatchElementIsA400() throws Exception {
		/*
		 * The events endpoint takes List<@Valid GameEventRequest>, which is a
		 * container element constraint. Those are checked by METHOD validation,
		 * enabled by @Validated on the controller, and method validation throws
		 * ConstraintViolationException rather than MethodArgumentNotValidException.
		 *
		 * Different exception, different handler. If nothing handles it the
		 * catch-all turns a caller's bad input into a 500, which is the exact
		 * failure C2 exists to prevent, just on a path the obvious test misses.
		 */
		mockMvc.perform(post("/api/telemetry/events")
						.header("X-Api-Key", ROBLOX_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("[{\"jobId\":\"job-a\"}]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("validation failed"))
				.andExpect(jsonPath("$.fieldErrors.placeId").exists());
	}

	@Test
	@DisplayName("fieldErrors is always present, so no client has to null-check it")
	void fieldErrorsIsNeverAbsent() throws Exception {
		// the record's compact constructor guarantees this; asserted through
		// the wire because that is where consumers actually see it.
		mockMvc.perform(post("/api/telemetry")
						.header("X-Api-Key", ROBLOX_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("not json{"))
				.andExpect(jsonPath("$.fieldErrors").exists());
	}
}
