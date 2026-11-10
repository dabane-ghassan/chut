#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Prints a short summary of JUnit XML results (one line per test)."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

root = sys.argv[1] if len(sys.argv) > 1 else "."
files = glob.glob(os.path.join(root, "**", "*.xml"), recursive=True)
if not files:
    print("   no results found (the tests did not run)")
    sys.exit(0)
passed = failed = 0
for f in files:
    for case in ET.parse(f).getroot().iter("testcase"):
        name = case.get("name")
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is None:
            passed += 1
            print(f"  PASS  {name}")
        else:
            failed += 1
            msg = (problem.get("message") or problem.text or "").strip().splitlines()
            print(f"  FAIL  {name}  ({msg[0][:300] if msg else ''})")
print(f"RESULT: {passed} passed, {failed} failed")
