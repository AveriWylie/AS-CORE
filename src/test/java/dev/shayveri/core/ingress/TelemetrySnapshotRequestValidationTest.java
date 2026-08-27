package dev.shayveri.core.ingress;

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
	@Disabled("TODO(averi): enable after A1 annotations are in")
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

	@Disabled("TODO(averi): implement - negative playerCount must produce a violation on 'playerCount'")
	@Test
	void negativePlayerCountIsRejected() {
		// TODO(averi): build a request that is valid EXCEPT playerCount = -1,
		// validate it, assert one violation on "playerCount".
	}

	@Disabled("TODO(averi): implement - blank jobId (\"   \") must be rejected; this is why @NotBlank not @NotNull")
	@Test
	void blankJobIdIsRejected() {
		// TODO(averi)
	}

	@Disabled("TODO(averi): implement - a fully valid request must produce ZERO violations")
	@Test
	void validRequestHasNoViolations() {
		// TODO(averi): assertTrue(violations.isEmpty()) - and if this fails,
		// an annotation is stricter than the contract says.
	}

	@Disabled("TODO(averi): implement after the A1 compact constructor - null customMetrics must become an empty map, not null")
	@Test
	void nullCustomMetricsDefaultsToEmptyMap() {
		// TODO(averi): construct with customMetrics = null, then
		// assertEquals(Map.of(), request.customMetrics());
	}

}
