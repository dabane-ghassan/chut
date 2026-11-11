<p align="center">
  <img src="fastlane/metadata/android/en-US/images/featureGraphic.png" alt="chut – spam calls, silenced" width="720">
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: GPL v3 or later" src="https://img.shields.io/badge/license-GPL--3.0--or--later-3D35A8"></a>
  <a href="https://github.com/dabane-ghassan/chut/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/dabane-ghassan/chut?sort=semver&color=3D35A8&label=release"></a>
  <a href="https://github.com/dabane-ghassan/chut/actions/workflows/android.yml"><img alt="Android build" src="https://github.com/dabane-ghassan/chut/actions/workflows/android.yml/badge.svg"></a>
  <a href="https://github.com/dabane-ghassan/chut/releases/tag/test-report"><img alt="Tested on Android 14 with real calls" src="https://img.shields.io/badge/tested-Android_14_real_calls-FF6B57"></a>
</p>

# A free call blocker that learns from what you block.

**chut** (French for *shh!*) checks every unknown call **before your phone rings**, even when the
app is closed, and silences telemarketing. It filters with your lists and with a model it trains on
the phone from what you block. No account, no ads, no trackers, no internet access, no subscription:
every feature and every statistic is free. In French by default, or English. Android 10+.

🇫🇷 *chut filtre le démarchage avant que le téléphone sonne et apprend de vos blocages. Gratuit, libre,
sans compte, sans internet. Tout est expliqué en français sur le [site](https://dabane-ghassan.github.io/chut/).*

## Installation

<p>
  <a href="https://github.com/dabane-ghassan/chut/releases/latest/download/chut.apk"><img alt="Download the latest APK" src="https://img.shields.io/badge/Download-APK-3D35A8?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.dabaneghassan.chut%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdabane-ghassan%2Fchut%22%2C%22author%22%3A%22Ghassan%20Dabane%22%2C%22name%22%3A%22chut%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Echut%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img alt="Get it on Obtainium" src="https://img.shields.io/badge/Obtainium-updates-FF6B57?style=for-the-badge"></a>
  <img alt="F-Droid: coming soon" src="https://img.shields.io/badge/F--Droid-coming_soon-lightgrey?style=for-the-badge&logo=fdroid&logoColor=white">
</p>

1. Download `chut.apk` on your phone and open it (allow your browser to install apps if asked).
   Or, for automatic updates, add chut to [Obtainium](https://github.com/ImranR98/Obtainium) with the
   badge above (or paste `https://github.com/dabane-ghassan/chut` in Obtainium).
2. In chut, tap **Turn on protection** and pick chut as your **Caller ID & spam app**.
3. That's it. Telemarketing stops ringing; doubtful calls ring silently with a notification.

## What it does

- **Blocks before the ring** with Android's own call-screening API, whether chut is open, closed,
  force-stopped or the phone just restarted. Blocked calls stay in your call history.
- **Your lists, if you want them**: chut ships no list of numbers. Block a number when it bothers you,
  or import your phone's "Blocked numbers" export, a file, or pick from your call log. Whole ranges
  (`0162*`) and an allow list work too. Numbers are never displayed unless you turn on *Show numbers*.
- **ARCEP telemarketing ranges**: since 2023, French telemarketers must call from 01 62, 01 63, 02 70,
  02 71, 03 77, 03 78, 04 24, 04 25, 05 68, 05 69, 09 48 or 09 49.
- **A model learned from your blocks** (below), with its accuracy shown in the app.
- **Every statistic**: per day and week, by type, reason, region, hour and weekday, the most
  insistent numbers, time saved, and a weekly report.
- **Senior protection**: strict filtering, and only contacts ring normally. Made for a parent's phone.
- **French by default**, with a Français / English switch in the settings.
- **Private by design**: no internet permission at all. Lists, history and model never leave the phone.
- **Light**: no background service, no dependencies, an APK under 1 MB.

## How chut decides

Android asks chut about each incoming call. chut answers on the phone in a fraction of a millisecond
([`Engine.java`](app/src/main/java/io/github/dabaneghassan/chut/engine/Engine.java)):

1. **Your lists.** Contacts and your allow list always ring; your block list never does.
2. **ARCEP ranges** count for 70 points.
3. **The learned model** ([`Learner.java`](app/src/main/java/io/github/dabaneghassan/chut/engine/Learner.java))
   gives up to 95 points. It is a logistic regression trained on the phone:
   - *features*: every prefix of the number from 4 to 9 digits, the line type, the hour of the call,
     hashed into 16,384 weights;
   - *data*: your blocked numbers (and when they called) as spam; your allow list, the calls you let
     ring and a background of 3,000 random French numbers as not spam (positive-unlabelled learning);
   - *training*: SGD on the log-loss with L2 regularisation, seeded, in well under a second; retrained
     after every list change, every Block / Not spam tap, and weekly;
   - *behaviour*: one blocked number does not make its neighbours suspicious (0.35); two in the same
     block of 1,000 numbers do (0.41), three more so (0.58); the rest of the 10,000-block stays low (0.23);
   - *accuracy*, by 5-fold cross-validation on a synthetic history of spam blocks (in the tests):
     **66 %** of hidden spam numbers recognised, **0.1 %** false alarms on random numbers. The app
     shows the same measure for your own list.
4. **Combine** the points as a noisy OR, `score = 1 − Π(1 − pᵢ)`, and **decide** with your sensitivity:
   *relaxed* silences from 60 and blocks from 90, *balanced* 40 / 70, *strict* 25 / 50.
   A silenced number that calls back within 5 minutes rings: a person insists, a robot moves on.

Every verdict keeps its reasons; tap a call to see them. You can try the same rules, and train the same
model, on the [website's number checker](https://dabane-ghassan.github.io/chut/#tester).

## Tested on a real Android system

Every change is tested before it can be released ([`android.yml`](.github/workflows/android.yml)):

- **JVM unit tests** of the engine, the statistics and the model (including the cross-validation above);
- **instrumented tests** inside the app on an Android 14 emulator: database, preset list, import and
  export, model training and persistence, language, notifications, background jobs;
- **an end-to-end test** ([`scripts/e2e.sh`](scripts/e2e.sh)) that places real incoming calls to the
  emulator and checks what Android did, what chut decided and why, and what it showed: with the app
  never opened, force-stopped, killed, and after a reboot; the notification buttons; the model learning
  from blocks; the weekly jobs; the screens in French and English; senior protection.

The latest report is published on the [test-report](https://github.com/dabane-ghassan/chut/releases/tag/test-report) page.

## Privacy

| Access | Why | |
|---|---|---|
| Call screening | Android gives chut each incoming number, before it rings | required |
| Contacts | "Contacts always ring": checks if a number is a contact, never reads names | optional |
| Call log | "Pick from your call log" when you ask for it | optional |
| Notifications | silenced calls and the weekly report, silently | optional |
| Internet | none: chut does not have the permission | never |

No account, no analytics, no ads, no crash reporting. The history of unknown numbers is kept for 400 days on the phone;
calls from contacts are never logged.

## Building

The APK is built and tested by [GitHub Actions](.github/workflows/android.yml) on every change: a test
build on the `latest-build` pre-release, and a release whenever `versionName` in `app/build.gradle` changes,
both only after all tests pass. To build it
yourself or set up release signing, see [BUILDING.md](BUILDING.md).

The code is plain Java on the Android framework, with no third-party libraries:

```
app/src/main/java/io/github/dabaneghassan/chut/
  engine/   PhoneNumbers, Engine, Learner, Stats   pure Java, unit-tested on the JVM
  data/     Store (SQLite), Learning (model on disk), Prefs, Lang, Phone (contacts, call log)
  ui/       the four screens, charts, labels
  ScreeningService, Notifier, ActionReceiver, BackgroundJobs, MainActivity
app/src/androidTest/   instrumented tests
scripts/    end-to-end test on an emulator (real incoming calls)
site/       the companion website (GitHub Pages)
brand/      the logo
```

## Support

If chut gives you some peace, you can [buy me a coffee](https://buymeacoffee.com/ghassandabane) ☕,
or open an issue or a pull request.

## License

chut is licensed under the [GNU GPL v3 or later](LICENSE). The logo in `brand/` is CC BY-SA 4.0. The Barlow fonts are under the SIL Open Font License 1.1.
