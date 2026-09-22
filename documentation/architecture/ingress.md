Telemetry intake: accept what Roblox sends, validate it, timestamp it, store it, broadcast it. The
highest-frequency path in the system, and the only one whose volume is set by someone else.

The classes carry what each one is. This is how they fit, what each term means, and why the shapes differ.

## The four data classes, and how they relate

Ingress has four data classes, forming two parallel pairs:

```text
TelemetrySnapshotRequest   ->   TelemetrySnapshot
GameEventRequest           ->   GameEvent
```

The pairs are SIBLINGS, not a chain. A snapshot request does not produce a game event. Each request flows
to its own document and the two lanes never cross. They live in the same package only because both are
"telemetry coming in from Roblox".

| Term | What it is |
|---|---|
| **TelemetrySnapshotRequest** | the incoming shape of one periodic state report. A record, validated at the door. |
| **TelemetrySnapshot** | the stored shape of that report, with the server's own receivedAt added. A document class. |
| **GameEventRequest** | the incoming shape of one discrete event. A record. Arrives as a JSON array of them. |
| **GameEvent** | the stored shape of that event, with receivedAt added. A document class. |
| **TelemetryStore** | the storage seam. The only thing the logic talks to. |
| **TelemetryService** | the logic: stamp, convert, hand off, broadcast. |
| **TelemetryController** | the HTTP edge. Two endpoints, both 202, no logic of its own. |

## The path a post takes

```text
JSON from a Roblox server
        │
        ▼
TelemetrySnapshotRequest      the SHAPE of incoming data, plus its validation rules.
        │                     A record: fixed at arrival, never changed after.
        ▼
TelemetryController           the HTTP edge. Receives, delegates, replies 202 at once.
        │
        ▼
TelemetryService              stamps the arrival time, converts the request into a
        │                     storable document, hands the write to the executor.
        ▼
TelemetrySnapshot             the SHAPE of stored data. A class, because Spring Data
        │                     constructs it blank and fills it when reading back.
        ▼
TelemetryStore (interface)    the persistence BOUNDARY. Logic only ever talks to this.
        │
        ▼
AsdbTelemetryStore            the only class that knows where telemetry actually goes
  or MongoTelemetryStore      (see asdb.md).
```

## Snapshots are not events: two independent streams

A common early confusion: does a TelemetrySnapshotRequest produce a GameEvent? No. They are unrelated
peers, each with its own endpoint, request shape and stored form.

|  | Snapshot | Event |
|---|---|---|
| answers | how is the server right now | this specific thing just happened |
| endpoint | `POST /api/telemetry` | `POST /api/telemetry/events` |
| incoming | one object | a JSON ARRAY, batched |
| sent | periodically, about every 10s | when something happens |
| clocks | one: receivedAt, the server's | two: occurredAt (client) and receivedAt (server) |
| collection | `telemtry_snapshots` | `game_events` |
| lifespan | disposable, 7 day TTL | permanent, no TTL |
| purpose | current state reading | the research dataset |

A snapshot is a STATE READING; an event is a LOGGED FACT. That is why they get separate endpoints,
separate documents, and opposite retention.

## Request and document: the doorway and the resident

Telemetry flows through three shapes on its way in:

```text
Roblox JSON  ->  Request (record)  ->  Document (class)  ->  stored
                 validate at the door   plus the server's own fields
```

**The REQUEST is the doorway.** An immutable record mirroring the incoming JSON exactly. Its entire life
is three steps: Jackson binds the JSON into it; its annotations check the payload at the boundary, so bad
data is rejected with a 400 before any logic touches it; and `from(request, receivedAt)` copies it into
the document, after which the request has done its job and is garbage. It is never stored, never used
downstream, never changes.

**The DOCUMENT is the resident.** A class that copies the validated request data and adds CORE's own
trusted fields (receivedAt, the storage id). This is what gets persisted and what the rest of the system
works with.

The governing rule: THE CLIENT SHAPES THE REQUEST; CORE ALONE SHAPES THE DOCUMENT. A client can never set
a server-authoritative field, because the type it fills in does not have one.

### Why a record for the request and a class for the document

The choice is not stylistic; each type is forced by what has to happen to the object at that stage. It
falls out of DIRECTION OF TRAVEL:

```text
incoming (client -> CORE)   Jackson builds it once from JSON, and it must
                            never change again                            -> RECORD
stored   (CORE <-> storage) the framework must construct it blank and
                            populate it, both on save and on read         -> CLASS
```

The document class still keeps encapsulation despite being mutable: all fields private, getters only, NO
setters, and `getCustomMetrics` / `getData` return `Map.copyOf(...)` so a caller cannot mutate the stored
map through the getter. It is mutable to the framework, effectively read-only to application code.

WATCH OUT: the no-argument constructor is the load-bearing technical requirement here. A document class
with only its all-args creation constructor will fail to instantiate when READ BACK, and that failure does
not surface at compile time, only on the first find call.

### Why save lives on the repository, not on the request

Saving writes to storage, and only a document class is a stored shape. A request record has none of the
mapping the storage layer needs. The chain is:

```text
request -> TelemetrySnapshot.from(...) -> document -> repository.save(document)
```

Giving the request a save would collapse the doorway and the resident into one thing.

## TelemetrySnapshot: trusted stored telemetry

A stored snapshot combines two kinds of data, and their trust levels differ:

| Source | Data | Trust level |
|---|---|---|
| Roblox request | placeId, jobId, playerCount, serverFps, round, customMetrics | client-provided, validated |
| CORE service | receivedAt | server-provided, authoritative |

Roblox sends:

```json
{
  "placeId": "8271",
  "jobId": "server-abc",
  "playerCount": 12,
  "serverFps": 58.5,
  "round": "round-4"
}
```

The request has no receivedAt. CORE creates it:

```java
Instant receivedAt = Instant.now();
TelemetrySnapshot snapshot = TelemetrySnapshot.from(request, receivedAt);
```

The factory takes both values deliberately, which is what makes the boundary visible in the code:

```text
Roblox JSON
    -> TelemetrySnapshotRequest (validated incoming data)
    -> TelemetryService adds receivedAt = Instant.now()
    -> TelemetrySnapshot (the stored document)
```

WHY receivedAt IS SEPARATE. The game client should not decide when CORE received its telemetry: a
client-side timestamp can be missing, wrongly clocked, or deliberately misreported. receivedAt records
when CORE actually accepted the snapshot, and it is stamped BEFORE the async write begins, so queue delay
or a slow store never makes a snapshot appear to have arrived later than it did.

## GameEvent: trusted stored game event

The event counterpart, with the same request-to-document pattern and two deliberate differences.

| Source | Data | Trust level |
|---|---|---|
| Roblox request | type, placeId, jobId, occurredAt, position, data | client-provided, validated |
| CORE service | receivedAt | server-provided, authoritative |

Events arrive batched, as a JSON array whose elements look like:

```json
{
  "type": "PLAYER_DEATH",
  "placeId": "8271",
  "jobId": "server-abc",
  "occurredAt": "2026-07-08T12:00:00Z",
  "position": { "x": 12.0, "y": 3.5, "z": -40.0 }
}
```

**Difference one: two clocks, on purpose.** `occurredAt` is the CLIENT clock, when the event actually
happened in the game, which only the client knows. `receivedAt` is the SERVER clock, when CORE accepted
it. Client clocks drift and the network adds delay, so the two will not match, and THE GAP BETWEEN THEM IS
ITSELF DIAGNOSTIC DATA: how laggy that server was, how stale the report is. A snapshot needs only the
server clock, because "current state" is only meaningful as of when the server got it. An event is a
point-in-time fact, so when it happened and when we heard about it are two genuinely different facts.

**Difference two: no TTL.** See below.

`position` is nullable by design, since only some event types carry one, and `getData` returns a defensive
copy for the reason given above.

## The TTL difference, including where the annotation is absent

TelemetrySnapshot puts `@Indexed(expireAfter = "7d")` on receivedAt, so raw snapshots self-delete after
seven days: state is disposable. GameEvent has NO TTL anywhere, because events are the permanent research
dataset for heatmaps and balance analysis. THE ABSENCE OF THE ANNOTATION IS THE DESIGN DECISION.

GameEvent still carries a plain `@Indexed` on placeId. Not for expiry: heatmap and balance queries filter
by place, and the index is what makes those reads fast.

On asdb the annotation is documentation rather than enforcement, and the server's sweeper flag is what
expires anything. That gap is the sharpest edge in the adapter; see asdb.md.

## Validation: what fires, and when

The annotations on the request records (`@NotBlank`, `@NotNull`, `@Min`, `@Positive`) do NOTHING at
compile time. javac treats them as inert metadata and will happily compile a `@NotNull` field assigned
null. They act at runtime, in this order:

```text
1. the controller marks the parameter @Valid, which
2. makes Spring MVC's argument resolver call validate on the object, which
3. walks its fields by reflection, reads each annotation, and checks it
```

A failure becomes MethodArgumentNotValidException, which GlobalExceptionHandler turns into a 400 with
per-field errors. Never a 500.

NOTE FOR THE EVENTS ENDPOINT: validating every element of a `List<GameEventRequest>` needs `@Valid` on the
list AND `@Validated` on the controller class. Element-level validation is opt-in, and silently absent if
either is missing.

THE BIGGER POINT. Java has no compile-time null safety. Unlike Kotlin (`String?` vs `String`) or Rust
(`Option<T>`), javac lets any reference be null anywhere. The annotations, the compact-constructor guards,
and choosing Integer over int so a missing number arrives as null and is caught, are a RUNTIME SUBSTITUTE
assembled from application-level guards. They catch nulls after the fact rather than preventing them the
way a compiler would.

Optional is deliberately not used for these fields: it is a plain library class with no compiler powers
(`Optional<String> x = null;` compiles fine), and Java convention reserves it for return types.

## What the compact constructors normalize, and what they do not

Both request records carry the same one-line guard:

```java
customMetrics = customMetrics == null ? Map.of() : customMetrics;   // snapshot request
data          = data == null ? Map.of() : data;                     // event request
```

WHY THE FIELD CAN BE NULL AT ALL: customMetrics and data are OPTIONAL in the incoming JSON. A Luau script
may send a snapshot with no customMetrics key, or a ROUND_START event with no data key. When Jackson binds
JSON and a key is simply ABSENT, it passes null. The null is not malformed input; it is the normal shape
of "the client chose not to send this optional field".

WHAT IT NORMALIZES, exactly: there are two ways "no extra data" can arrive, an absent key (null) and a
present-but-empty object (an empty Map). The guard folds the first into the second, so only ONE
representation of "nothing" exists past that line. That is the entire normalization.

WHY IN THE COMPACT CONSTRUCTOR: it runs on EVERY path that creates the record, Jackson or a hand-written
test, before anyone else can touch it. One chokepoint, one fix, a permanent downstream guarantee, which
holds precisely BECAUSE the record is immutable. Anywhere else, every future caller needs its own null
check.

WHY AN EMPTY MAP RATHER THAN NULL: `data.size()` returns 0, a loop over it does nothing, and
`Map.copyOf(data)` never throws. The work is done ONCE, AT THE DOOR, so it is never redone downstream.
That is what makes the document a guaranteed-complete object: every field is either validated-present or
defaulted-non-null.

WHAT IT EXPLICITLY DOES NOT DO:

- NO duplicate-key checking. JSON objects cannot have duplicate keys anyway; the parser settles that
  before Jackson builds anything.
- NO validation of contents. The map is `Map<String, Object>`, completely untyped. `{"zombiesAlive":
  "banana"}` sails through untouched.
- NO structural or type checking of any kind. It is one ternary against null.

Those two maps are deliberately unvalidated inside: they are an open-ended bag of whatever extra a caller
wants to attach, which is the whole reason they exist as `Map<String, Object>`.

## Changing it

- **A new field on a snapshot:** add it to TelemetrySnapshotRequest (with any validation) and to
  TelemetrySnapshot, and map it in `from`. Nothing else moves.
- **Payloads being rejected:** the rule is an annotation on the request record. That is the only place to
  look.
- **A different store:** implement TelemetryStore and leave every file above the seam untouched. That is
  exactly what the asdb swap did.

Each kind of change has one place it belongs, which is the whole payoff of the structure.
