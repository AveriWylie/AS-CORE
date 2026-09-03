package dev.shayveri.core.asdb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to an asdb server over HTTP.
 *
 * <p>WHY THERE IS NO DRIVER HERE. asdb speaks HTTP with an ASL statement as the
 * request body and JSON as the response, which is the whole reason that wire
 * format was chosen: the client is {@code java.net.http.HttpClient}, already in
 * the JDK, and there is no dependency to add and no protocol to maintain. That
 * is the trade against a binary protocol, which would be faster but would need
 * a driver written per language.
 *
 * <p>WHAT THIS CLASS DELIBERATELY DOES NOT DO. It does not parse the response.
 * The store layer above only needs to know whether the write succeeded, and
 * asdb reports failures as a non-2xx status with a JSON body, so a status check
 * plus the raw body is enough. Adding a JSON parser (and therefore Jackson
 * databind wiring, or a hand-rolled one) to read a field nobody consumes would
 * be work with no caller. When a read path lands that genuinely needs the
 * documents back, parse it then.
 *
 * <p>Not annotated as a Spring component on purpose: it is constructed by
 * {@link AsdbTelemetryStore}, which is the only thing that needs it, so its
 * lifetime is tied to the bean that uses it rather than floating in the context.
 */
public class AsdbClient {

	private final HttpClient http;
	private final URI queryEndpoint;
	private final Duration requestTimeout;

	public AsdbClient(String baseUrl, Duration connectTimeout, Duration requestTimeout) {
		this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		this.queryEndpoint = URI.create(trimTrailingSlash(baseUrl) + "/query");
		this.requestTimeout = requestTimeout;
	}

	/**
	 * Runs one ASL statement and returns the raw JSON response body.
	 *
	 * <p>Throws {@link AsdbException} on a transport failure or a non-2xx
	 * status. Failing loudly matters more here than it might elsewhere: a
	 * telemetry write that silently vanishes leaves no trace anywhere, so the
	 * caller has to be able to see it and decide.
	 *
	 * @param statement an ASL statement. Any user-supplied values inside it MUST
	 *                  already have been escaped by {@link AsdbEntityMapper};
	 *                  see the injection note there.
	 */
	public String execute(String statement) {

		HttpRequest request = HttpRequest.newBuilder()
				.uri(queryEndpoint)
				.timeout(requestTimeout)
				.header("Content-Type", "text/plain; charset=utf-8")
				.POST(HttpRequest.BodyPublishers.ofString(statement))
				.build();

		HttpResponse<String> response;

		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (java.io.IOException e) {
			throw new AsdbException("asdb request failed: " + queryEndpoint, e);
		} catch (InterruptedException e) {
			// Restore the flag rather than swallowing it, so a shutdown that
			// interrupts this thread still propagates.
			Thread.currentThread().interrupt();
			throw new AsdbException("asdb request interrupted", e);
		}

		int status = response.statusCode();

		if (status < 200 || status >= 300) {
			throw new AsdbException("asdb returned " + status + ": " + response.body() + "  (statement: " + statement + ")");
		}

		return response.body();
	}

	/** True when the server answers its health endpoint. Never throws. */
	public boolean isHealthy() {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(queryEndpoint.toString().replaceAll("/query$", "/health")))
					.timeout(requestTimeout)
					.GET()
					.build();
			return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	private static String trimTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/** Unchecked so it matches the Spring Data repository style the rest of the ingress code uses. */
	public static class AsdbException extends RuntimeException {

		public AsdbException(String message) {super(message);}

		public AsdbException(String message, Throwable cause) {super(message, cause);}
	}
}
