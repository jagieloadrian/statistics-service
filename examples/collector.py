#!/usr/bin/env python3
"""Example collector app (stdlib only): poll a JSON URL, push native records to the platform.

Edit to_records() for your data; see .docs/Collector_Apps.md.

Env:
  SOURCE_URL        JSON endpoint to poll (required)
  SOURCE_ID         platform source id, a source with no mapping (required)
  SOURCE_TOKEN      token from POST /api/v2/sources/{id}/credential (required)
  PLATFORM_URL      default http://localhost:8080
  INTERVAL_SECONDS  default 60
  DEVICE            value of the `device` dimension, default the host name
"""
import json
import os
import socket
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

SOURCE_URL = os.environ["SOURCE_URL"]
SOURCE_ID = os.environ["SOURCE_ID"]
TOKEN = os.environ["SOURCE_TOKEN"]
PLATFORM_URL = os.environ.get("PLATFORM_URL", "http://localhost:8080")
INTERVAL = int(os.environ.get("INTERVAL_SECONDS", "60"))
DEVICE = os.environ.get("DEVICE", socket.gethostname())


def to_records(data):
    """Native format: every numeric top-level field becomes a value. Names must be declared in the dataset."""
    vals = {k: v for k, v in data.items() if isinstance(v, (int, float)) and not isinstance(v, bool)}
    return [{"ts": datetime.now(timezone.utc).isoformat(), "dims": {"device": DEVICE}, "vals": vals}]


def push(records):
    req = urllib.request.Request(
        f"{PLATFORM_URL}/api/v2/ingest/{SOURCE_ID}",
        data=json.dumps(records).encode(),
        method="POST",
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {TOKEN}"},
    )
    while True:
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code in (429, 503):  # rate limited / source paused: wait and retry the same records
                time.sleep(int(e.headers.get("Retry-After", "5")))
            elif e.code in (401, 404):  # wrong token or source id: retrying cannot help
                raise SystemExit(f"{e.code}: {e.read().decode()}")
            else:  # 400: the platform rejected the records, nothing was stored
                return {"rejected": json.loads(e.read() or b"{}").get("details")}


def main():
    while True:
        try:
            with urllib.request.urlopen(SOURCE_URL, timeout=10) as r:
                print(push(to_records(json.load(r))), flush=True)
        except (urllib.error.URLError, OSError, ValueError) as e:
            # ponytail: a failed poll or push is skipped, not buffered; add a local queue if gaps matter
            print(f"skipped: {e}", flush=True)
        time.sleep(INTERVAL)


if __name__ == "__main__":
    main()
