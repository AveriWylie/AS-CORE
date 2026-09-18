package ascore.overrides;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * O1 - PUT /api/config body. Consumes: Jakarta validation + Jackson.
 */
public record ConfigSaveRequest(
		String placeId,
		@NotBlank String namespace,
		@NotNull Map<String, Object> values
) {

	// a missing or blank placeId is the global config, which every place falls back to
	public ConfigSaveRequest {
		placeId = placeId == null || placeId.isBlank() ? ConfigService.GLOBAL : placeId;
	}

}
