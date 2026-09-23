Three kinds of code touch the game, and only one of them is the game. Worth keeping straight, because
they live in different places and have different lifetimes.

## The three kinds

```text
scripts/                    operator tooling and build-time generators, run by hand
  ascore_cli.py               drives the AS-CORE API for black-box testing
  fake-roblox.sh              a curl loop posing as a game server
  roblox-studio/              Luau pasted into Studio's command bar, git-ignored

the game repo               game code: runs on every server, every session
  portals, queue, config client, telemetry client

AS-CORE                     the backend, and the asset pipeline described below
```

**Build-time generators** (`roblox-studio/`) are Luau, but they are not game code. They run once, in
Studio's command bar, against the open place. Their output is geometry saved into the place file: the
sealed hub shell, a lighting pass, a tree scatter. Re-running one rebuilds its output. Nothing about them
ships.

They are git-ignored for now because they are scratch: the numbers change every time the hub is walked.
Once the hub settles, the ones worth keeping become committed tooling.

**Game code** is the opposite in every respect. It runs on the server or the client while people play, it
is synced by Rojo from the game repo, and it is version controlled. The portal queue and the teleport are
the first of it.

The line: a generator produces a thing; game code produces behaviour.

## What AS-CORE can and cannot do for the visuals

"Rendering" means two different things and the split matters here.

REAL-TIME RENDERING is the player's device drawing the scene each frame. That cannot move. No pixels, no
frames and no geometry can be streamed into a running experience from outside Roblox, and anything
per-frame is Luau on the client.

OFFLINE WORK, which is what AS-CORE does, is better called BAKING or ASSET PREPROCESSING: turning
expensive computation into files ahead of time, so the client's per-frame work gets cheaper. Lightmaps,
decimated meshes, LODs, texture atlases. It is rendering in the offline sense, and it is entirely ours.

```text
AS-CORE job  →  node runs Blender  →  artifact  →  uploaded to Roblox  →  asset id
                                                                             │
                                                     AS-CORE config ◄────────┘
                                                            │
                                            the game reads the id at runtime
```

The handoff is two things and only two: an UPLOAD, which turns a file into an asset id, and a CONFIG
VALUE, which tells the game which id to use. The game never receives art from AS-CORE; it receives
identifiers for art already sitting on Roblox's CDN, where it is cached.

Which job type does what, for the forest hub:

    TEXTURE_BAKE          lighting and AO baked into a texture, so the client shades less
    LIGHT_BAKE            static lighting baked into scenery, since the hub has no time of day
    MESH_OPTIMIZE         decimated meshes and LOD variants, under the per-mesh triangle cap
    PATHFIND_PRECOMPUTE   navigation and visibility data, for the maps rather than the hub
    CUSTOM                texture atlasing, which is the real draw-call win with hundreds of trees

## What travels over HTTP at runtime

Data, never art. Small JSON the server fetches and Luau applies:

    asset ids           so swapping in a better-baked tree is a config change, not a republish
    scatter layout      positions, rotations and scales, if the forest is data-driven
    the darkness dials  ClockTime, Brightness, FogEnd, Atmosphere density
    gameplay numbers    party size, countdown, which portals are open

EditableImage and EditableMesh do allow textures and geometry to be built at runtime from fetched data.
Real, but rate limits, payload caps and client CPU make it wrong for static scenery: an uploaded asset is
cached and costs nothing per frame.

## Why it is worth the arrangement

The loop closes through telemetry. Server FPS per place tells you which scenery is expensive, which tells
you what to queue a bake for. Nodes bake overnight, the artifact is uploaded, a config activation swaps
the asset id, and every running server picks it up on its next poll. No republish, no client update, no
downtime.
