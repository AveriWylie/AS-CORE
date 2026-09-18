package ascore.overrides;

import java.util.Map;

// a save the schema refused; ConfigController turns it into a 400 naming each bad key
public class ConfigRejectedException extends RuntimeException {

	private final Map<String, String> problems;

	public ConfigRejectedException(Map<String, String> problems) {
		super("config rejected: " + problems.keySet());
		this.problems = Map.copyOf(problems);
	}

	public Map<String, String> getProblems() {return problems;}

}
