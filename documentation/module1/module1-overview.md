# Module 1 - Ingress (Telemetry Intake)

**Package:** `ascore.ingress`
**One-line purpose:** accept telemetry from the outside world (Roblox game servers), validate it, timestamp it, store it, and broadcast it live - cheaply, because this is the highest-frequency path in the whole system.

This document explains *what the module is and how its pieces fit*. For the exact build steps see the blueprint (`Idea_Generation/docs/module1_blueprint.txt`); for line-level detail read the code comments.

---

## What "a module" means here

The codebase is organized **package-by-module**: each package is one self-contained capability, and everything for that capability lives together. `ingress` owns exactly one job - *taking telemetry in* - and nothing else in the system does that job. (The alternative, package-by-layer, would scatter all controllers into one folder, all services into another; we don't do that.)

Think of a module as a small department with one responsibility, staffed by a few classes that each do one thing.

---

## The pieces and how data flows through them

A telemetry POST arrives as JSON and moves through the module in a straight line:

```
JSON from a Roblox server
        │
        ▼
TelemetrySnapshotRequest      the SHAPE of incoming data (+ validation rules)
        │                     a record - immutable, never changed after arrival
        ▼
TelemetryController           the HTTP edge - receives the request, delegates,
        │                     replies 202 immediately. No logic of its own.
        ▼
TelemetryService              the logic - stamp the arrival time, convert the
        │                     request into a storable document, hand it onward.
        ▼
TelemetrySnapshot             the SHAPE of stored data
        │                     a class - Mongo fills it in when reading back
        ▼
TelemetryStore  (interface)   the persistence BOUNDARY - an abstraction.
        │                     Business logic only ever talks to this.
        ▼
MongoTelemetryStore           the concrete implementation - the ONLY class in
                              the module that knows MongoDB exists.
```

There are two parallel intake shapes: **`TelemetrySnapshotRequest`** (periodic snapshots: player count, FPS, round) and **`GameEventRequest`** (discrete events: deaths, round transitions, perf spikes - accepted as a batched array). Each has a matching stored form (`TelemetrySnapshot`, `GameEvent`).

---

## Why it's built this way (the design ideas worth understanding)

These are the concepts a newcomer should take away - they recur across every module:

- **One responsibility per class (SRP).** The *incoming* shape, the *stored* shape, the *logic*, and the *transport* are four different classes because they change for four different reasons. If the JSON format changes, only the request DTO moves; if the database changes, only the store implementation moves.

- **Records for data coming in, classes for data being stored.** A `record` is an immutable data carrier (fields fixed at creation; the compiler writes the constructor, accessors, `equals`, `hashCode`, `toString`). Requests are records because a request never changes after it arrives. Documents are plain classes because MongoDB needs to *mutate* them when loading rows back. Choosing record-vs-class per role is itself a design decision, not an accident.

- **The store is an interface, not the Mongo class.** `TelemetryService` depends on the `TelemetryStore` *abstraction*. The Mongo implementation sits behind it and is the only place the database is visible. Swap the implementation (a different database, an in-memory fake for tests) and nothing above it changes. This is dependency inversion, and it's the seam that keeps storage swappable.

- **Validation lives in one place.** The rules for what a valid payload looks like are annotations *on the request DTO* (`@NotBlank`, `@Min(0)`, `@Positive`…) and nowhere else. A bad payload is rejected at the edge with a clear 400, never deep inside the logic.

- **The controller is thin on purpose.** It does no real work - it receives, delegates to the service, and returns `202 Accepted` right away. The actual persistence and broadcast happen asynchronously so the caller isn't kept waiting.

---

## How you'd extend or debug it

- **New field on a snapshot?** Add it to `TelemetrySnapshotRequest` (with any validation) and to `TelemetrySnapshot`, and map it in the request→document conversion. Nothing else needs to change.
- **Payloads getting rejected?** The rule is an annotation on the request DTO; that's the only place to look.
- **Switching the database?** Write a new class implementing `TelemetryStore`; leave every other file untouched.

That last point is the whole payoff of the structure: each kind of change has exactly one place it belongs.
