Every caller of the HTTP API presents an API key: one fixed secret string, sent in the X-Api-Key header on
every request. There is no login and no session. The key says which of three kinds of caller this is, and
that is the only identity AS-CORE has.

    ROBLOX   game servers: post telemetry, poll config
    NODE     lab machines: register, heartbeat, claim and report on jobs
    DASH     the dashboard: create jobs, edit and activate config, read everything

In dev the three come from application.yml as dev-roblox-key and friends. In production they come from the
SHAYVERI_KEY_* environment variables, so no real secret is ever committed. Not to be confused with
shayveri.opencloud.api-key, which is Roblox's key, sent OUTBOUND when egress pushes a config activation.


THE TWO LAYERS

Where they live: the key model and the filter are in common, the rules are in config/SecurityConfig, and
common imports neither config nor any module. The dependency only ever points inwards, which is what lets
the HTTP side and the STOMP side share one answer to "which secret means which role".

1. Authentication - common/ApiKeyAuthFilter. "Who is this?" Secret in, role out. If no secret matches, nothing is set and the
caller stays anonymous. Note it does not reject, it just doesn't authenticate you.

2. Authorisation - config/SecurityConfig. "May this role do this?" Role plus path in, allow or deny out. This is where the
rejection actually happens: .requestMatchers("/api/telemetry/**").hasRole("ROBLOX").

That split is why an unknown key gives 403 rather than 401, the filter shrugs, and the rule later says an
unauthenticated caller can't have that path.

So the map takes the secret that was sent and maps it to a role; a separate layer then decides what that role is allowed
to reach, that is the entire security premise for the http API.


HOW A KEY BECOMES A ROLE

The map is stored as role → secret:

    "roblox" → "dev-roblox-key"
    "node"   → "dev-node-key"
    "dash"   → "dev-dash-key"

But the filter searches it backwards. It doesn't look anything up by what was sent
it scans every entry comparing the value against the header, and when one matches it takes
that entry's key as the answer:

    for (Map.Entry<String, String> entry : apiKeyProperties.getApiKeys().entrySet()) {
        if (entry.getValue().equals(providedKey)) {        // match on the SECRET
            ApiKeyRole role = ApiKeyRole.valueOf(entry.getKey()...);   // take the ROLE

Which is also the order the pieces come alive in:

    1. application.yml          shayveri.security.api-keys: {roblox:…, node:…, dash:…}
            │
            ▼  Spring binds it, calling setApiKeys(Map<String,String>)
    2. ApiKeyProperties.apiKeys   ← the map now exists. Enum not involved at all.
            │
            ▼  @PostConstruct runs after binding
    3. validate()                 ← FIRST time the enum is consulted: are these names real?
            │
            ▼  at request time
    4. ApiKeyAuthFilter

Step 3 is why a typo in the YAML stops the application rather than quietly authorising an unknown role at
3am: the check happens once, at deployment, where somebody is watching.

THE SAME LOOKUP, WITHOUT THE FILTER

ApiKeyResolver is that scan on its own: secret in, Optional<ApiKeyRole> out, and nothing else. No header
read, no SecurityContext, no filter chain. It reads the same ApiKeyProperties, so it can never disagree with
the filter about which secret means which role.

It exists for callers that have a key but no servlet request. Today that is one: StompAuthInterceptor, on
the STOMP side below. The plan and the Module 7 blueprint also name it as the seam for where "who" comes
from in the audit trail, once keys go per person rather than per role, so "dash" becomes "averi".

The filter does not call it. Both read the same properties and so agree, but the scan is written twice, and
pointing the filter at the resolver is a safe change whenever the filter is next touched.


WHAT THE FILTER HANDS TO THE RULES

The filter stores an authority named "ROLE_" + role.name(), and SecurityConfig writes
hasRole(ApiKeyRole.NODE.name()), which adds that prefix back for you. That prefix is the contract between
the two: store it without, or ask with hasAuthority instead, and every rule silently denies. The principal
itself is the ApiKeyRole, which is what Authentication.getName() returns when a controller needs to record
WHO did something, as ConfigController and JobController do for the audit trail.

THE RULES ARE PATHS, NOT METHODS

SecurityConfig's rules are written as URL patterns because that is the only vocabulary available where it
runs. The filter chain sits BEFORE the dispatcher decides which controller handles a request, so there is no
controller and no method to name yet, only a method, a path and headers.

    /api/jobs                   exactly that path, nothing under it
    /api/telemetry/**           that path and anything beneath it, at any depth
    /api/nodes/*/heartbeat      one segment in the middle, so any node id
    /api/config/**              everything under /api/config, including itself

So * is one path segment and ** is any number of them. This is why /api/config/active has to be listed ABOVE
/api/config/**: the rules are matched in order, first match wins, and the broader pattern would otherwise
swallow the poll path and hand it to DASH.

The same patterns turn up all over Spring, not only in security: WebSocketConfig's addEndpoint("/ws"), CORS
mappings, static resource handlers. Controller mappings are the exception worth knowing:
@PostMapping("/api/nodes/{id}/heartbeat") uses {id} because it BINDS that segment to a parameter, where
SecurityConfig only has to match it.

THE COST OF MATCHING BY PATH. The patterns here and the mappings in controllers can drift apart. Rename a
mapping and the rule silently stops matching, so the request falls through to anyRequest().authenticated()
and becomes reachable by any valid key rather than the intended role. Nothing catches that at compile time,
which is why every module has a security test asserting the wrong role gets a 403.


THE STOMP HALF

Servlet filters do not run on WebSocket frames. The /ws handshake is HTTP and is permitAll on purpose, but
once the socket is upgraded, STOMP frames ride inside it and SecurityConfig never sees them again. So the
dashboard socket has its own equivalent of the filter:

    realtime/StompAuthInterceptor   rejects any CONNECT frame without a DASH key
    common/ApiKeyResolver           the same secret → role lookup, without the servlet chain

Same key, same roles, same reverse lookup, enforced at the one point a session is established rather than
per frame. A rejected CONNECT gets an ERROR frame and the socket closes.

Why a second class rather than the filter: the filter is servlet-shaped. It reads the header itself and
leaves an Authentication in the SecurityContext, because that is where SecurityConfig's rules look. A frame
has no servlet request to hang an identity on, so the resolver stops at an Optional and the interceptor
attaches the principal to the SESSION, which outlives any one frame.


WHAT A FAILURE REVEALS

ApiError is the one JSON shape every failure returns and GlobalExceptionHandler is what produces it, which
is a security decision as much as a consistency one: an unexpected error becomes a 500 with the internals
stripped, because a stack trace handed to a caller names your classes, line numbers and dependency versions.
No module formats its own errors, so there is one place that rule is enforced.


ENCAPSULATION, THE OTHER HALF OF IT

Keys and roles decide who may call in. Encapsulation decides how much is reachable once they are in, and
how much of the system a mistake in one module can touch. Same premise, one layer down: a small deliberate
surface, everything else unreachable.

Three places it is doing security work rather than tidiness:

Repositories are package-private. JobRepository, ConfigVersionRepository and the rest are visible only to
their adapter, so no other module can reach Mongo directly and skip the store seam, its validation or its
audit.

Request records are immutable. Validation runs once at the edge, and there is no setter, so nothing
downstream can alter a request after it has been checked. A mutable request would mean the value that was
validated and the value that was used need not be the same.

Entity mutators are package-private. Job.claim, Job.complete and Job.fail are callable by JobService, not
by a controller, so the status machine cannot be driven from the HTTP edge.

Stable default for this project: private fields; private helpers; public request records; public
controllers and endpoint methods; public service use-case methods; public store interfaces; private adapter
internals. When an exception appears, document the reason in the unit's dedicated documentation after the
design is settled.


INTERFACES AND ADAPTERS: DELIBERATELY PUBLIC CONTRACTS

The store rule introduced for a future asdb backend is an encapsulation decision. A service depends on
TelemetryStore, not MongoTelemetryStore. That interface must be visible where the service needs it. The
Mongo adapter's internal repositories stay hidden.

    public interface TelemetryStore {
        void saveSnapshot(TelemetrySnapshot snapshot);
    }

    @Component
    public class MongoTelemetryStore implements TelemetryStore {
        private final TelemetrySnapshotRepository snapshots;
        // Mongo-specific implementation stays here
    }

This is dependency inversion in concrete terms: change the adapter later, not every caller. Do not make an
adapter's database client or repository public "just in case."


MODULE 1 VISIBILITY MAP

    A1/A2 request records   visible: the record and its accessors, the HTTP/Roblox contract
                            hidden:  mutable fields, setters, validation and normalization detail
    A3/A4 Mongo documents   visible: the class, Mongo's no-arg constructor, getters, from(...)
                            hidden:  fields, representation decisions, incidental setters
    A5 TelemetryStore       visible: the interface and its signatures, a cross-module seam
                            hidden:  how any implementation stores data
    A6 MongoTelemetryStore  visible: its component identity and the contract it implements
                            hidden:  repository fields and persistence mechanics
    A7 TelemetryService     visible: accept(...) and acceptEvents(...), the module's use cases
                            hidden:  store, publisher and executor fields, topic construction
    A8 TelemetryController  visible: the endpoint methods, framework entry points
                            hidden:  the injected service field and HTTP helper logic
    B1/B2 realtime          visible: RealtimePublisher and publish(topic, payload)
                            hidden:  SimpMessagingTemplate and STOMP transport detail
    C1/C2 errors            visible: ApiError and the handler methods Spring calls
                            hidden:  field-map construction and logging helpers

The same shape holds for the later modules: the store interface and the service's use cases are public, the
repository and the adapter's mechanics are not.


LEAKING MUTABLE STATE

A private field is not fully encapsulated if a public getter hands out the same mutable collection. Node
returns Map.copyOf(capabilities) rather than the map itself, and request records normalize optional maps to
Map.of() for the same reason: downstream code sees a stable, non-null value rather than a mutable or
absent implementation detail.
