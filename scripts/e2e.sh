#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# End-to-end test of chut on an Android emulator, with real incoming calls.
# Each scenario places a call with `adb emu gsm call` and checks three things:
# what Android did with it (rang, or was rejected), what chut decided and why
# (its database), and what chut showed (notifications, screens).
#
#   scripts/e2e.sh path/to/chut.apk [report.txt]
#
# Needs an emulator with root (a "google_apis" system image). Exit code = failed checks.
set -u
APK="$1"
REPORT="${2:-e2e-report.txt}"
PKG=io.github.dabaneghassan.chut
HERE="$(cd "$(dirname "$0")" && pwd)"
UI="python3 $HERE/ui.py"
PASS=0
FAIL=0
: > "$REPORT"

say() { echo "$*" | tee -a "$REPORT"; }
pass() { say "  PASS  $1"; PASS=$((PASS + 1)); }
fail() { say "  FAIL  $1${2:+  ($2)}"; FAIL=$((FAIL + 1)); }
check() { local name="$1"; shift; if "$@"; then pass "$name"; else fail "$name"; fi; }
section() { say ""; say "== $*"; }
sh() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

boot_wait() {
  adb wait-for-device
  for _ in $(seq 1 120); do
    [ "$(sh getprop sys.boot_completed)" = "1" ] && break
    sleep 2
  done
  adb root >/dev/null 2>&1
  adb wait-for-device
  for _ in $(seq 1 60); do
    [ "$(sh getprop sys.boot_completed)" = "1" ] && break
    sleep 2
  done
  sh input keyevent 82 >/dev/null   # unlock
  sleep 3
}

# The last verdict chut recorded for a number: "action|category|reasons" (action 0 rang, 1 silenced, 2 blocked).
pull_db() {
  rm -rf /tmp/chutdb && mkdir -p /tmp/chutdb
  for f in chut.db chut.db-wal chut.db-shm chut.db-journal; do
    adb pull "/data/data/$PKG/databases/$f" "/tmp/chutdb/$f" >/dev/null 2>&1
  done
}

sql() {   # one value from the app's database
  pull_db
  python3 - "$1" "${2:-}" <<'PY'
import sqlite3, sys
try:
    db = sqlite3.connect("/tmp/chutdb/chut.db")
    args = (sys.argv[2],) if sys.argv[2] else ()
    r = db.execute(sys.argv[1], args).fetchone()
    print("|".join("" if x is None else str(x) for x in r) if r else "none")
except Exception as e:
    print("error " + str(e))
PY
}

verdict() { sql "SELECT action, category, reasons FROM calls WHERE number=? ORDER BY id DESC LIMIT 1" "$1"; }
calls_logged() { sql "SELECT COUNT(*) FROM calls"; }
eq() { [ "$1" = "$2" ]; }
ge() { [ "$1" -ge "$2" ] 2>/dev/null; }
not_running() { [ -z "$(sh pidof $PKG)" ]; }
holds_role() { sh cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING | grep -q "$PKG"; }
jobs_scheduled() { sh dumpsys jobscheduler | grep -q "$PKG/.BackgroundJobs"; }
no_number_on_screen() { ! $UI texts | grep -qE '0[1-9]( [0-9]{2}){4}'; }
number_on_screen() { $UI texts | grep -qE '0[1-9]( [0-9]{2}){4}'; }
not_on_screen() { ! $UI has "$1" 6; }
mkdir -p shots
shot() { adb exec-out screencap -p > "shots/$1.png" 2>/dev/null; }
is_rejected() { [ "$1" = 5 ] || [ "$1" = 6 ]; }
newer() { [ -n "$2" ] && [ "$2" -gt "$1" ] 2>/dev/null; }

# Type of the newest entry for a number in the phone's own call log (1 in, 3 missed, 5 rejected, 6 blocked).
calllog() { adb shell "content query --uri content://call_log/calls --projection number:type:date --sort 'date DESC'" 2>/dev/null | tr -d '\r'; }
calllog_type() {
  local digits="${1#+33}"
  for _ in 1 2 3 4 5; do
    local t; t="$(calllog | grep -m1 -E "number=(\+33|0033|33|0)?$digits," | sed -n 's/.*type=\([0-9]*\).*/\1/p')"
    [ -n "$t" ] && { echo "$t"; return; }
    sleep 2
  done
}

# Places a call, notes whether the phone is ringing after a few seconds, hangs up.
RANG=0
call() {
  local n="$1"
  adb emu gsm call "$n" >/dev/null 2>&1
  sleep 7
  if sh dumpsys telephony.registry | grep -m1 "mCallState" | grep -q "mCallState=1"; then RANG=1; else RANG=0; fi
  adb emu gsm cancel "$n" >/dev/null 2>&1
  sleep 4
}

notified() {
  for _ in 1 2 3 4 5; do
    sh dumpsys notification --noredact | grep -qF "$1" && return 0
    sleep 2
  done
  return 1
}
running() { [ -n "$(sh pidof $PKG)" ]; }
broadcast() { sh am broadcast -n "$PKG/.ActionReceiver" -a "$PKG.$1" --es number "$2" >/dev/null; sleep 4; }
open_app() { sh am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 3; }

expect_call() {   # name number action(0/1/2) reason-substring rang(0/1)
  local name="$1" n="$2" action="$3" reason="$4" ring="$5"
  call "$n"
  local v; v="$(verdict "$n")"
  local a="${v%%|*}"
  if [ "$a" = "$action" ] && [[ "$v" == *"$reason"* ]] && [ "$RANG" = "$ring" ]; then
    pass "$name  [verdict $v, rang=$RANG]"
  else
    fail "$name" "expected action=$action reason~$reason rang=$ring; got verdict '$v' rang=$RANG"
  fi
}

say "chut end-to-end test — $(date -u +%Y-%m-%dT%H:%MZ)"
section "Setup"
boot_wait
say "  Android $(sh getprop ro.build.version.release) (API $(sh getprop ro.build.version.sdk)), $(sh getprop ro.product.model)"
adb uninstall "$PKG" >/dev/null 2>&1
check "install the APK" adb install -r "$APK"
sh cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING "$PKG" >/dev/null
check "chut holds the call-screening role" holds_role
for p in POST_NOTIFICATIONS READ_CONTACTS READ_CALL_LOG; do sh pm grant "$PKG" "android.permission.$p"; done

section "Calls while chut has never been opened"
check "app process is not running" not_running
expect_call "preset spam number is blocked (04 24 03 60 62)" "+33424036062" 2 "USER_LIST" 0
t="$(calllog_type +33424036062)"
check "it is in the phone's call log as rejected/blocked (type $t)" is_rejected "$t"
[ -n "$t" ] || { say "  call log:"; calllog | head -5 | sed 's/^/    /' | tee -a "$REPORT" >/dev/null; }
check "background jobs were scheduled without opening the app" jobs_scheduled

section "Calls after chut was force-stopped"
sh am force-stop "$PKG"
check "app is force-stopped" not_running
expect_call "ARCEP telemarketing range is blocked (01 62 99 99 99)" "+33162999999" 2 "ARCEP_RANGE" 0
expect_call "an ordinary mobile rings (06 12 34 56 78)" "+33612345678" 0 "" 1

section "Calls after the process was killed"
open_app
sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
PID="$(sh pidof $PKG)"
[ -n "$PID" ] && sh kill -9 $PID
sleep 2
check "process killed" not_running
expect_call "preset spam number is blocked (09 48 20 48 00)" "+33948204800" 2 "USER_LIST" 0

section "Learning from blocks (notification Block button)"
for n in +33512340001 +33512340457 +33512340930; do broadcast BLOCK "$n"; done
check "the three numbers are on the block list" eq "$(sql "SELECT COUNT(*) FROM numbers WHERE number LIKE '+3351234%' AND list=0")" 3
check "the model was saved" eq "$(sh ls /data/data/$PKG/files/model.bin)" "/data/data/$PKG/files/model.bin"
expect_call "an unseen number in that block is silenced by the model (05 12 34 02 22)" "+33512340222" 1 "LEARNED" 1
check "a silent notification explains it" notified "05 12 34 02 22"
expect_call "a number far from it still rings (05 99 88 77 66)" "+33599887766" 0 "" 1

section "Not spam button"
broadcast TRUST "+33162999999"
expect_call "a telemarketing number you trusted now rings (01 62 99 99 99)" "+33162999999" 0 "ALLOWLIST" 1

section "Background jobs"
sh dumpsys notification >/dev/null
sh cmd jobscheduler run -f "$PKG" 2 >/dev/null
sleep 6
check "weekly report job posts its notification" notified "Votre semaine avec chut"
before="$(sh stat -c %Y /data/data/$PKG/files/model.bin)"
sleep 2
sh cmd jobscheduler run -f "$PKG" 3 >/dev/null
sleep 8
after="$(sh stat -c %Y /data/data/$PKG/files/model.bin)"
check "weekly retraining job rewrites the model ($before → $after)" newer "$before" "$after"

section "Screens, French by default"
open_app
check "home is in French" $UI has "Protection active" 0
check "home shows how many calls were stopped" $UI has "appels stoppés" 0
check "no phone number is shown by default" no_number_on_screen
shot home-fr
$UI tap "Numéro à bloquer" 0 >/dev/null
sh input text 0478123456
sh input keyevent 66 >/dev/null
sleep 2
check "a number typed on the home screen goes on the block list" eq "$(sql "SELECT list FROM numbers WHERE number='+33478123456'")" 0
expect_call "and its calls are blocked (04 78 12 34 56)" "+33478123456" 2 "USER_LIST" 0
open_app
$UI tap "Statistiques" 0 >/dev/null
check "statistics page" $UI has "Pourquoi ils ont été stoppés" 4
check "statistics show no phone number" no_number_on_screen
shot stats-fr
sh input keyevent 4
$UI tap "Réglages" 0 >/dev/null
check "settings show the language switch" $UI has "Langue · Language" 0
check "advanced settings are folded" not_on_screen "Afficher les numéros"
shot settings-fr
$UI tap "Plus de réglages" 8 >/dev/null
check "the learned model reports its cross-validated recall" $UI has "des numéros de spam jamais vus" 12
shot settings-more-fr

section "Language switch"
sh input keyevent 4
$UI tap "Réglages" 0 >/dev/null
$UI tap "English" 0 >/dev/null
sleep 2
check "settings switch to English" $UI has "Settings" 0
sh input keyevent 4
check "home is in English" $UI has "Protection is on" 0
shot home-en
sh am force-stop "$PKG"
open_app
check "English is kept after the app is killed" $UI has "Protection is on" 0
$UI tap "Settings" 3 >/dev/null
$UI tap "Français" 0 >/dev/null
sleep 2
check "back to French" $UI has "Réglages" 0

section "Optional: show numbers"
$UI tap "Plus de réglages" 8 >/dev/null
check "turn on Show numbers" $UI tap "Afficher les numéros" 12
sh input keyevent 4
check "home now lists the latest calls" $UI has "Derniers appels inconnus" 8
check "with their numbers" number_on_screen
$UI tap "Mes listes" 8 >/dev/null
check "my lists show only my own numbers" $UI has "04 78 12 34 56" 6
check "the shipped numbers are counted, not listed" $UI has "fournis avec chut" 6
shot lists-fr
sh input keyevent 4
$UI tap "Réglages" 6 >/dev/null
$UI tap "Plus de réglages" 8 >/dev/null
$UI tap "Afficher les numéros" 12 >/dev/null
sh input keyevent 4
check "numbers hidden again" no_number_on_screen

section "Senior protection"
$UI tap "Réglages" 6 >/dev/null
$UI tap "Plus de réglages" 8 >/dev/null
check "turn on senior protection" $UI tap "Protéger une personne âgée" 12
sh input keyevent 3
sh am force-stop "$PKG"
expect_call "an unknown mobile rings silently (06 98 76 54 32)" "+33698765432" 1 "SENIOR_UNKNOWN" 1
check "with a notification" notified "06 98 76 54 32"
open_app
$UI tap "Réglages" 6 >/dev/null
$UI tap "Plus de réglages" 8 >/dev/null
$UI tap "Protéger une personne âgée" 12 >/dev/null
sh input keyevent 3

section "After a reboot"
adb reboot
sleep 15
boot_wait
check "chut still holds the call-screening role" holds_role
check "jobs survived the reboot" jobs_scheduled
expect_call "preset spam number is blocked after reboot (03 77 32 53 77)" "+33377325377" 2 "USER_LIST" 0
expect_call "the model still silences its block after reboot (05 12 34 07 77)" "+33512340777" 1 "LEARNED" 1
open_app
check "French after reboot" $UI has "Protection active" 0
shot home-after-reboot

section "History"
n="$(calls_logged)"
check "every screened call was recorded ($n)" ge "$n" 10

say ""
say "Screen at the end: $($UI texts | cut -c1-300)"
say ""
say "RESULT: $PASS passed, $FAIL failed"
exit "$FAIL"
