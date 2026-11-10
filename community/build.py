#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds community/numbers.csv (the list the app uses) from community/reports.csv.

A number is listed when at least two different people reported it, or when the
maintainer did. Numbers in ignore.csv are never listed, and a number whose
"not spam" disputes match or outnumber its reports is dropped.

The result is also copied into the app (app/src/main/res/raw/community.csv), so
every new version ships with the latest list and works offline from day one.
"""
import csv
import os
import re
import sys
from collections import defaultdict
from datetime import date

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
REPORTS = os.path.join(HERE, "reports.csv")
IGNORE = os.path.join(HERE, "ignore.csv")
OUT = os.path.join(HERE, "numbers.csv")
APP_COPY = os.path.join(ROOT, "app", "src", "main", "res", "raw", "community.csv")
MIN_REPORTERS = 2

E164 = re.compile(r"^\+[1-9]\d{7,14}\*?$")


def normalize(raw: str) -> str:
    """Same rules as PhoneNumbers.normalize in the app: French numbers become +33…"""
    raw = (raw or "").strip()
    prefix = raw.endswith("*")
    digits = ""
    for ch in raw:
        if ch.isdigit():
            digits += ch
        elif ch == "+" and not digits:
            digits = "+"
    s = digits
    if s.startswith("00"):
        s = "+" + s[2:]
    elif s.startswith("0") and len(s) > 1:
        s = "+33" + s[1:]
    elif not s.startswith("+") and s.startswith("33") and len(s) == 11:
        s = "+" + s
    if s.startswith("+330"):
        s = "+33" + s[4:]
    if s.startswith("+33") and not prefix and len(s) != 12:
        return ""
    return s + ("*" if prefix and s else "")


def valid(n: str) -> bool:
    return bool(E164.match(n))


def rows(path):
    if not os.path.exists(path):
        return []
    with open(path, newline="", encoding="utf-8") as f:
        lines = [l for l in f if l.strip() and not l.lstrip().startswith("#")]
    return list(csv.DictReader(lines))


def ignored_numbers():
    return {normalize(r.get("number", "")) for r in rows(IGNORE)} - {""}


def build():
    ignore = ignored_numbers()
    reporters = defaultdict(set)
    disputers = defaultdict(set)
    scam_votes = defaultdict(int)
    maintainer = set()
    for r in rows(REPORTS):
        n = normalize(r.get("number", ""))
        if not valid(n):
            continue
        who = (r.get("reporter") or "").strip() or "anonymous"
        cat = (r.get("category") or "").strip()
        if cat == "not_spam":
            disputers[n].add(who)
            continue
        reporters[n].add(who)
        if who == "maintainer":
            maintainer.add(n)
        if cat == "scam":
            scam_votes[n] += 1

    listed = []
    for n, who in reporters.items():
        if any(n.startswith(i[:-1]) if i.endswith("*") else n == i for i in ignore):
            continue
        if len(disputers[n]) >= len(who) and n not in maintainer:
            continue
        if len(who) >= MIN_REPORTERS or n in maintainer:
            cat = "scam" if scam_votes[n] * 2 > len(who) else "telemarketing"
            listed.append((n, cat, len(who)))
    listed.sort()

    text = [
        "# chut community list: numbers reported by chut users.",
        f"# Built {date.today().isoformat()} from community/reports.csv by community/build.py. Do not edit by hand.",
        "# Free to reuse under the Open Database License (ODbL 1.0).",
        "number,category,reports",
    ]
    text += [f"{n},{c},{k}" for n, c, k in listed]
    content = "\n".join(text) + "\n"
    for path in (OUT, APP_COPY):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
    return listed


if __name__ == "__main__":
    listed = build()
    print(f"{len(listed)} numbers listed")
    sys.exit(0)
