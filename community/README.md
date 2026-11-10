# The chut community list

`numbers.csv` is the list of spam numbers that every chut app can use. It is built
from public reports, with no server and no account:

1. Someone reports a number, from the app or with the
   [report form](https://github.com/dabane-ghassan/chut/issues/new?template=report.yml).
2. The [Community list workflow](../.github/workflows/community.yml) adds a line to
   `reports.csv`, runs `build.py`, answers on the issue and closes it.
3. A number is listed once **two different people** reported it (or the maintainer did).
   A number whose "not spam" votes match its reports leaves the list.
4. Apps that turned on daily updates download `numbers.csv`. Every new app version
   also ships the current list, so it works offline from the first call.

## Files

| File | What it is |
|---|---|
| `numbers.csv` | The published list: `number,category,reports`. Generated, don't edit. |
| `reports.csv` | Every report. Reporters are a one-way hash of their GitHub user id, not their name. |
| `ignore.csv` | Numbers that must never be listed (wrong reports, legitimate services). Maintainers edit this. |
| `build.py` | Rebuilds `numbers.csv` (and the copy in `app/src/main/res/raw/`). |
| `process_report.py` | Turns one issue into one line of `reports.csv`. |

Rebuild by hand with `python3 community/build.py`; editing `reports.csv` or
`ignore.csv` on `main` rebuilds the list automatically.

## Reuse

The list is free to reuse under the [Open Database License (ODbL 1.0)](https://opendatacommons.org/licenses/odbl/1-0/):
credit "chut community list" and share improvements under the same license.

Since 2023, ARCEP reserves 01 62, 01 63, 02 70, 02 71, 03 77, 03 78, 04 24, 04 25,
05 68, 05 69, 09 48 and 09 49 for telemarketing; chut handles those ranges itself,
so they don't need to be reported number by number. Reports still help: they confirm
which numbers in those ranges are scams, and they catch spam from ordinary numbers.
