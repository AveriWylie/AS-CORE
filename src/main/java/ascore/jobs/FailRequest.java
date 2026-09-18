package ascore.jobs;

import jakarta.validation.constraints.NotBlank;

// POST /api/jobs/{id}/fail body
public record FailRequest(@NotBlank String error) { }
