# SHAYVERI CORE - Documentation

Reference documentation meant to be **read by other people** to genuinely understand the system - clear, usable, and complete, the opposite of trying to learn something from sparse or scattered docs.

## What this is (and isn't)

This is **not**:
- **Inline code comments** - those explain a single file to whoever is editing it.
- **The plan / blueprints** (`Idea_Generation/plan.txt`, `Idea_Generation/docs/module*_blueprint.txt`) - those are *construction specs*: what to build and in what order.
- **The research log** (`Idea_Generation/claude-usage-guide.txt`) - that's a separate experiment.

This **is**: the settled, human-facing explanation of *what each part is, why it exists, and how it fits together* - the understanding someone needs to work in this codebase without having built it.

## Structure

```
documentation/
  README.md              ← this file
  module1/
    module1-overview.md  ← what the module is, conceptually (added when settled)
    A1-....md            ← one doc per unit, added when that unit is DONE + understood
    A2-....md
    ...
  module2/  ...
```

One folder per module. Inside each: an **overview** (the module's purpose and shape), then **one doc per unit** as that unit is completed.

## The rule for how docs get added here

Docs are added **passively and late** - only once a piece is a *long-standing conclusion*: implemented, understood, and unlikely to change. Nothing gets documented here while it's still in flux, half-built, or being figured out. That keeps this reference trustworthy: if it's written down here, it's true and stable.
