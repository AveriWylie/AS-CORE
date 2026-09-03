The asdb folder is the translator between Spring and your Rust database. Four classes, 666 lines, plus 392 lines of
tests. Nothing else in the codebase imports it, it plugs in behind TelemetryStore and is invisible above that line.

Each one does exactly one job, and they stack:

TelemetryService  →  TelemetryStore (interface)
                          ↓
             ┌── AsdbTelemetryStore ────────── the plug
             │        uses ↓
             ├── AsdbEntityMapper ──────────── Java object → ASL text
             │        then ↓
             └── AsdbClient ───────────────────  ASL text → HTTP → asdb

                 AsdbHealthIndicator ────────── reports reachability

AsdbTelemetryStore (163) is the plug. The only class that implements TelemetryStore, so it's the only one
Spring can inject. Two real methods:

saveSnapshot(TelemetrySnapshot)  → mapper → client
saveEvents(List<GameEvent>)      → mapper → client   (empty list = no-op)

Its constructor also runs ensureSchema() once at startup, creating collections and @Indexed indexes, because
Mongo does both implicitly and asdb does neither. That method checks health first, so an unreachable server logs
a loud ERROR instead of six benign-looking "skipped" lines.

AsdbEntityMapper (346), this is the the heart, and the largest for a reason. Converts a Java object into ASL text
by reflection:

collectionOf(Class)      reads @Document      → "telemtry_snapshots"
indexedFieldsOf(Class)   reads @Indexed       → ["placeId"]
insertStatement(entity)  → from telemtry_snapshots | insert { ... }
insertStatement(List)    → from game_events | insert [ {...}, {...} ]

It's final with a private constructor, pure functions, no state, so its tests need no server and no Spring.

Most of its size is the careful parts: quote() is the injection boundary (a placeId of x" } | delete // must
store as data, not execute), and backtick() handles customMetrics keys that collide with ASL keywords like order.
Both are package-private so every caller has to go through insertStatement.

AsdbClient (114), java.net.http, so no new dependency and no driver. execute(statement) POSTs to
/query and throws AsdbException on non-2xx. It deliberately doesn't parse the response, the store only needs to know
the write succeeded, and adding a JSON parser to read a field nobody consumes would be work with no caller.

AsdbHealthIndicator (43), Puts asdb into /actuator/health. Exists because Spring contributes a health
check for every datastore it auto-configures, so Mongo and Redis appeared while the store actually serving traffic did
not.

Why it's shaped this way: each layer only knows the one below it. The mapper doesn't know HTTP exists; the client
doesn't know what an entity is; the store doesn't know ASL syntax. That's why the mapper is testable with 13 pure string
tests, and why fixing the batch-insert bracket bug touched one method.

Note all four carry @ConditionalOnProperty(havingValue = "asdb"), including the health indicator, so a health check for
a switched-off backend doesn't linger.

THE BINARY PATH (added after the above, and the folder is now six classes)

asdb speaks two protocols from one process against one database: ASL text over
HTTP on 7070, and ABP/1, a binary protocol, on 7071. This folder can use either.
shayveri.store.asdb.protocol picks one and defaults to binary.

TelemetryService  →  TelemetryStore (interface)
                          ↓
             ┌── AsdbTelemetryStore ────────── the plug, unchanged above this line
             │        picks ↓
             │   Transport (private interface, two implementations)
             │        ↓                            ↓
             │   HttpTransport                BinaryTransport
             ├── AsdbEntityMapper              AsdbEntityMapper.toMap
             │   Java object → ASL text        Java object → field map
             └── AsdbClient                    AbpCodec + AsdbBinaryClient
                 ASL text → HTTP               field map → binary frames

AbpCodec (356) is the encoder. It is a MIRROR of src/wire.rs in asdb, and the
two are held together by a byte-exact fixture asserted on both sides, since
nothing catches a drift at compile time.

AsdbBinaryClient (232) holds a pool of persistent connections. A pool rather
than one socket because asdb replies in arrival order per connection, so two
threads sharing one would read each other's answers. It locks with
ReentrantLock rather than synchronized because this application runs on virtual
threads and synchronized would pin their carriers.

AsdbEntityMapper gained toMap(Object), which is documentLiteral with the text
rendering removed, so both paths walk the same fields.

WHY. Measured from Java against the same server, per document:

    HTTP + ASL text            236.78 us
    ABP binary, single          28.57 us     8.29x
    ABP binary, batch of 100     3.27 us    72.45x

saveEvents sends batches, so it takes the last row. Values also stop being
syntax on the binary path: they travel as length-prefixed bytes and are never
lexed, so the escaping AsdbEntityMapper.quote has to get right every time has
nothing to get wrong.

The full derivation, including what was measured and rejected, is in
PROTOCOL.txt in the asdb repo.
