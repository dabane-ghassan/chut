<p align="center">
  <img src="fastlane/metadata/android/en-US/images/featureGraphic.png" alt="chut – spam calls, silenced" width="720">
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: GPL v3 or later" src="https://img.shields.io/badge/license-GPL--3.0--or--later-3D35A8"></a>
  <a href="https://github.com/dabane-ghassan/chut/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/dabane-ghassan/chut?sort=semver&color=3D35A8&label=release"></a>
  <a href="https://github.com/dabane-ghassan/chut/actions/workflows/android.yml"><img alt="Android build" src="https://github.com/dabane-ghassan/chut/actions/workflows/android.yml/badge.svg"></a>
  <a href="community/numbers.csv"><img alt="Community list" src="https://img.shields.io/badge/community_list-ODbL-FF6B57"></a>
</p>

# A free call blocker that tells you why.

**chut** (French for *shh!*) checks every unknown call **before your phone rings** and silences
telemarketing and scams. No account, no ads, no trackers, no subscription: every feature and every
statistic is free, for everyone. Built for France, works anywhere on Android 10+.

🇫🇷 *chut filtre le démarchage et les arnaques avant que le téléphone sonne. Gratuit, libre, sans compte.*
*Tout est expliqué en français sur le [site](https://dabane-ghassan.github.io/chut/).*

## Installation

<p>
  <a href="https://github.com/dabane-ghassan/chut/releases/latest/download/chut.apk"><img alt="Download the latest APK" src="https://img.shields.io/badge/Download-APK-3D35A8?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.dabaneghassan.chut%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdabane-ghassan%2Fchut%22%2C%22author%22%3A%22Ghassan%20Dabane%22%2C%22name%22%3A%22chut%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Echut-v.%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img alt="Get it on Obtainium" src="https://img.shields.io/badge/Obtainium-updates-FF6B57?style=for-the-badge"></a>
  <img alt="F-Droid: coming soon" src="https://img.shields.io/badge/F--Droid-coming_soon-lightgrey?style=for-the-badge&logo=fdroid&logoColor=white">
</p>

1. Download `chut.apk` on your phone and open it (allow your browser to install apps if asked).
2. In chut, tap **Turn on protection** and pick chut as your **Caller ID & spam app**.
3. That's it. Telemarketing stops ringing; doubtful calls ring silently with a notification.

## What it does

- **Blocks before the ring** with Android's own call-screening API. Blocked calls stay in your call history.
- **ARCEP telemarketing ranges**: since 2023, French telemarketers must call from 01 62, 01 63, 02 70, 02 71,
  03 77, 03 78, 04 24, 04 25, 05 68, 05 69, 09 48 or 09 49. chut knows them without any list.
- **Community list**: numbers two people reported, in a [public list](community/) built from GitHub issues, with no server.
- **An engine that explains itself** (see below), and **learns from your blocks**.
- **Unlimited lists**: block list, allow list, whole ranges (`0162*`), import from your phone's
  "Blocked numbers" export, a file, or your call log; export everything as CSV.
- **Every statistic**: per day and week, by type, reason, region, hour and weekday, the most
  insistent numbers, time saved, and a weekly report.
- **Senior protection**: strict filtering, and only contacts ring normally. Made for a parent's phone.
- **Private by design**: works offline. Lists and history never leave the phone. Every permission is optional.
- **Light**: no background service, no dependencies, an APK under 1 MB.

## How chut decides

Android asks chut about each incoming call. chut answers on the phone in a fraction of a millisecond
([`Engine.java`](app/src/main/java/io/github/dabaneghassan/chut/engine/Engine.java)):

1. **Hard rules.** Contacts and your allow list always ring; your block list never does.
2. **Signals**, each with a weight out of 100:

   | Signal | Weight |
   |---|---|
   | Reported as a scam / as telemarketing by the community | 85 / 75 |
   | ARCEP telemarketing range | 70 |
   | Caller ID failed the operator's authentication check (spoofed) | 60 |
   | Neighbours in the same block of 1,000 numbers that you or the community blocked (1 / 2 / 3+) | 20 / 45 / 60 |
   | Premium rate (089) | 50 |
   | High-risk country code (one-ring callback scams) | 45 |
   | Malformed French number | 40 |
   | Unknown number during your quiet hours | 30 |
   | Foreign number | 10 |

3. **Combine** as a noisy OR, `score = 1 − Π(1 − wᵢ)`: two weak signals make a stronger one, never above 100.
4. **Decide** with your sensitivity: *relaxed* silences from 60 and blocks from 90, *balanced* 40 / 70, *strict* 25 / 50.
   A silenced number that calls back within 5 minutes rings: a person insists, a robot moves on.

Every verdict keeps its reasons; tap a call to see them. The **Block** and **Not spam** buttons on
notifications feed your lists, and your lists feed the learning signal. You can try the same rules on the
[website's number checker](https://dabane-ghassan.github.io/chut/#tester).

## The community list

Report a number from the app, or with the [report form](https://github.com/dabane-ghassan/chut/issues/new?template=report.yml).
A [workflow](.github/workflows/community.yml) records it and answers; once two different people
report a number, it joins [`community/numbers.csv`](community/numbers.csv). Apps can download it daily
(opt-in), and every release ships the current copy. Details in [community/README.md](community/README.md).

## Privacy

| Access | Why | |
|---|---|---|
| Call screening | Android gives chut each incoming number, before it rings | required |
| Contacts | "Contacts always ring": checks if a number is a contact, never reads names | optional |
| Call log | "Pick from your call log" when you ask for it | optional |
| Notifications | silenced calls and the weekly report, silently | optional |
| Internet | downloading the public community list, if you turn that on; nothing is uploaded | optional |

No account, no analytics, no ads, no crash reporting. The history of unknown numbers is kept for 400 days on the phone;
calls from contacts are never logged.

## Building

The APK is built by [GitHub Actions](.github/workflows/android.yml) on every change: a test build on the
`latest-build` pre-release, and a release whenever `versionName` in `app/build.gradle` changes. To build it
yourself or set up release signing, see [BUILDING.md](BUILDING.md).

The code is plain Java on the Android framework, with no third-party libraries:

```
app/src/main/java/io/github/dabaneghassan/chut/
  engine/   PhoneNumbers, Engine, Stats       pure Java, unit-tested on the JVM
  data/     Store (SQLite), Prefs, Community, Phone (contacts, call log)
  ui/       the four screens, charts, labels
  ScreeningService, Notifier, ActionReceiver, BackgroundJobs, MainActivity
community/  the community list and the scripts that build it
site/       the companion website (GitHub Pages)
brand/      the logo
```

## Support

If chut gives you some peace, you can [buy me a coffee](https://buymeacoffee.com/ghassandabane) ☕,
[report numbers](https://github.com/dabane-ghassan/chut/issues/new?template=report.yml), or open an issue or a pull request.

## License

chut is licensed under the [GNU GPL v3 or later](LICENSE). The community list is under the
[Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/). The logo in `brand/` is
CC BY-SA 4.0. The Barlow fonts are under the SIL Open Font License 1.1.
