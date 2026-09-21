# A4 - GameEvent: Trusted Stored Game Event

`GameEvent` is the MongoDB document for one stored discrete event (a player
death, a round transition, a performance spike). It is not the same object as
`GameEventRequest`, even though much of their data overlaps. It is the event
counterpart of `TelemetrySnapshot` (A3): same request-to-document pattern,
with two deliberate differences called out below.

## Two sources of information

The stored event combines two different kinds of data:

| Source | Data | Trust level |
|---|---|---|
| Roblox request | `type`, `placeId`, `jobId`, `occurredAt`, `position`, `data` | Client-provided and validated |
| CORE service | `receivedAt` | Server-provided and authoritative |

Roblox sends events as a JSON **array** (batched), each element like:

```json
{
  "type": "PLAYER_DEATH",
  "placeId": "8271",
  "jobId": "server-abc",
  "occurredAt": "2026-07-08T12:00:00Z",
  "position": { "x": 12.0, "y": 3.5, "z": -40.0 }
}
```

Spring turns each element into a `GameEventRequest`. The request does not
include `receivedAt`. CORE creates that timestamp itself in `TelemetryService`:

```java
Instant receivedAt = Instant.now();
GameEvent event = GameEvent.from(request, receivedAt);
```

The factory method deliberately receives both values:

```java
public static GameEvent from(
        GameEventRequest request,
        Instant receivedAt) {
    // Copy request data and add CORE's trusted receipt time.
}
```

This creates the following boundary:

```text
Roblox JSON array
    -> GameEventRequest (validated incoming data, per element)
    -> TelemetryService adds receivedAt = Instant.now()
    -> GameEvent (stored MongoDB document)
```

## Two clocks, on purpose

Unlike a snapshot, an event carries **two** timestamps, and both are stored:

- `occurredAt` - the **client** clock: when the event actually happened in the
  game. The client knows this and sends it in the request.
- `receivedAt` - the **server** clock: when CORE actually accepted the event.

Client clocks drift and the network adds delay, so the two will not match
exactly. The gap between them is itself useful data (how laggy the server was,
how stale the report is). A snapshot only needs `receivedAt` because "current
state" is only meaningful as of when the server got it; an event is a
point-in-time fact, so *when it happened* and *when we heard about it* are two
different facts worth keeping.

`receivedAt` is stamped before the async persistence task begins, so database
queue delay or a slow Mongo write never makes an event appear to arrive later
than it really did.

## The key difference from A3: no TTL

`TelemetrySnapshot` puts `@Indexed(expireAfter = "7d")` on `receivedAt`, so raw
snapshots self-delete after seven days - they are disposable. `GameEvent` does
the opposite: it has **no TTL anywhere**. Game events are the permanent
research dataset (heatmaps, balance analysis), so they never expire. The
*absence* of the expiry annotation is the design decision.

`placeId` still gets a plain `@Indexed` (no `expireAfter`) - not for expiry,
but because heatmap and balance queries filter by place, and an index makes
those reads fast.

## Encapsulation in this document class

All fields, including the MongoDB `id`, are private with getters and no
setters. New events do not set `id`; MongoDB generates it when the object is
saved. The creation constructor assigns the remaining fields. `getData()`
returns a defensive copy (`Map.copyOf(data)`) so callers cannot mutate the
stored map. `position` is nullable by design - only `PLAYER_DEATH` events
carry it.

```java
@Document("game_events")
public class GameEvent {
    @Id
    private String id;                 // MongoDB creates this for new events.

    @Indexed
    private String placeId;            // plain index for heatmap queries, no TTL

    private Instant occurredAt;        // client clock
    private Instant receivedAt;        // server clock
}
```
