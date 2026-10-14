# MiniLauncher

Minimalist, text-only Android home screen launcher (inspired by "Minimalist Phone"). Personal use, sideloaded.

## Features

- Home: centered clock inside a ring that shows the battery level (tap → alarms), date (tap → calendar), up to 8 favorite apps
- Swipe right: alphabetical app list with search; keyboard opens automatically, Enter launches the top result; swipe left to go back
- Long-press an app: favorite, rename, hide, app info, uninstall
- Long-press empty home area: settings (reorder favorites, hidden apps, version)
- Crash reports are saved and shown on the next launch with a copy button

## Build

```sh
./gradlew assembleDebug
```

Every push builds a debug APK in GitHub Actions and publishes it as a GitHub Release
(`build-<run number>`; `main` builds are marked latest, other branches are prereleases).
APKs are signed with the committed `app/debug.keystore`, so each new build installs as an update.
