package ascore.jobs;

import ascore.common.ApiError;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP edge. Thin delegation; the NODE and DASH rules live in
 * SecurityConfig.
 *
 * The two handlers here are scoped to this controller: an unknown job is a 404 and
 * a job in the wrong state for the call is a 409, both in the ApiError shape.
 */
@RestController
public class JobController {

	private final JobService js;

	public JobController(JobService js) {this.js = js;}

	@PostMapping("/api/jobs")
	public ResponseEntity<Map<String, String>> create(@Valid @RequestBody JobCreateRequest request, Authentication caller) {
		return ResponseEntity.accepted().body(Map.of("id", js.create(request, caller.getName().toLowerCase()).getId()));
	}

	// 200 with the job, or 204 once the claim window passes with nothing claimable
	@PostMapping("/api/jobs/claim")
	public ResponseEntity<Job> claim(@Valid @RequestBody ClaimRequest request) {
		return js.claim(request).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/api/jobs/{id}/progress")
	public Job progress(@PathVariable String id, @Valid @RequestBody ProgressRequest request) {return js.progress(id, request);}

	@PostMapping("/api/jobs/{id}/complete")
	public Job complete(@PathVariable String id, @Valid @RequestBody CompleteRequest request) {return js.complete(id, request);}

	@PostMapping("/api/jobs/{id}/fail")
	public Job fail(@PathVariable String id, @Valid @RequestBody FailRequest request) {return js.fail(id, request);}

	@GetMapping("/api/jobs")
	public List<Job> list(@RequestParam Optional<JobStatus> status, @RequestParam Optional<String> mapId) {return js.list(status, mapId);}

	@ExceptionHandler(NoSuchElementException.class)
	ResponseEntity<ApiError> notFound(NoSuchElementException e) {return ResponseEntity.status(404).body(ApiError.of(404, e.getMessage()));}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<ApiError> conflict(IllegalStateException e) {return ResponseEntity.status(409).body(ApiError.of(409, e.getMessage()));}

}
