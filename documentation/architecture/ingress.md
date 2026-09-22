Telemetry intake: accept what Roblox sends, validate it, timestamp it, store it, broadcast it. The
highest-frequency path in the system, and the only one where the volume is set by someone else.

The classes carry the detail of what each one is; this is how they fit and why the shapes differ.

## The path a post takes

```text
JSON from a Roblox server
        │
        ▼
TelemetrySnapshotRequest      the shape of incoming data, plus its validation rules.
        │                     A record: fixed at arrival, never changed after.
        ▼
TelemetryController           the HTTP edge. Receives, delegates, replies 202 at once.
        │
        ▼
TelemetryService              stamps the arrival time, converts to the stored shape,
        │                     hands the work to the executor and returns.
        ▼
TelemetrySnapshot             the shape of stored data. A class, because Spring Data
        │                     constructs it blank and fills it when reading back.
        ▼
TelemetryStore (interface)    the storage seam. Logic only ever talks to this.
        │
        ▼
AsdbTelemetryStore            the only class that knows where telemetry actually goes
  or MongoTelemetryStore      (see asdb.md).
```

## Two streams, side by side, that never meet

A common first assumption is that a snapshot produces an event. It does not. Ingress takes two separate
streams, each with its own endpoint, request shape and stored form.

```text
Snapshot stream:   TelemetrySnapshotRequest  ->  TelemetrySnapshot
Event stream:      GameEventRequest          ->  GameEvent
```

|  | Snapshot | Event |
|---|---|---|
| answers | how is the server right now | this specific thing just happened |
| endpoint | `POST /api/telemetry` | `POST /api/telemetry/events` |
| sent | periodically, about every 10s | as a batched JSON array |
| clocks | one, the server's | two, the client's and the server's |
| collection | `telemtry_snapshots` | `game_events` |
| lifespan | disposable, 7 day TTL | permanent, no TTL |
| purpose | current state reading | the research dataset |

A snapshot is a state reading; an event is a logged fact. That is why they get separate endpoints,
separate documents, and opposite retention.

## The request is the doorway, the document is the resident

Three shapes, each with one job:

```text
Roblox JSON  ->  Request (record)  ->  Document (class)  ->  stored
                 validate at the door   plus the server's own fields
```

A request is received, validated and converted, then discarded. It is never stored, never used
downstream, never changed. A document copies the validated data, adds what the server alone decides,
and is what the rest of the system works with.

The rule behind the split: THE CLIENT SHAPES THE REQUEST, CORE ALONE SHAPES THE DOCUMENT. A client can
never set a server-authoritative field, because the type it fills in does not have one.

Why each is the Java type it is comes down to direction of travel:

    incoming (client to CORE)    built once from JSON and never changed again      -> record
    stored (CORE to storage)     the framework constructs it blank and fills it    -> class

The document stays encapsulated despite being mutable: private fields, getters, no setters, and
getData returns Map.copyOf so a caller cannot reach through the getter and change the stored map. It is
mutable to the framework and effectively read-only to application code.

One load-bearing detail: the no-argument constructor on a document class. Without it Spring Data cannot
instantiate the document when reading it back, and nothing says so until the first read at runtime.

## receivedAt is the server's, and it is stamped early

A stored snapshot mixes two kinds of data, and their trust levels are not the same.

| Source | Fields | Trust |
|---|---|---|
| Roblox request | placeId, jobId, playerCount, serverFps, round, customMetrics | client-provided, validated |
| CORE | receivedAt | server-provided, authoritative |

The game decides nothing about when CORE received its telemetry: a client clock can be absent, wrong or
deliberately misreported. `receivedAt` is stamped in TelemetryService BEFORE the write is handed to the
executor, so a slow store never makes a snapshot look like it arrived later than it did.

An event carries two clocks on purpose. `occurredAt` is the client's, when the thing happened in-game;
`receivedAt` is the server's, when CORE accepted it. Both are kept because the GAP BETWEEN THEM IS
ITSELF DATA: how laggy that server was, how stale the report is. A snapshot needs only the server clock,
since "current state" is only meaningful as of when the server got it.

## The TTL difference is a decision, including where it is absent

TelemetrySnapshot carries `@Indexed(expireAfter = "7d")` on receivedAt: raw state is disposable and
deletes itself after a week. GameEvent has no TTL anywhere, because events are the permanent dataset for
heatmaps and balance work. The ABSENCE of the annotation on GameEvent is the design decision, not an
oversight.

GameEvent still has a plain `@Indexed` on placeId. Not for expiry: heatmap and balance queries filter by
place, and the index is what makes those reads fast.

On asdb the annotation is documentation rather than enforcement, and the server's sweeper flag is what
actually expires anything. See asdb.md, which is also the sharpest edge in that adapter.

## Validation, and when it actually fires

The annotations on the request records do NOTHING at compile time. javac treats them as inert metadata
and will happily compile a @NotNull field assigned null. They fire at runtime, in this order:

    1. the controller marks the parameter @Valid, so
    2. Spring MVC's argument resolver calls validate on the object, which
    3. walks its fields by reflection, reads each annotation, and checks it

A failure becomes MethodArgumentNotValidException, which GlobalExceptionHandler turns into a 400 naming
the fields. Never a 500, and never something the module has to catch itself.

For the events endpoint, validating every element of a List needs @Valid on the list AND @Validated on
the controller class. Element-level validation is opt-in, and silently absent if either is missing.

THE BIGGER POINT. Java has no compile-time null safety: unlike Kotlin or Rust, any reference can be null
anywhere. The annotations, the compact-constructor guards and choosing Integer over int so a missing
number arrives as null and is caught, are all a runtime substitute assembled by hand. They catch nulls
after the fact rather than preventing them the way a compiler would.

Optional is deliberately not used for these fields: it has no compiler powers either, and Java
convention reserves it for return types.

## What the compact constructors do, and what they do not

Both request records carry one line:

    customMetrics = customMetrics == null ? Map.of() : customMetrics;

Those fields are optional in the incoming JSON, and an absent key is not an error: Jackson simply passes
null. So the guard collapses the two ways "nothing" can arrive, an absent key and an empty object, into
one, and every field downstream is guaranteed non-null. `data.size()` returns 0, a loop over it does
nothing, and Map.copyOf never throws.

A compact constructor is the right place because it runs on EVERY path that creates the record, Jackson
or a test, before anyone else can touch it. One chokepoint, one fix, a permanent guarantee, which holds
precisely because the record is immutable.

What it explicitly does not do: no duplicate-key checking (JSON cannot have them anyway), no validation
of contents, no structural or type checking. It is one ternary against null. `{"zombiesAlive": "banana"}`
sails straight through, by design: those maps are an open-ended bag of whatever a caller wants to
attach, which is the whole reason they are Map<String, Object>.

## Changing it

- **A new field on a snapshot:** add it to the request (with its validation) and to the document, and map
  it in `from`. Nothing else moves.
- **Payloads being rejected:** the rule is an annotation on the request record. That is the only place to
  look.
- **A different store:** implement TelemetryStore. Every file above the seam stays as it is, which is
  what the asdb swap actually did.
