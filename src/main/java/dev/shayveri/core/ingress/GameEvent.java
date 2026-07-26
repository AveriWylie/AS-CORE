package dev.shayveri.core.ingress;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A4 - GameEvent (A4) is GameEventRequest (A2)'s data plus the server-added
 * fields, same relationship A3 has with A1
 *
 * The telemetry pattern:
 * Telemetry flows through three shapes, each with one job:
 *
 * Roblox JSON  →  Request (record)  →  Document (class)  →  stored / iterable
 *                 validate at door      + server fields
 *
 * Request (TelemetrySnapshotRequest, GameEventRequest) - the doorway.
 * An immutable record that mirrors the incoming JSON. Its only job is to
 * be validated, then converted and discarded.
 *
 * Document (TelemetrySnapshot, GameEvent) - the resident. A mutable class
 * that copies the validated request data and adds CORE's own trusted fields
 * (receivedAt, the Mongo id). This is what gets stored and worked with.
 *
 * The client shapes the request; CORE alone shapes the document. The
 * client never gets to set server-authoritative fields.
 *
 * Why an event is different telemetry:
 * Both are "telemetry in," but they answer different questions:
 *
 *   Snapshot: "how is the server right now" (periodic, ~10s); one clock
 *             (receivedAt, server); one object in; disposable, 7-day TTL.
 *   Event:    "this specific thing just happened" (a death, a round change);
 *             two clocks (occurredAt client + receivedAt server), the gap is
 *             diagnostic; a JSON array in (batched); permanent, no TTL - it
 *             is the research dataset.
 *
 * A snapshot is a state reading; an event is a logged fact. That is why they
 * get separate endpoints, separate documents, and opposite retention.
 *
 * How we validate the bare shape, then make it reliably iterable:
 * Validate at the boundary. The request record carries validation
 * annotations (@NotBlank, @NotNull) directly on its fields. The controller
 * marks the parameter @Valid, so Spring checks the raw shape before any
 * logic runs. Anything malformed becomes a 400 with field errors; it never
 * reaches the service.
 *
 * Normalize away nulls. The record's compact constructor swaps missing
 * collections for empty ones (data = data == null ? Map.of() : data;). After
 * this, optional maps are never null - always at least an empty map.
 *
 * Convert to a guaranteed-complete object. A from(request, receivedAt)
 * factory copies the validated fields and stamps the server fields into the
 * document. Because every field is either validated-present or defaulted-
 * non-null, downstream code can iterate or call .size() with zero null checks.
 *
 * Consumes: @Document("game_events") and @Id, exactly as in A3. No
 * @Indexed(expireAfter) - the absence IS the design decision (events are
 * permanent). A plain @Indexed on placeId: heatmap queries will filter by it.
 *
 * Done when: D3 shows a batch of 3 posted events -> 3 documents here, and
 * the collection has no TTL index.
 */

// for mongo db this java object becomes a document
@Document("game_events")
public class GameEvent {

    // --- fields ---
    @Id private String id;
    private String type;
    // plain index (NOT a TTL): heatmap queries filter by placeId
    @Indexed private String placeId;
    private String jobId;
    private Instant occurredAt;
    private GameEventRequest.Position position;
    private Map<String, Object> data;
    // NOTE:
    // no @Indexed(expireAfter) here - game events never expire (see above)
    private Instant receivedAt;

    // --- Constructor (everything except id; Mongo assigns id) ---
    public GameEvent(
            String type,
            String placeId,
            String jobId,
            Instant occurredAt,
            GameEventRequest.Position position,
            Map<String, Object> data,
            Instant receivedAt) {

        this.type = type;
        this.placeId = placeId;
        this.jobId = jobId;
        this.occurredAt = occurredAt;
        this.position = position;
        this.data = data;
        this.receivedAt = receivedAt;
    }

    // --- Static object creation factory (calls constructor above) ---
    public static GameEvent from(GameEventRequest request, Instant receivedAt) {

        return new GameEvent(
                request.type(),
                request.placeId(),
                request.jobId(),
                request.occurredAt(),
                request.position(),
                request.data(),
                receivedAt);
    }

    // --- getters (no setters per security model) ---
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getJobId() {
        return jobId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public GameEventRequest.Position getPosition() {
        return position;
    }

    public Map<String, Object> getData() {
        return Map.copyOf(data);
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
