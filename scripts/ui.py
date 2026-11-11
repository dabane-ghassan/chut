#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Tiny UI automation over adb, for the end-to-end test: read the screen with uiautomator,
find a text, tap it, scroll to it.

    ui.py has "Protection active"      exit 0 if the text is on screen (scrolls down to look)
    ui.py tap "Réglages"               tap the first element showing this text
    ui.py texts                        print every text on screen (for the test report)
    ui.py y "Plus de réglages"         print where a text is on screen now (no scrolling)
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True)


def dump():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("exec-out", "cat", "/sdcard/ui.xml").stdout
        if "<hierarchy" in xml and "</hierarchy>" in xml:
            return ET.fromstring(xml[xml.index("<hierarchy"):xml.rindex("</hierarchy>") + len("</hierarchy>")])
        time.sleep(1)
    return None


def nodes():
    root = dump()
    return [] if root is None else list(root.iter("node"))


def find(text):
    for n in nodes():
        if text in (n.get("text") or "") or text in (n.get("content-desc") or ""):
            return n
    return None


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def scroll(down=True):
    size = adb("shell", "wm", "size").stdout
    m = re.search(r"(\d+)x(\d+)", size)
    w, h = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)
    y1, y2 = (int(h * 0.70), int(h * 0.30)) if down else (int(h * 0.30), int(h * 0.70))
    adb("shell", "input", "swipe", str(w // 2), str(y1), str(w // 2), str(y2), "300")
    time.sleep(0.8)


def locate(text, scrolls):
    for i in range(scrolls + 1):
        n = find(text)
        if n is not None:
            return n
        if i < scrolls:
            scroll()
    return None


def main():
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "texts":
        print(" | ".join(t for t in ((n.get("text") or "").strip() for n in nodes()) if t))
        return 0
    if cmd == "y":   # vertical position of a text on screen now, without scrolling
        n = find(args[0])
        if n is None:
            return 1
        print(center(n)[1])
        return 0
    text = args[0]
    scrolls = int(args[1]) if len(args) > 1 else (6 if cmd == "has" else 6)
    node = locate(text, scrolls)
    if node is None:
        return 1
    if cmd == "tap":
        x, y = center(node)
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(1.5)
    return 0


if __name__ == "__main__":
    sys.exit(main())
