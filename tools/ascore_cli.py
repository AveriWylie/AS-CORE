"""
A hand-driven CLI for black-box testing AS-CORE. You type a command, it makes one
HTTP call, and prints exactly what came back: the status code and the body.

Nothing happens on its own except one thing: once you register a node, a background
heartbeat keeps it alive every 10 seconds, the way a real agent would. Turn it off
with "beat off" to make the node die and watch AS-CORE mark it DOWN.

Standard library only, so there is nothing to install:

    python3 tools/ascore_cli.py
    python3 tools/ascore_cli.py --server http://other-machine:8080

Type "help" at the prompt for every command, or "help <command>" for one.
"""

import argparse
import cmd
import json
import threading
import urllib.error
import urllib.request

# The dev keys from application.yml. Each command uses the role AS-CORE expects for it.
KEYS = {"roblox": "dev-roblox-key", "node": "dev-node-key", "dash": "dev-dash-key"}


def call(server, method, path, role, body=None, headers=None):
    """One HTTP request. Returns (status, headers, body text). Never raises on 4xx/5xx."""
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(server + path, data=data, method=method)
    request.add_header("X-Api-Key", KEYS[role])
    if data is not None:
        request.add_header("Content-Type", "application/json")
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        # 30s because a claim can legitimately wait 20s for work
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.headers, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read().decode()
    except urllib.error.URLError as error:
        return None, {}, f"could not reach {server}: {error.reason}"


def show(status, text):
    """Prints a response, pretty-printing JSON when the body is JSON."""
    print(f"-> {status}")
    if not text:
        return
    try:
        print(json.dumps(json.loads(text), indent=2))
    except ValueError:
        print(text)


def parse_value(raw):
    """key=value values: true/false, whole numbers and decimals become JSON types, anything else stays text."""
    if raw in ("true", "false"):
        return raw == "true"
    for kind in (int, float):
        try:
            return kind(raw)
        except ValueError:
            pass
    return raw


class Cli(cmd.Cmd):
    intro = "AS-CORE test CLI. Type help for commands, quit to exit."
    prompt = "ascore> "

    def __init__(self, server):
        super().__init__()
        self.server = server
        self.node = None          # the node id you registered as
        self.job = None           # the job you last claimed
        self.etags = {}           # last ETag seen per place, for the 304 check
        self.beating = False
        self.stop = threading.Event()

    def request(self, method, path, role, body=None, headers=None):
        status, response_headers, text = call(self.server, method, path, role, body, headers)
        show(status, text)
        return status, response_headers, text

    # ---------- general ----------

    def do_health(self, _):
        """health : is AS-CORE up, and can it reach asdb and Redis"""
        self.request("GET", "/actuator/health", "dash")

    def do_snapshot(self, _):
        """snapshot : the whole current state (nodes, queue, active jobs, config, alerts)"""
        self.request("GET", "/api/snapshot", "dash")

    def do_metrics(self, _):
        """metrics : only the shayveri_ lines from /actuator/prometheus"""
        status, _, text = call(self.server, "GET", "/actuator/prometheus", "dash")
        print(f"-> {status}")
        print("\n".join(line for line in text.splitlines() if line.startswith("shayveri_")))

    def do_audit(self, arg):
        """audit [action] : dashboard changes in the last 24h, e.g. audit config.activate"""
        self.request("GET", "/api/audit" + (f"?action={arg}" if arg else ""), "dash")

    # ---------- roblox ----------

    def do_telemetry(self, arg):
        """telemetry <placeId> [players] : send one snapshot as a Roblox server would"""
        parts = arg.split()
        if not parts:
            return print("usage: telemetry <placeId> [players]")
        players = int(parts[1]) if len(parts) > 1 else 10
        self.request("POST", "/api/telemetry", "roblox",
                     {"placeId": parts[0], "jobId": "cli", "playerCount": players, "serverFps": 60.0})

    # ---------- node ----------

    def do_register(self, arg):
        """register <nodeId> [blender] [slots] : become a node, and start heartbeating every 10s"""
        parts = arg.split()
        if not parts:
            return print("usage: register <nodeId> [blender] [slots]")
        self.node = parts[0]
        capabilities = {"blender": "blender" in parts}
        slots = next((int(p) for p in parts[1:] if p.isdigit()), 1)
        status, _, _ = self.request("POST", "/api/nodes/register", "node",
                                    {"nodeId": self.node, "hostname": "cli", "capabilities": capabilities,
                                     "maxConcurrentJobs": slots})
        if status == 200:
            self.do_beat("on")

    def do_beat(self, arg):
        """beat on|off : start or stop the background heartbeat. off = the node dies"""
        if arg == "on" and self.node and not self.beating:
            self.beating = True
            self.stop.clear()
            threading.Thread(target=self.heartbeat_loop, daemon=True).start()
            print(f"heartbeating as {self.node} every 10s")
        elif arg == "off" and self.beating:
            self.beating = False
            self.stop.set()
            print("heartbeat stopped: AS-CORE marks this node DOWN within about 60s")
        else:
            print(f"heartbeat is {'on' if self.beating else 'off'} (register first if you have no node)")

    def heartbeat_loop(self):
        while not self.stop.is_set():
            running = [self.job] if self.job else []
            call(self.server, "POST", f"/api/nodes/{self.node}/heartbeat", "node",
                 {"currentLoad": len(running), "runningJobIds": running})
            self.stop.wait(10)

    def do_claim(self, arg):
        """claim [blender] : ask for a job. Waits up to 20s; 204 means nothing to do"""
        if not self.node:
            return print("register first")
        status, _, text = self.request("POST", "/api/jobs/claim", "node",
                                       {"nodeId": self.node, "capabilities": {"blender": "blender" in arg}})
        if status == 200:
            self.job = json.loads(text)["id"]
            print(f"you now hold job {self.job}")

    def do_progress(self, arg):
        """progress <pct> [log text] : report progress on the job you hold"""
        parts = arg.split(maxsplit=1)
        if not self.job or not parts:
            return print("usage: progress <pct> [log]  (claim a job first)")
        body = {"pct": int(parts[0]), "log": parts[1] if len(parts) > 1 else None}
        self.request("POST", f"/api/jobs/{self.job}/progress", "node", body)

    def do_complete(self, arg):
        """complete [resultRef] : finish the job you hold"""
        if not self.job:
            return print("claim a job first")
        self.request("POST", f"/api/jobs/{self.job}/complete", "node",
                     {"resultRef": arg or "file:///tmp/result", "resultMeta": {"seconds": 1}})
        self.job = None

    def do_fail(self, arg):
        """fail [error] : fail the job you hold; AS-CORE retries it until maxRetries"""
        if not self.job:
            return print("claim a job first")
        self.request("POST", f"/api/jobs/{self.job}/fail", "node", {"error": arg or "failed from cli"})
        self.job = None

    # ---------- dashboard: jobs and nodes ----------

    def do_nodes(self, _):
        """nodes : every node and whether it is UP or DOWN"""
        self.request("GET", "/api/nodes", "dash")

    def do_create(self, arg):
        """create <TYPE> <mapId> [priority] : queue a job. TYPE: TEXTURE_BAKE LIGHT_BAKE MESH_OPTIMIZE PATHFIND_PRECOMPUTE CUSTOM"""
        parts = arg.split()
        if len(parts) < 2:
            return print("usage: create <TYPE> <mapId> [priority]")
        body = {"type": parts[0].upper(), "mapId": parts[1], "priority": int(parts[2]) if len(parts) > 2 else 0}
        self.request("POST", "/api/jobs", "dash", body)

    def do_jobs(self, arg):
        """jobs [STATUS] : list jobs, optionally only QUEUED, CLAIMED, RUNNING, DONE or FAILED"""
        self.request("GET", "/api/jobs" + (f"?status={arg.upper()}" if arg else ""), "dash")

    # ---------- dashboard and roblox: config ----------

    def do_save(self, arg):
        """save <namespace> key=value ... [place=<id>] : save a new config version, e.g. save spawns zombieSpeed=16"""
        parts = arg.split()
        if len(parts) < 2:
            return print("usage: save <namespace> key=value ... [place=<id>]")
        values = dict(p.split("=", 1) for p in parts[1:])
        place = values.pop("place", None)
        body = {"placeId": place, "namespace": parts[0], "values": {k: parse_value(v) for k, v in values.items()}}
        self.request("PUT", "/api/config", "dash", body)

    def do_activate(self, arg):
        """activate <namespace> <version> [placeId] : make a version live. Older version = rollback"""
        parts = arg.split()
        if len(parts) < 2:
            return print("usage: activate <namespace> <version> [placeId]")
        query = f"?namespace={parts[0]}" + (f"&placeId={parts[2]}" if len(parts) > 2 else "")
        self.request("POST", f"/api/config/activate/{parts[1]}{query}", "dash")

    def do_active(self, arg):
        """active [placeId] : poll config as Roblox would. Sends the last ETag, so unchanged = 304"""
        place = arg or "global"
        etag = self.etags.get(place)
        status, headers, _ = self.request("GET", "/api/config/active" + (f"?placeId={arg}" if arg else ""), "roblox",
                                          headers={"If-None-Match": etag} if etag else None)
        if status == 200:
            self.etags[place] = headers.get("ETag")
        print(f"(sent ETag {etag}, got {headers.get('ETag') if headers else None})")

    def do_history(self, arg):
        """history [placeId] : every saved config version"""
        self.request("GET", "/api/config/history" + (f"?placeId={arg}" if arg else ""), "dash")

    # ---------- leaving ----------

    def do_quit(self, _):
        """quit : exit. The heartbeat stops with it, so a registered node goes DOWN"""
        self.stop.set()
        return True

    do_exit = do_quit
    do_EOF = do_quit

    def emptyline(self):
        pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Hand-driven AS-CORE test CLI")
    parser.add_argument("--server", default="http://localhost:8080", help="where AS-CORE is running")
    Cli(parser.parse_args().server).cmdloop()
