package ascore.asdb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * Where the store contract tests find their servers. asdb's binary port is 7071 unless
 * ASDB_TEST_ABP_PORT says otherwise, so the contracts can run against a scratch asdb
 * without touching the dev database.
 */
public final class AsdbTestServer {

	public static final int ABP_PORT = Integer.parseInt(System.getenv().getOrDefault("ASDB_TEST_ABP_PORT", "7071"));

	private AsdbTestServer() { }

	public static boolean asdbReachable() {
		try (AsdbBinaryClient probe = new AsdbBinaryClient("127.0.0.1", ABP_PORT, Duration.ofSeconds(2), Duration.ofSeconds(5), 1)) {
			return probe.isHealthy();
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean mongoReachable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", 27017), 300);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

}
