# A3 - TelemetrySnapshot: Trusted Stored Telemetry

`TelemetrySnapshot` is the MongoDB document for one stored telemetry update.
It is not the same object as `TelemetrySnapshotRequest`, even though much of
their data overlaps.

## Two sources of information

The stored snapshot combines two different kinds of data:

| Source | Data | Trust level |
|---|---|---|
| Roblox request | `placeId`, `jobId`, `playerCount`, `serverFps`, `round`, `customMetrics` | Client-provided and validated |
| CORE service | `receivedAt` | Server-provided and authoritative |

Roblox sends a request such as:

```json
{
  "placeId": "8271",
  "jobId": "server-abc",
  "playerCount": 12,
  "serverFps": 58.5,
  "round": "round-4"
}
```

Spring turns that JSON into a `TelemetrySnapshotRequest`. The request does
not include `receivedAt`. CORE creates that timestamp itself in
`TelemetryService`:

```java
Instant receivedAt = Instant.now();
TelemetrySnapshot snapshot = TelemetrySnapshot.from(request, receivedAt);
```

The factory method deliberately receives both values:

```java
public static TelemetrySnapshot from(
        TelemetrySnapshotRequest request,
        Instant receivedAt) {
    // Copy request data and add CORE's trusted receipt time.
}
```

This creates the following boundary:

```text
Roblox JSON
    -> TelemetrySnapshotRequest (validated incoming data)
    -> TelemetryService adds receivedAt = Instant.now()
    -> TelemetrySnapshot (stored MongoDB document)
```

## Why `receivedAt` is separate

The game client should not decide when CORE received its telemetry. A
client-side timestamp can be missing, incorrectly clocked, or deliberately
misreported. `receivedAt` records the time CORE actually accepted the
snapshot.

It is stamped before the async persistence task begins. That preserves the
correct meaning of the field: database queue delay or a slow Mongo write does
not make the snapshot appear to have arrived later than it really did.

## Snapshots are not events (two independent streams)

A common early confusion: *does a `TelemetrySnapshotRequest` produce a
`GameEvent`?* No. They are unrelated peers. `ingress` handles **two separate
incoming streams**, each with its own request shape, its own endpoint, and its
own stored form. Neither derives from the other; they never cross.

| Stream | Endpoint | Incoming (record) | Stored (document) | Meaning |
|---|---|---|---|---|
| Snapshots | `POST /api/telemetry` | `TelemetrySnapshotRequest` (A1) | `TelemetrySnapshot` (A3) | The server's overall state *right now* (player count, FPS, round), sent periodically (~every 10s). |
| Events | `POST /api/telemetry/events` | `GameEventRequest` (A2) | `GameEvent` (A4) | One specific thing that *just happened* (a death, a round change, a perf spike), sent as a batched array. |

```text
Snapshot stream:   TelemetrySnapshotRequest (A1)  ->  TelemetrySnapshot (A3)
Event stream:      GameEventRequest (A2)          ->  GameEvent (A4)
                   (the two lanes never meet)
```

The two `...Request` records only *look* related because both validate
incoming JSON, both end in `Request`, and both live in `ingress`. They are two
lanes of the same highway, not one feeding the other. A snapshot answers
"what is the state?"; an event answers "what happened?" - different data,
different endpoint, different document.

(The stored forms differ in one telling way, too: snapshots carry a 7-day TTL
because raw state is disposable, while `GameEvent` has no TTL - events are the
permanent research dataset. See below on `@Indexed`.)

## Encapsulation in this document class

All fields, including the MongoDB `id`, are private. New snapshots do not set
`id`; MongoDB generates it when the object is saved. The creation constructor
assigns the remaining fields, and the no-argument constructor exists so
Spring Data MongoDB can reconstruct an existing stored document.

```java
@Document("telemetry_snapshots")
public class TelemetrySnapshot {
    @Id
    private String id; // MongoDB creates this for new snapshots.

    private String placeId;
    private Instant receivedAt;

    public TelemetrySnapshot() {
    }
}
```

`@Indexed(expireAfter = "7d")` belongs on `receivedAt`. It creates MongoDB's
seven-day TTL cleanup rule: raw snapshots expire automatically, while
long-lived game events do not.
