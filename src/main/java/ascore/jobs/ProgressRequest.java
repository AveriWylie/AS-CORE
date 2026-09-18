package ascore.jobs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// POST /api/jobs/{id}/progress body
public record ProgressRequest(@NotNull @Min(0) @Max(100) Integer pct, String log) { }
