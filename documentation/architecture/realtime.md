Everything else in AS-CORE answers requests. This package pushes: when something changes, the dashboard is
told, rather than asking every few seconds. Five classes, and the module owns no data of its own.

```text
    RealtimePublisher         the interface every module calls: publish(topic, payload)
    StompRealtimePublisher    the STOMP implementation, and where alerts are kept as they go out
    StompAuthInterceptor      rejects any CONNECT frame without a DASH key
    AlertBuffer               the last 50 alerts, in memory
    SnapshotController        GET /api/snapshot, the whole current state in one response
```

## The topics

A fixed vocabulary, so the dashboard can be written against them:

```text
    /topic/telemetry/{placeId}   snapshots as they arrive
    /topic/nodes                 a node went UP or DOWN
    /topic/jobs                  queued, claimed, done
    /topic/jobs/{id}             progress on one job
    /topic/config                a version was activated
    /topic/alerts                job failures, dead nodes, degraded pushes
```

## The publisher is a seam

No module imports SimpMessagingTemplate. JobService calls publisher.publish("/topic/jobs", ...) and knows
nothing about WebSockets, the same way it calls JobStore and knows nothing about Mongo or asdb. If the
in-memory broker is ever swapped for an external one, nothing above this interface changes.

Payload is typed Object because one facade has to carry every module's payloads: a telemetry snapshot, a
node status, a job progress map. Jackson serialises whatever it is given.

## The socket is authenticated at CONNECT

StompAuthInterceptor sits on the client inbound channel, so every frame a client sends passes through it
before the broker sees it. It validates one: CONNECT, which must carry an X-Api-Key resolving to DASH.
Everything after that passes through, because the session is already vetted. security.md has the rest,
including why the /ws handshake itself is permitAll and why this cannot reuse the servlet filter.

### It is also the only point where realtime meets common

One class, importing ApiKeyResolver, ApiKeyRole and the header constant, on messages inbound from a client,
and only on the frame that opens a session. Nothing else in this package imports anything from common. The
other two connections to security are passive:

SnapshotController is a DASH-only endpoint, but nothing in the class says so. The rule /api/snapshot to DASH
lives in SecurityConfig, and the controller is ignorant of it, exactly like every other controller. It is
protected by security rather than interacting with it.

StompRealtimePublisher has no security at all. Broadcasts leave on a different channel from the one the
interceptor sits on, so nothing checks outbound messages. What protects them is that only a DASH session can
be connected to receive them in the first place.

### One non-security consequence

Worth remembering because it looks unrelated: the interceptor calls setUser on the accepted session, which
is what makes that session count in SimpUserRegistry. AsCoreMetrics reads that registry for
shayveri_ws_sessions, so without that one line the gauge would read zero with a dashboard connected.

## Alerts are kept as they go out

StompRealtimePublisher hands every /topic/alerts payload to AlertBuffer on its way to the broker, so the
buffer holds exactly what subscribers saw, newest first, capped at 50. Memory only: a restart loses them,
which is acceptable because alerts are ephemeral by nature. Durable history is Module 7's audit trail.

## The snapshot is for arriving late

A socket only carries changes. A dashboard opened at 3pm has no idea what happened before it, and one whose
laptop slept missed everything in between. GET /api/snapshot answers that in one response: nodes with their
status, queue depths, active jobs, active config versions, recent alerts.

It is assembled purely through other modules' interfaces, NodeService, JobStore, QueueStore and ConfigStore,
because realtime owns no data. AlertBuffer is the single exception, and only because alerts are broadcast
and then gone, so nothing else has them.

The dashboard subscribes first and fetches second. A change landing between those two steps arrives twice,
which is harmless: every message carries the new state rather than a change to it, so applying it again is
the same as applying it once.

## The broker is the scale limit

enableSimpleBroker("/topic") in WebSocketConfig is an in-memory broker: fine for one AS-CORE instance and a
handful of dashboards, and it keeps no messages for a client that is not connected. The plan's escape hatch
is one line there, enableStompBrokerRelay, pointing at a real broker. Documented so nobody designs around
the in-memory broker's limits by accident.
