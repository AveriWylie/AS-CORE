package ascore.ingress;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A3 - telemtery snap shot, wrapped as an object of post shape with added
 *      instant of when it was added handled by CORE.
 *
 * Documents are classes (not records) here:
 * Spring Data Mongo works best with a mutable class it can instantiate and
 * populate when reading back.
 *
 * Consumes (not ours) - all from Spring Data MongoDB:
 *
 *   {@code @Document}("telemetry_snapshots")
 *              - class-level: "instances of this class live in the Mongo
 *                collection named telemetry_snapshots."
 *                (import org.springframework.data.mongodb.core.mapping.Document)
 *   {@code @Id}        - marks the identifier field; leave it null on insert and
 *                Mongo generates a unique id, Spring writes it back.
 *                (import org.springframework.data.annotation.Id)
 *   {@code @Indexed}(expireAfter = "7d")
 *              - THE TTL: put this on the receivedAt field and Mongo itself
 *                deletes documents 7 days after that instant. No cleanup
 *                job, no scheduler - the database does it. This is the
 *                plan's "raw snapshots are disposable".
 *                (import org.springframework.data.mongodb.core.index.Indexed)
 *
 * IMPORTANT (blueprint A3 note):
 *
 * index creation from annotations is OFF by default in Spring Boot.
 * Add to application.yml:
 *
 *   spring.data.mongodb.auto-index-creation: true
 *
 * so a fresh database gets the TTL index automatically. Without it the
 * annotation is decoration and nothing ever expires.
 *
 *     Note:
 *     Integer is java's object version of int, int cannot be null Integer
 *     can, and Jackson then gives noll and @NotNull cleanly handles error.
 *     object that can be null, thats it
 *
 * Done when: D3 shows an accepted snapshot in the collection with
 * receivedAt set, and the TTL index exists with 7-day expiry.
 */

// for mongo db this java object becomees a document
@Document("telemtry_snapshots")
public class TelemetrySnapshot {

    @Id private String id;
    private String placeId;
    private String jobId;
    private Integer playerCount;
    private Double serverFps;
    private String round;
    private Map<String, Object> customMetrics;
    // tells MongoDB to create an index on that field and automatically
    // delete old documents.
    @Indexed(expireAfter = "7d") private Instant receivedAt;

    public TelemetrySnapshot(
            String placeId,
            String jobId,
            Integer playerCount,
            Double serverFps,
            String round,
            Map<String, Object> customMetrics,
            Instant receivedAt) {

        this.placeId = placeId;
        this.jobId = jobId;
        this.playerCount = playerCount;
        this.serverFps = serverFps;
        this.round = round;
        this.customMetrics = customMetrics;
        this.receivedAt = receivedAt;

    }

    public static TelemetrySnapshot from(TelemetrySnapshotRequest request, Instant receivedAt) {

        return new TelemetrySnapshot(
                request.placeId(),
                request.jobId(),
                request.playerCount(),
                request.serverFps(),
                request.round(),
                request.customMetrics(),
                receivedAt);

    }

    public String getId() {return id;}

    public String getPlaceId() {return placeId;}

    public String getJobId() {return jobId;}

    public Integer getPlayerCount() {return playerCount;}

    public Double getServerFps() {return serverFps;}

    public String getRound() {return round;}

    public Instant getReceivedAt() {return receivedAt;}

    public Map<String, Object> getCustomMetrics() {return Map.copyOf(customMetrics);}

}
