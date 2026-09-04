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

1. Authentication - ApiKeyAuthFilter. "Who is this?" Secret in, role out. If no secret matches, nothing is set and the
caller stays anonymous. Note it does not reject, it just doesn't authenticate you.

2. Authorisation - SecurityConfig. "May this role do this?" Role plus path in, allow or deny out. This is where the
rejection actually happens: .requestMatchers("/api/telemetry/**").hasRole("ROBLOX").

That split is why an unknown key gives 403 rather than 401, the filter shrugs, and the rule later says an
unauthenticated caller can't have that path.

So the map takes the secret that was sent and maps it to a role; a separate layer then decides what that role is allowed
to reach, that is the entire security premise for the http API.
