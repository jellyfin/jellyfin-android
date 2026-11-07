#!/usr/bin/env python3
"""Reload the phone's WebView and report the JS errors/console output it produces.

The comments plugin runs once at page load; if something in that chain throws, everything after
it silently never runs (that is how the toolbar bell went missing on the phone). This attaches to
the DevTools socket, enables the Runtime/Log domains, reloads, and prints what came out.
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wv_eval import connect, page_ws, send_text, recv_frame  # noqa: E402


def main():
    seconds = float(sys.argv[1]) if len(sys.argv) > 1 else 12.0
    sock = connect(page_ws())

    def send(mid, method, params=None):
        send_text(sock, json.dumps({"id": mid, "method": method, "params": params or {}}))

    send(1, "Runtime.enable")
    send(2, "Log.enable")
    send(3, "Page.enable")
    send(4, "Page.reload", {"ignoreCache": True})

    sock.settimeout(2.0)
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            msg = json.loads(recv_frame(sock))
        except Exception:
            continue

        method = msg.get("method")
        if method == "Runtime.exceptionThrown":
            d = msg["params"]["exceptionDetails"]
            text = d.get("exception", {}).get("description") or d.get("text")
            print("EXCEPTION:", (text or "").split("\n")[0][:300])
        elif method == "Runtime.consoleAPICalled" and msg["params"].get("type") in ("error", "warning"):
            args = [str(a.get("value", a.get("description", ""))) for a in msg["params"].get("args", [])]
            print(msg["params"]["type"].upper() + ":", " ".join(args)[:300])
        elif method == "Log.entryAdded":
            e = msg["params"]["entry"]
            if e.get("level") in ("error", "warning"):
                print("LOG:", (e.get("text") or "")[:300], (e.get("url") or "")[:80])


if __name__ == "__main__":
    main()
