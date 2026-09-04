package ascore.ingress;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D1 (first slice) - proves the A1 validation annotations work, as pure
 * unit tests: no Spring, no web server, no database. The web-layer version
 * of D1 (real HTTP 400s through C2) comes later, once the controller (A8)
 * exists.
 *
 * Consumes (not ours) - the Jakarta Validation API used directly:
 *   Validation.buildDefaultValidatorFactory().getValidator()
 *       - bootstraps a Validator, the engine that reads the annotations.
 *         This is the same engine Spring runs when a controller parameter
 *         is marked @Valid; here we invoke it by hand.
 *   validator.validate(object)
 *       - runs every annotation on the object, returns a
 *         Set<ConstraintViolation<T>>: empty set = valid.
 *   violation.getPropertyPath().toString()  - which field failed ("placeId")
 *   violation.getMessage()                  - the complaint ("must not be blank")
 * And JUnit 5 (org.junit.jupiter): @Test, @BeforeAll, assertions.
 *
 * The @Disabled tests are yours: remove the @Disabled line as soon as the
 * A1 TODOs are done, and make them green. The first test is a fully worked
 * example of the pattern.
 */
class TelemetrySnapshotRequestValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		validator = Validation.buildDefaultValidatorFactory().getValidator();
	}

	/**
	 * Worked example - read this one, then write the rest in its image.
	 * Enable after adding the A1 annotations.
	 */
	@Test
	void missingPlaceIdIsRejectedAndNamed() {

		var request = new TelemetrySnapshotRequest(
				null,          // placeId - the broken field under test
				"job-123",
				12,
				58.5,
				"round-4",
				Map.of());

		Set<ConstraintViolation<TelemetrySnapshotRequest>> violations = validator.validate(request);

		// exactly one thing is wrong, and it names the field:
		assertEquals(1, violations.size());
		ConstraintViolation<TelemetrySnapshotRequest> v = violations.iterator().next();
		assertEquals("placeId", v.getPropertyPath().toString());
	}

	@Test
	void negativePlayerCountIsRejected() {

		var request = new TelemetrySnapshotRequest(
				"8271",
				"job-123",
				-1,            // playerCount - the broken field under test
				58.5,
				"round-4",
				Map.of());

		Set<ConstraintViolation<TelemetrySnapshotRequest>> violations = validator.validate(request);

		assertEquals(1, violations.size());
		assertEquals("playerCount", violations.iterator().next().getPropertyPath().toString());
	}

	@Test
	void blankJobIdIsRejected() {

		// whitespace only: @NotNull would pass this, @NotBlank does not
		var request = new TelemetrySnapshotRequest(
				"8271",
				"   ",         // jobId - the broken field under test
				12,
				58.5,
				"round-4",
				Map.of());

		Set<ConstraintViolation<TelemetrySnapshotRequest>> violations = validator.validate(request);

		assertEquals(1, violations.size());
		assertEquals("jobId", violations.iterator().next().getPropertyPath().toString());
	}

	@Test
	void validRequestHasNoViolations() {

		var request = new TelemetrySnapshotRequest(
				"8271", "job-123", 12, 58.5, "round-4", Map.of("zombies", 30));

		Set<ConstraintViolation<TelemetrySnapshotRequest>> violations = validator.validate(request);

		// if this fails, an annotation is stricter than the contract says
		assertTrue(violations.isEmpty(), violations.toString());
	}

	@Test
	void nullCustomMetricsDefaultsToEmptyMap() {

		var request = new TelemetrySnapshotRequest(
				"8271", "job-123", 12, 58.5, "round-4", null);

		assertEquals(Map.of(), request.customMetrics());
	}

}
