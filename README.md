# SHAYVERI CORE

**SHAYVERI CORE** (Compute Orchestration & Rendering Engine) is the Spring Boot backend of the SHAYVERI Roblox pipeline. It currently ingests and broadcasts live game telemetry, with planned modules for compute jobs, node coordination, versioned game configuration, and Roblox Open Cloud publishing.

CORE is the single source of truth for the system. Roblox game servers and lab compute nodes are clients that push data in and poll or receive configuration out. A React dashboard is the planned read/write control surface over WebSocket and REST. CORE alone talks to the Roblox Open Cloud API.

## Quick start

Prerequisites: Java 21 and Docker.

```bash
docker compose up -d
./gradlew bootRun --args='--shayveri.store=mongo'

# Verify health and authentication.
curl http://localhost:8080/actuator/health
curl -H "X-Api-Key: dev-dash-key" http://localhost:8080/api/ping
```

Every endpoint except `/actuator/health` requires an `X-Api-Key` header. Development keys are `dev-roblox-key` for game servers, `dev-node-key` for lab nodes, and `dev-dash-key` for the dashboard. Production keys come from environment variables.

ASDB is the default telemetry backend in `application.yml`, but it runs as a separate service and is not included in `docker-compose.yml`. The quick-start command selects MongoDB so the repository can run with its included services. See [ASDB adapter](documentation/architecture/asdb.md) for the ASDB path.

## Layout

```text
src/main/java/ascore/
  common/ config/
  ingress/ nodes/ jobs/ overrides/ egress/ realtime/ observability/
src/main/resources/
src/test/java/ascore/
documentation/
scripts/
```

Production code is organized by capability. Tests mirror the production package layout.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Scaffold, security filter, local infrastructure, `/api/ping` | Done |
| 1 | Telemetry ingress, MongoDB/ASDB storage, WebSocket feed | Implemented; some tests remain disabled |
| 2 | Node registry and job queue | Scaffolded |
| 3 | Configuration control plane and Open Cloud publishing | Scaffolded |
| 4 | Observability and hardening | Scaffolded |

## Documentation

- [Documentation index](documentation/README.md)
- [Ingress module](documentation/module1/module1-overview.md)
- [ASDB adapter](documentation/architecture/asdb.md)
- [API-key security](documentation/architecture/security.md)
- [Requests and stored documents](documentation/module1/request-vs-document.md)
