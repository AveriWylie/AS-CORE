package ascore.jobs;

import java.util.Map;

// POST /api/jobs/{id}/complete body; resultMeta carries the before/after research metrics
public record CompleteRequest(String resultRef, Map<String, Object> resultMeta) {

	public CompleteRequest {resultMeta = resultMeta == null ? Map.of() : resultMeta;}

}
