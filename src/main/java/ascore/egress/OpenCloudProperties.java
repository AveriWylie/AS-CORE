package ascore.egress;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shayveri.opencloud")
public class OpenCloudProperties {

    private String MSUrl = "http://messaging-service";

    public String getMSUrl() {return MSUrl;}

    public void setMSUrl(String MSUrl) {this.MSUrl = MSUrl;}
}