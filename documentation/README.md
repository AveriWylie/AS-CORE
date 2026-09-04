# SHAYVERI CORE - Documentation

Reference documentation meant to be **read by other people** to genuinely understand the system - clear, usable, and complete, the opposite of trying to learn something from sparse or scattered docs.

## What this is (and isn't)

This is **not**:
- **Inline code comments** - those explain a single file to whoever is editing it.

This **is**: the settled, human-facing explanation of *what each part is, why it exists, and how it fits together* - the understanding someone needs to work in this codebase without having built it.

## Structure

```
documentation/
  README.md              ← this file
  architecture/
    asdb.md
    security.md
  module1/
    module1-overview.md  ← what the module is, conceptually (added when settled)
    A3-telemetry-snapshot.md
    A4-game-event.md
    request-vs-document.md
  module2/  ...
```

One folder per module. Inside each: an **overview** (the module's purpose and shape), then **one doc per unit** as that unit is completed. Shared architecture notes live under `architecture/`.

## The rule for how docs get added here

Docs are added **passively and late** - only once a piece is a *long-standing conclusion*: implemented, understood, and unlikely to change. Nothing gets documented here while it's still in flux, half-built, or being figured out. That keeps this reference trustworthy: if it's written down here, it's true and stable.
