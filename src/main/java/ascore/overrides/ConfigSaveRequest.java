package ascore.overrides;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * O1 - PUT /api/config body. Consumes: Jakarta validation + Jackson.
 */
public record ConfigSaveRequest(
		String placeId,
		String namespace,
		Map<String, Object> values
) {
	// TODO(averi): validation annotations + compact constructor per blueprint O1.
}
