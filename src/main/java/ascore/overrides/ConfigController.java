package ascore.overrides;

import ascore.common.ApiError;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O9 - the HTTP edge. ROBLOX polls active; everything else is DASH, set in
 * SecurityConfig. who is the caller's role in lower case, "dash" today.
 *
 * placeId is optional everywhere and means the global config when absent.
 */
@RestController
public class ConfigController {

	private final ConfigService cs;

	public ConfigController(ConfigService cs) {this.cs = cs;}

	/**
	 * The poll path. A matching If-None-Match is answered 304 with no body, straight from the
	 * cache, so an unchanged config costs a Redis read and a few header bytes.
	 */
	@GetMapping("/api/config/active")
	public ResponseEntity<String> active(@RequestParam(required = false) String placeId,
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		ActiveConfig config = cs.getActive(place(placeId));

		if (ifNoneMatch != null && unquote(ifNoneMatch).equals(config.etag())) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(config.etag()).build();
		}
		return ResponseEntity.ok().eTag(config.etag()).contentType(MediaType.APPLICATION_JSON).body(config.body());
	}

	@PutMapping("/api/config")
	public Map<String, Object> save(@Valid @RequestBody ConfigSaveRequest request, Authentication caller) {
		int version = cs.save(request, who(caller));
		return Map.of("placeId", request.placeId(), "namespace", request.namespace(), "version", version);
	}

	// also the rollback: activating an older version is all a rollback is
	@PostMapping("/api/config/activate/{version}")
	public Map<String, Object> activate(@PathVariable int version, @RequestParam(required = false) String placeId,
			@RequestParam String namespace, Authentication caller) {
		cs.activate(place(placeId), namespace, version, who(caller));
		return Map.of("placeId", place(placeId), "namespace", namespace, "version", version);
	}

	@GetMapping("/api/config/history")
	public List<ConfigVersion> history(@RequestParam(required = false) String placeId) {return cs.history(place(placeId));}

	@ExceptionHandler(ConfigRejectedException.class)
	ResponseEntity<ApiError> rejected(ConfigRejectedException e) {
		return ResponseEntity.badRequest().body(new ApiError(400, "Config rejected by schema", e.getProblems(), Instant.now()));
	}

	@ExceptionHandler(NoSuchElementException.class)
	ResponseEntity<ApiError> notFound(NoSuchElementException e) {return ResponseEntity.status(404).body(ApiError.of(404, e.getMessage()));}

	private static String place(String placeId) {return placeId == null || placeId.isBlank() ? ConfigService.GLOBAL : placeId;}

	private static String who(Authentication caller) {return caller.getName().toLowerCase();}

	// clients echo the header back quoted, and some mark it weak with W/
	private static String unquote(String etag) {
		String value = etag.startsWith("W/") ? etag.substring(2) : etag;
		return value.replace("\"", "");
	}

}
