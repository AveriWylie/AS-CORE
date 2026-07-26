# SHAYVERI CORE

**SHAYVERI CORE** (Compute Orchestration & Rendering Engine) is the Spring Boot backend of the SHAYVERI Roblox pipeline: it ingests live game telemetry, orchestrates optimization jobs across lab compute nodes, and manages versioned live-game config pushed via Roblox Open Cloud. Runs standalone with just Docker (Mongo + Redis).

CORE is the single source of truth for the whole system. Roblox game servers and lab compute nodes are clients that push data in and poll/receive config out; a React dashboard is the read/write control surface over WebSocket + REST; CORE alone talks to the Roblox Open Cloud API.

## Research strand

This repository doubles as the substrate for an ongoing research program on **dynamic compute-density** — modulating an LLM agent (Claude) across different "focus modes" the way a human varies focus, measured through an external token/time lens. The scaffolding-and-documentation work here is the low-stakes busy-work the experiment runs on; the implementation is kept human-side by design (the offload principle). Start here:

- **Usage guide + experiment log** (readable PDF): `Idea_Generation/ClaudeUsageGuide.pdf` — the transferable guide (seven levers, recipes, failure modes) plus the append-only iteration log (the data).
- **Live dashboard** (open in a browser): `Idea_Generation/dashboard.html` — iterations, the temporal model (τ, ρ), the failure-signal board, file index.
- **Source of truth** (append-only text I log to each iteration): `Idea_Generation/claude-usage-guide.txt`.

## Quick Start

Prerequisites: Java 21, Docker.

```bash
docker compose up -d        # start local MongoDB + Redis
./gradlew bootRun           # boot the service

# verify
curl http://localhost:8080/actuator/health
# -> {"status":"UP"}
curl -H "X-Api-Key: dev-dash-key" http://localhost:8080/api/ping
# -> {"service":"shayveri-core","status":"ok"}
```

Every endpoint except `/actuator/health` requires an `X-Api-Key` header. Dev keys: `dev-roblox-key` (game servers), `dev-node-key` (lab nodes), `dev-dash-key` (dashboard). Production keys come from environment variables and are never committed.

## Layout

```
src/main/java/dev/shayveri/core/   the service, package-by-module:
  common/ config/                  shared pieces + Spring configuration
  ingress/ nodes/ jobs/ overrides/ egress/ realtime/ observability/
                                   one folder per module (see the plan)
Idea_Generation/plan.txt           THE PLAN: full module specs, milestones,
                                   acceptance criteria
```

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Scaffold, security filter, dev infra, `/api/ping` | Done (boot verification pending Docker install) |
| 1 | Telemetry ingress + live WebSocket feed | Next |
| 2 | Node registry + job queue | - |
| 3 | Config control plane + Open Cloud push | - |
| 4 | Observability + hardening | - |

## Documentation

Research (the compute-density program):
- **Usage guide + experiment log**: `Idea_Generation/ClaudeUsageGuide.pdf` (source: `claude-usage-guide.txt`)
- **Dashboard**: `Idea_Generation/dashboard.html`

Project (the SHAYVERI build):
- **The plan** (module specs, contracts, milestones): `Idea_Generation/plan.txt`
- Construction blueprints, one per module: `Idea_Generation/docs/module1–7_blueprint.txt`
- Pre-Phase-1 summary (structure, decisions, gitignore explained): `Idea_Generation/PrePhase1_Summary.pdf`
- Java/CS structure notes (local, git-ignored): `Idea_Generation/java-structure-notes.txt`
