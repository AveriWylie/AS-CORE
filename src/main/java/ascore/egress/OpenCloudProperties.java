package ascore.egress;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * E1 - shayveri.opencloud from application.yml. The key and universe come from the
 * environment and are blank in dev, which EgressService treats as "push disabled".
 *
 * baseUrl only moves for tests. The bucket numbers sit under Open Cloud's published
 * messaging limit for a universe with few players, so a burst of activations waits
 * rather than draws 429s.
 */
@ConfigurationProperties(prefix = "shayveri.opencloud")
public class OpenCloudProperties {

	private String baseUrl = "https://apis.roblox.com";
	private String apiKey = "";
	private String universeId = "";
	private String topic = "shayveri-config";
	private int bucketCapacity = 10;
	private double refillPerSecond = 2;
	private long backoffBaseMs = 1000;

	public boolean isConfigured() {return !apiKey.isBlank() && !universeId.isBlank();}

	public String getBaseUrl() {return baseUrl;}

	public void setBaseUrl(String baseUrl) {this.baseUrl = baseUrl;}

	public String getApiKey() {return apiKey;}

	public void setApiKey(String apiKey) {this.apiKey = apiKey;}

	public String getUniverseId() {return universeId;}

	public void setUniverseId(String universeId) {this.universeId = universeId;}

	public String getTopic() {return topic;}

	public void setTopic(String topic) {this.topic = topic;}

	public int getBucketCapacity() {return bucketCapacity;}

	public void setBucketCapacity(int bucketCapacity) {this.bucketCapacity = bucketCapacity;}

	public double getRefillPerSecond() {return refillPerSecond;}

	public void setRefillPerSecond(double refillPerSecond) {this.refillPerSecond = refillPerSecond;}

	public long getBackoffBaseMs() {return backoffBaseMs;}

	public void setBackoffBaseMs(long backoffBaseMs) {this.backoffBaseMs = backoffBaseMs;}

}
