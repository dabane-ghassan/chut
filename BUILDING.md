# Building chut

## On GitHub (the usual way)

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs the unit tests and builds a
release APK on every push to `main` that touches the app:

- every build goes to the **`latest-build`** pre-release as `chut-test-build.apk`;
- when `versionName` in `app/build.gradle` has no `v…` tag yet, it also publishes a **release**
  with `chut-vX.Y.Z.apk` and `chut.apk` (so `releases/latest/download/chut.apk` is always the newest).

To release: bump `versionCode` and `versionName` in `app/build.gradle`, add
`fastlane/metadata/android/{en-US,fr-FR}/changelogs/<versionCode>.txt`, push.

If a build fails, its full log is pushed to the `ci-log` branch
(`git fetch origin ci-log && git show origin/ci-log:build-log.txt`).

## Release signing

Without secrets, builds are signed with the committed **test key** (`app/test-signing.jks`): fine for
trying chut, but anyone could sign an APK with it. For real releases, create your own key once:

```sh
keytool -genkeypair -keystore chut-release.jks -alias chut -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 chut-release.jks   # copy the output
```

and add two repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `CHUT_KEYSTORE_BASE64` | the base64 output |
| `CHUT_KEYSTORE_PASSWORD` | the keystore password (use the same for the key) |

Keep `chut-release.jks` and its password safe and out of the repository: losing them means users must
uninstall to update. Switching from the test key to your key also needs one uninstall.

## Locally

Needs JDK 17 and the Android SDK (platform 35).

```sh
./gradlew testDebugUnitTest      # engine tests
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
python3 community/build.py       # rebuild the community list
```

## The website

[`site/`](site/) is a static page deployed by [`.github/workflows/pages.yml`](.github/workflows/pages.yml).
One-time setup: repository **Settings → Pages → Source: GitHub Actions**.
