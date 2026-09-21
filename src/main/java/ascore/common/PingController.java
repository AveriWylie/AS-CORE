package ascore.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

// trivial endpoint for checking the app is alive.
@RestController
public class PingController {

	@GetMapping("/api/ping")
	public Map<String, String> ping() {return Map.of("service", "shayveri-core", "status", "ok");}

}
