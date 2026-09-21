The asdb folder is the translator between Spring and your Rust database. Four classes, each one does exactly
one job, and they stack:

TelemetryService  →  TelemetryStore (interface)
                          ↓
             ┌── AsdbTelemetryStore ────────── the plug
             │        uses ↓
             ├── AsdbEntityMapper ──────────── Java object → ASL text
             │        then ↓
             └── Client ───────────────────  ASL text → HTTP → asdb

                 AsdbHealthIndicator ────────── reports reachability strictly

ONE WRITE, END TO END

Who calls whom when a job is saved. The document store is the one driving; the mapper and the client are
helpers it calls, and neither knows the other exists.

AsdbJobStore.save(job)                      the adapter, module-side
  └─ AsdbDocumentStore.insert(job)          generic store
       ├─ document(job)
       │    ├─ AsdbEntityMapper.toMap(job)  reflection: fields into a LinkedHashMap
       │    └─ plain(...)                   enums to names, recursively through maps and lists
       └─ client.insert(collection, doc)    AsdbBinaryClient
            └─ AbpCodec.insertPayload       tag and length-prefix each value
                 └─ frame on TCP 7071

Reading is the same chain in reverse, with one step telemetry never needed: client.query returns maps, and
AsdbDocumentStore.entity turns each back into the entity, millis to Instant and names to enums. That is why
AsdbEntityMapper has toMap and no fromMap.

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

WHERE ASDB DOES NOT MATCH MONGO

TTL is configured on the server, not by the annotation. @Indexed(expireAfter = "7d") on
TelemetrySnapshot.receivedAt is read by Spring Data and would be read by Mongo. asdb has no TTL index; its
server runs a sweeper configured with a command-line flag:

    asdb telemetry.db --ttl telemtry_snapshots.receivedAt=7d

So the annotation stays true as documentation but stops being the thing that enforces it. If the flag is
missing, the collection grows forever and nothing fails. That is the sharpest edge in this whole adapter.

No generated ids. Mongo fills a null @Id with an ObjectId. asdb does not, and the mapper omits the field
instead. Nothing in the ingress path reads ids back, so this is currently invisible, but a read path would
have to deal with it.

Writes are not transactional. saveEvents sends one batched statement, so it is one request, but asdb has no
transactions: a failure partway through leaves the earlier documents written. Mongo's saveAll is not atomic
across documents either, so this is a match in practice rather than a regression.

One writer at a time. The asdb server serializes every statement behind a mutex, so concurrent telemetry
posts queue rather than run in parallel.

THE DOCUMENT STORE

Telemetry only ever inserts. Nodes, jobs, config and audit also read back, filter, sort and overwrite, so
they share one generic piece in the asdb folder instead of each growing its own mapping.

AsdbDocumentStore<T> is one entity type's collection. It creates the collection and its indexes at startup
(the id, every @Indexed field, plus any the adapter names), inserts over the binary protocol, overwrites
with an ASL update, and reads documents back into the entity. Values in a where clause go through eq, in,
atLeast and atMost, which escape them with the same AsdbEntityMapper.literal the text path uses.

Reading back is the half telemetry never needed. Fields are set by type: epoch millis back to Instant,
names back to enums, and whole numbers inside maps narrowed to Integer where they fit, since that is what
Jackson and Mongo hand back for the same values. Entities are created without calling a constructor, as
Spring Data does, then filled field by field.

The four adapters sit in their own modules and are thin:

    AsdbNodeStore    nodes            upsert on nodeId, find by id, find all
    AsdbJobStore     jobs             insert when new, overwrite by id, filter by status, mapId, claimedBy
    AsdbConfigStore  config_versions  insert, find by place + namespace + version, latest by order + limit
                     config_active    upsert on "placeId:namespace", list all
    AsdbAuditStore   audit            insert, range on at, optional action, newest first

They share one AsdbBinaryClient bean from config/AsdbConfig, built from the same shayveri.store.asdb
properties. AsdbTelemetryStore keeps its own client and its HTTP option.

Each has a Mongo twin, and shayveri.store picks which pair is live, exactly as for telemetry. With asdb
selected nothing connects to Mongo, so Mongo does not need to be running. Redis is still needed either way;
see below.

Each store has a contract test (NodeStoreContract, JobStoreContract, ConfigStoreContract,
AuditStoreContract) run once against asdb and once against Mongo. The asdb runs use port 7071 unless
ASDB_TEST_ABP_PORT says otherwise, so they can point at a scratch server.

WHAT THE ADAPTERS WORK AROUND, WHICH IS ASDB'S BACKLOG

Each of these is handled on the Java side today and would be better as a feature in asdb.

No upsert. Saving a node or moving a config pointer is an update, then an insert if nothing matched. The
method is synchronized, so one AS-CORE process cannot insert the same id twice, but two processes could.
A native upsert would make it one statement.

unique is parsed, not enforced. Mongo's unique index on (placeId, namespace, version) is what stops two
config saves landing on the same version. AsdbConfigStore checks and inserts under a lock instead, which
again only holds within one process.

No generated ids. Telemetry leaves a null id out. These stores read ids back, so a null String id is filled
with a UUID before insert.

No binary update. Inserts travel as bytes, but an overwrite is ASL text, so every value in it passes
through the escaper. An OP_UPDATE would remove that.

WHY REDIS STAYS

Redis is not only storage here. Node liveness is a key that expires 45 seconds after the last heartbeat,
and a job claim is one atomic move from a queue list to a node's in-flight list. asdb's TTL is a sweeper
over a timestamp field, set per collection from the command line (--ttl nodes.at=45s would parse), so a
heartbeat would only expire when the sweeper next ran, not at 45 seconds. It also has no atomic list move.
Replacing Redis would mean building both into asdb rather than writing another adapter.
