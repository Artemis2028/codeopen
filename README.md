# GridFix (working title)

MGRS land-navigation app for Android. Inspired by the category MilGPS defined on iOS,
built independently with its own design and code.

**Status: Milestone 1** — app shell, live MGRS/UTM/Lat-Lon position readout, night mode, settings.

## Roadmap

- **M1** — Shell + live position readout *(this build)*
- **M2** — Waypoints + compass navigation (bearing arrow, distance, back-azimuth, mils)
- **M3** — Map view with MGRS grid overlay, offline maps, MBTiles import
- **M4** — GPX import/export, waypoint projection, Play Billing (3-day trial → subscription), store launch

## Tech

Kotlin · Jetpack Compose · Material 3 · platform LocationManager + GNSS status ·
[NGA MGRS library](https://github.com/ngageoint/mgrs-java) (MIT) · DataStore · GitHub Actions CI

## Build

Pushes to `main` build a debug APK automatically (Actions tab → latest run → `gridfix-debug-apk` artifact).

Local build: `gradle assembleDebug` with JDK 17 and the Android SDK installed.

## Principles

Offline-first. No accounts, no analytics, no data collection. Not a primary means of navigation.
