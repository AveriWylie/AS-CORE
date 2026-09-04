REQUESTS AND EVENTS IN THE INGRESS MODULE (local notes)

You can find a lot of this scattered throughout the ingress module but for
one contained and organized note ...
=========================================================
Everything about the two incoming telemetry shapes: how requests relate to
documents, why each is the Java type it is, how snapshots differ from
events, and exactly what the null guard in the compact constructors does
and does not do.


1. THE FOUR CLASSES AND HOW THEY RELATE
------------------------------------------
The ingress module has four data classes, forming two parallel pairs:

  TelemetrySnapshotRequest (A1)  ->  TelemetrySnapshot (A3)
  GameEventRequest         (A2)  ->  GameEvent         (A4)

The pairs are SIBLINGS, not a chain. A1 does not produce A2. A1 flows to
A3; A2 flows to A4. They never cross. Both pairs live in the same package
only because both are "telemetry coming in from Roblox."


2. THE TELEMETRY PATTERN (three shapes, each with one job)
-------------------------------------------------------------
Telemetry flows through three shapes on its way in:

  Roblox JSON  ->  Request (record)  ->  Document (class)  ->  stored
                   validate at door      + server fields

REQUEST (TelemetrySnapshotRequest, GameEventRequest) - the DOORWAY.
An immutable record mirroring the incoming JSON exactly. Its entire life:
  1. Receive  - Jackson binds the incoming JSON into it automatically.
  2. Validate - its annotations (@NotBlank, @NotNull) check the payload at
                the boundary. Bad data is rejected with a 400 here, before
                any logic touches it.
  3. Convert, then discard - once valid, TelemetrySnapshot.from(request,
                receivedAt) copies it into the storable document. After
                that the request object has done its job and is garbage.

It is never stored, never used downstream, never changes.

DOCUMENT (TelemetrySnapshot, GameEvent) - the RESIDENT.
A mutable class that copies the validated request data and adds CORE's own
trusted fields (receivedAt, the Mongo id). This is what gets persisted and
what the rest of the system actually works with.

The governing rule: THE CLIENT SHAPES THE REQUEST; CORE ALONE SHAPES THE
DOCUMENT. The client never gets to set server-authoritative fields.

Useful mental model: the request is the DOORWAY (check ID, let it in), the
document is the RESIDENT (actually lives in the system). The request's
whole existence is that moment at the door.


3. WHY A RECORD FOR THE REQUEST AND A CLASS FOR THE DOCUMENT
---------------------------------------------------------------
The choice is not stylistic - each type is forced by what has to happen to
the object at each stage.

THE REQUEST IS A RECORD BECAUSE IT IS READ-ONCE, THEN THROWN AWAY.
A record is Java's immutable data carrier: declare the components in the
header and the compiler generates the constructor, the accessors
(placeId(), not getPlaceId()), equals(), hashCode(), and toString(). The
fields are final - once constructed, the object can never change.

That immutability is exactly right for a request, for three reasons:

  1. IT IS A CONTRACT, NOT STATE. The request mirrors the incoming JSON
     exactly, one component per key. Making it immutable means what
     arrived at the door is what gets validated and what gets copied -
     nothing between those steps can quietly alter it. If it had setters,
     any code holding a reference could mutate the payload AFTER
     validation had already passed, and the validation would be
     meaningless.
  2. VALIDATION AND NORMALIZATION HAPPEN ONCE, AT CONSTRUCTION. The
     compact constructor (the null guard, section 5) runs before the
     components are assigned, on every construction path. Because the
     object can never change afterwards, that one run is a PERMANENT
     guarantee. In a mutable class the guarantee would only hold until the
     next setter call.
  3. THERE IS NOTHING TO MUTATE ANYWAY. The request's whole life is
     receive, validate, convert, discard. It never gets stored, never
     accumulates state, never gets read back. A mutable class would be
     giving it capabilities it has no use for.

THE DOCUMENT IS A CLASS BECAUSE THE FRAMEWORK HAS TO POPULATE IT.
Spring Data Mongo does not just write documents, it READS them back:
finding an existing document means constructing a blank object and filling
its fields in via reflection. That process needs a mutable class with a
no-argument constructor - it cannot hand values to a record's canonical
constructor the way Jackson does on the way in. The document also carries a
field the client never supplies (the Mongo-generated @Id), which is written
INTO the object after it is created, on save.

So the split falls out of DIRECTION OF TRAVEL:

  incoming (client -> CORE)   Jackson builds it once from JSON, and it must
                              never change again        -> RECORD
  stored   (CORE <-> Mongo)   the framework must construct it blank and
                              populate it, both on save and on read
                                                        -> CLASS

Note the document class still keeps encapsulation despite being mutable:
all fields private, getters only, NO setters (per the security model), and
getData() returns Map.copyOf(data) so a caller cannot mutate the stored map
through the getter. It is mutable to the framework, effectively read-only
to application code.

WATCH OUT: the no-argument constructor is the load-bearing technical
requirement here. If a document class only has its all-args creation
constructor, Spring Data will fail to instantiate it when READING back from
Mongo - and that failure does not surface at compile time, only on the
first findById/findAll call.


4. WHY AN EVENT IS DIFFERENT TELEMETRY FROM A SNAPSHOT
---------------------------------------------------------
Both are "telemetry in," but they answer different questions:

                  SNAPSHOT                    EVENT
  answers      "how is the server right    "this specific thing just
               now" (periodic, ~10s)       happened" (a death, a round
                                           change, a perf spike)
  endpoint     POST /api/telemetry         POST /api/telemetry/events
  shape in     one object                  a JSON ARRAY (batched)
  clocks       one (receivedAt, server)    two (occurredAt client +
                                           receivedAt server)
  collection   telemetry_snapshots         game_events
  lifespan     disposable, 7-day TTL       permanent, no TTL
  purpose      current state reading       the research dataset

A snapshot is a STATE READING; an event is a LOGGED FACT. That is why they
get separate endpoints, separate documents, and opposite retention.

THE TWO CLOCKS ON AN EVENT (the interesting part):
  occurredAt - the CLIENT clock: when the event actually happened in-game.
               The client knows this and sends it in the request.
  receivedAt - the SERVER clock: when CORE actually accepted it.

Both are stored on purpose. Client clocks drift and the network adds delay,
so the two will not match exactly, and THE GAP BETWEEN THEM IS ITSELF
DIAGNOSTIC DATA (how laggy that server was, how stale the report is).

A snapshot does not bother with a client timestamp because "current state"
is only meaningful as of when the server got it. An event is a
point-in-time fact, so "when it happened" and "when we heard about it" are
two genuinely different useful facts.

THE TTL DIFFERENCE:
TelemetrySnapshot puts @Indexed(expireAfter = "7d") on receivedAt, so raw
snapshots self-delete after seven days, they are disposable. GameEvent has
NO TTL anywhere. Game events are the permanent research dataset (heatmaps,
balance analysis), so they never expire. THE ABSENCE OF THE ANNOTATION IS
THE DESIGN DECISION.

GameEvent still gets a plain @Indexed on placeId - not for expiry, but
because heatmap and balance queries filter by place, and an index makes
those reads fast.

WHY save() LIVES ON THE REPOSITORY, NOT THE REQUEST:
save() writes to Mongo, and only @Document-annotated classes are Mongo
documents. TelemetrySnapshotRequest is a plain record with no @Document, no
@Id - nothing Mongo understands. The chain is:

  request -> TelemetrySnapshot.from(...) -> document
          -> SR.save(document)   [SR = TelemetrySnapshotRepository]

Giving the request a save() would violate the doorway/resident split.


5. THE NULL GUARD IN THE COMPACT CONSTRUCTORS
------------------------------------------------
Both request records carry the same one-line guard:

  customMetrics = customMetrics == null ? Map.of() : customMetrics;   // A1
  data          = data == null ? Map.of() : data;                     // A2

WHAT Map.of() IS:
A static factory method that builds a small IMMUTABLE map in one line.
Map.of() with no arguments makes an empty one. Immutable means calling
.put() or .remove() on it throws UnsupportedOperationException at runtime -
the same fail-loud exception used for unimplemented stubs, for the same
reason: guaranteeing nobody accidentally mutates a fixed safe default.
(Practical limit: Map.of(...) supports at most 10 key-value pairs written
inline; past that you would need Map.ofEntries(...). Never relevant here.)

WHY THE FIELD CAN BE NULL AT ALL:
customMetrics and data are OPTIONAL fields in the incoming JSON. The Luau
script might send a snapshot with no customMetrics key, or a ROUND_START
event with no data key (maybe only PLAYER_DEATH populates it). When Jackson
binds JSON to a record and a key is simply ABSENT, it does not error - it
passes null for that constructor parameter. So the null is not a bug or
malformed input; it is the normal shape of "the client chose not to send
this optional field."

WHY FIX IT IN THE COMPACT CONSTRUCTOR SPECIFICALLY:
A compact constructor is record syntax - no parameter list, runs before the
components are assigned, and it runs on EVERY path that creates the record:
Jackson deserialization, a manual new TelemetrySnapshotRequest(...) in a
test, anywhere. It is the one chokepoint every instance passes through
before anyone else can touch it. Fix it once there and you get a permanent
downstream guarantee: this field is never null. Fix it anywhere else and
every future piece of code touching customMetrics or data needs its own
if (x != null) check, repeated across the codebase. (This is also point 2
of section 3 in action - the guarantee is permanent precisely BECAUSE the
record is immutable.)

WHY DEFAULT TO AN EMPTY MAP RATHER THAN LEAVING NULL:
With the guard in place, data.size() safely returns 0, a for loop over it
does nothing, and Map.copyOf(data) in GameEvent.getData() never throws.
Without it, every one of those call sites needs defensive null-checking.
The whole point is doing that work ONCE, AT THE DOOR, so it never has to
be redone anywhere downstream. This is what makes the resulting document a
"guaranteed-complete object": every field is either validated-present or
defaulted-non-null, so downstream code can iterate or call .size() with
zero null checks.


6. WHAT "NORMALIZE" ACTUALLY MEANS HERE
------------------------------------------------------
The word "normalize" gets used for this guard, but it does exactly ONE
narrow thing: collapsing two possible representations of "nothing" into one.

There are two ways Jackson could hand the constructor "no extra data":
  1. The key is ABSENT entirely  ({"placeId": "8271"})
     -> Jackson passes Java null.
  2. The key is PRESENT but empty ({"placeId": "8271", "customMetrics": {}})
     -> Jackson passes an already-empty Map.

Without the guard those are two DIFFERENT objects reaching the field - one
null, one a real empty Map, and downstream code would have to treat "is
this null?" and "is this empty?" as two separate cases. The guard folds
case 1 into case 2 so only ONE representation of "no data" ever exists past
that line. That is the entire normalization.

WHAT IT EXPLICITLY DOES NOT DO:
  - NO duplicate-key checking. JSON objects cannot have duplicate keys
    within themselves anyway; that is resolved at the parser level before
    Jackson ever builds the object.
  - NO validation of contents. The map is Map<String, Object> - completely
    untyped values. The guard never looks inside. A map with garbage values
    ({"zombiesAlive": "banana"}) sails through untouched.
  - NO structural or type checking of any kind. It is a single ternary
    comparing one thing to null. Nothing else.

Content validation, where it exists at all, happens at the annotation layer
(@NotBlank / @NotNull) on the fields that need it. customMetrics and data
were deliberately left unvalidated inside, they are meant to be an
open-ended bag of whatever extra info a caller wants to attach, which is
the whole reason they exist as Map<String, Object>.


7. HOW VALIDATION ACTUALLY FIRES (and when)
----------------------------------------------
The request records carry annotations (@NotBlank, @NotNull, @Min,
@Positive) directly on their fields. Important: THESE DO NOTHING AT COMPILE
TIME. javac treats them as inert metadata and will happily compile
@NotNull String x = null.

They only do something at RUNTIME, when:
  1. The controller marks the parameter @Valid, which
  2. causes Spring MVC's argument resolver to call
     validator.validate(theRequestObject), which
  3. uses reflection to walk the object's fields, read each annotation, and
     check it.

Failures become MethodArgumentNotValidException, which the global exception
handler converts into a 400 with per-field errors - never a 500.

NOTE for the events endpoint: to validate every element of a
List<GameEventRequest>, the parameter needs @Valid on the list AND the
controller class needs @Validated. Element-level validation is opt-in.

THE BIGGER POINT: Java has NO compile-time null safety. Unlike Kotlin
(String? vs String) or Rust (Option<T>), javac lets any reference type be
null anywhere. Everything in this module - the annotations, the compact
constructor guards, choosing Integer over int so a missing number can be
null and get caught - is a RUNTIME SUBSTITUTE assembled from
application-level guards. It CATCHES nulls after the fact rather than
PREVENTING them from being written the way a compiler would.

Note also that Optional<T> is NOT used for these nullable fields, and that
is deliberate: Optional is a plain library class with no special compiler
powers (Optional<String> x = null; compiles fine), and Java convention
reserves it for RETURN types, not fields or parameters.


8. THE THROUGH-LINE
----------------------
Validate the bare shape once at the door, normalize the gaps, then hand off
an object whose every field is guaranteed to exist - so nothing past the
boundary ever has to defend against bad or missing data.