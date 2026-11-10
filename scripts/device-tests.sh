#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Runs every on-device test and writes device-report.txt, whatever happens:
#   1. instrumented tests inside the app (database, model, language, notifications, jobs);
#   2. the end-to-end test with real incoming calls (scripts/e2e.sh) on the release APK.
# Exit code is 0 only when both pass.
set -u
APK="${1:-chut.apk}"
REPORT=device-report.txt

./gradlew --no-daemon connectedDebugAndroidTest > connected.log 2>&1
C=$?
{
  echo "== Instrumented tests (inside the app, debug build)"
  python3 scripts/junit_summary.py app/build/outputs/androidTest-results/connected
  if [ $C -ne 0 ]; then
    echo "   gradle exit code $C; last lines:"
    grep -E "FAILED|What went wrong|Exception|error:" connected.log | head -20 | sed 's/^/   /'
  fi
  echo
} > "$REPORT"

adb uninstall io.github.dabaneghassan.chut >/dev/null 2>&1
bash scripts/e2e.sh "$APK" e2e-report.txt
E=$?
cat e2e-report.txt >> "$REPORT"
cat "$REPORT"
[ $C -eq 0 ] && [ $E -eq 0 ]
