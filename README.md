# MiniLauncher

Minimalist, text-only Android home screen launcher (inspired by "Minimalist Phone"). Personal use, sideloaded.

## Features

Two home pages, each with a widget on top and a favorites list at the bottom (within thumb reach):

- **Home page:** today's date (tap → calendar), a widget (e.g. Todoist "Today"), favorites 1
- **Swipe right → second page:** a widget (e.g. the Google Calendar widget), favorites 2. Swipe left (or Back) to return
- **Swipe up → app list:** alphabetical with search; the keyboard opens automatically, Enter launches the top result. Pull down or press Back to close
- **Long-press an app:** add it to favorites 1 or 2, rename, hide, app info, uninstall
- **Widgets:** long-press the empty slot ("Long-press to add a widget") to pick one from all installed widgets, long-press a widget to replace or remove it. A swipe that starts on a widget still switches pages (swipe up only where the widget itself can't scroll up)
- **Long-press empty space** (e.g. above the date): settings (reorder favorites, widgets, hidden apps, version)
- Crash reports are saved and shown on the next launch with a copy button

## Build

```sh
./gradlew assembleDebug
```

Every push builds a debug APK in GitHub Actions and publishes it as a GitHub Release
(`build-<run number>`; `main` builds are marked latest, other branches are prereleases).
APKs are signed with the committed `app/debug.keystore`, so each new build installs as an update.
