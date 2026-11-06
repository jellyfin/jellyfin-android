#!/usr/bin/env python3
"""Evaluate JS in the phone's WebView over the DevTools protocol.

Minimal stdlib-only WebSocket client: the release APK enables WebView debugging, whose
socket is forwarded with `adb forward tcp:9223 localabstract:webview_devtools_remote_<pid>`.

Usage: wv_eval.py '<javascript expression>'
"""
import base64
import json
import os
import socket
import struct
import sys
import urllib.request

PORT = int(os.environ.get("WV_PORT", "9223"))


def targets():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json") as r:
        return json.load(r)


def page_ws():
    for t in targets():
        if t.get("type") == "page" and t.get("webSocketDebuggerUrl"):
            return t["webSocketDebuggerUrl"]
    raise SystemExit("no page target with a websocket url")


def connect(url):
    assert url.startswith("ws://")
    rest = url[len("ws://"):]
    hostport, path = rest.split("/", 1)
    host, port = hostport.split(":")
    sock = socket.create_connection((host, int(port)), timeout=90)
    key = base64.b64encode(os.urandom(16)).decode()
    req = (
        f"GET /{path} HTTP/1.1\r\n"
        f"Host: {hostport}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n\r\n"
    )
    sock.sendall(req.encode())

    # Read headers
    buf = b""
    while b"\r\n\r\n" not in buf:
        buf += sock.recv(4096)
    if b"101" not in buf.split(b"\r\n")[0]:
        raise SystemExit("handshake failed: " + buf.split(b"\r\n")[0].decode(errors="replace"))
    return sock


def send_text(sock, text):
    payload = text.encode()
    header = bytearray([0x81])  # FIN + text
    mask = os.urandom(4)
    n = len(payload)
    if n < 126:
        header.append(0x80 | n)
    elif n < (1 << 16):
        header.append(0x80 | 126)
        header += struct.pack(">H", n)
    else:
        header.append(0x80 | 127)
        header += struct.pack(">Q", n)
    header += mask
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    sock.sendall(bytes(header) + masked)


def recv_frame(sock):
    def read_exact(n):
        out = b""
        while len(out) < n:
            chunk = sock.recv(n - len(out))
            if not chunk:
                raise SystemExit("socket closed")
            out += chunk
        return out

    b1, b2 = read_exact(2)
    length = b2 & 0x7F
    if length == 126:
        length = struct.unpack(">H", read_exact(2))[0]
    elif length == 127:
        length = struct.unpack(">Q", read_exact(8))[0]
    data = read_exact(length)
    return data


def main():
    expression = sys.argv[1]
    sock = connect(page_ws())
    send_text(sock, json.dumps({
        "id": 1,
        "method": "Runtime.evaluate",
        "params": {
            "expression": expression,
            "returnByValue": True,
            "awaitPromise": True,
        },
    }))

    while True:
        data = recv_frame(sock)
        try:
            msg = json.loads(data)
        except Exception:
            continue
        if msg.get("id") != 1:
            continue
        result = msg.get("result", {})
        if "exceptionDetails" in result:
            print(json.dumps(result["exceptionDetails"], indent=2))
            return
        value = result.get("result", {}).get("value")
        print(json.dumps(value, indent=2, ensure_ascii=False) if not isinstance(value, str) else value)
        return


if __name__ == "__main__":
    main()
