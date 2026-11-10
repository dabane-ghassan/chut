#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Turns a "Report a number" GitHub issue into a line of community/reports.csv.

Run by .github/workflows/community.yml. Reads the issue from GITHUB_EVENT_PATH,
writes the outcome to GITHUB_OUTPUT (status, number, reporters, listed) so the
workflow can answer on the issue.

Reporters are stored as a short one-way hash of their GitHub user id: enough to
count distinct people, without copying account names into the list.
"""
import csv
import hashlib
import json
import os
import re
import sys
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402

CATEGORIES = {
    "démarchage": "telemarketing",
    "demarchage": "telemarketing",
    "telemarketing": "telemarketing",
    "arnaque": "scam",
    "scam": "scam",
    "pas du spam": "not_spam",
    "not spam": "not_spam",
}


def field(body: str, *labels: str) -> str:
    """Value of an issue-form field: the text after its '### Label' heading."""
    for label in labels:
        m = re.search(r"^###\s*" + re.escape(label) + r"[^\n]*\n+(.*?)(?=^###|\Z)", body, re.M | re.S | re.I)
        if m:
            v = m.group(1).strip()
            if v and v != "_No response_":
                return v
    return ""


def category(text: str) -> str:
    t = text.strip().lower()
    for key, value in CATEGORIES.items():
        if t.startswith(key):
            return value
    return "telemarketing"


def out(**kv):
    path = os.environ.get("GITHUB_OUTPUT")
    lines = [f"{k}={v}" for k, v in kv.items()]
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    print("\n".join(lines))


def main():
    event = json.load(open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8"))
    issue = event["issue"]
    owner = event["repository"]["owner"]["login"]
    body = issue.get("body") or ""
    raw = field(body, "Numéro", "Number")
    number = build.normalize(raw.splitlines()[0] if raw else "")
    if number.endswith("*") or not build.valid(number):
        out(status="invalid", number=raw.replace("\n", " ")[:40] or "?", reporters=0, listed="false")
        return
    cat = category(field(body, "Catégorie", "Category"))
    user = issue["user"]
    reporter = "maintainer" if user["login"] == owner else hashlib.sha256(
        f"chut:{user['id']}".encode()).hexdigest()[:12]

    existing = build.rows(build.REPORTS)
    if any(build.normalize(r["number"]) == number and r["reporter"] == reporter and r["category"] == cat for r in existing):
        status = "duplicate"
    else:
        with open(build.REPORTS, "a", newline="", encoding="utf-8") as f:
            csv.writer(f, lineterminator="\n").writerow([number, cat, reporter, issue["number"], date.today().isoformat()])
        status = "recorded"

    listed = {n: k for n, c, k in build.build()}
    people = len({r["reporter"] for r in build.rows(build.REPORTS)
                  if build.normalize(r["number"]) == number and r["category"] != "not_spam"})
    out(status=status, number=number, category=cat, reporters=people,
        listed="true" if number in listed else "false")


if __name__ == "__main__":
    main()
