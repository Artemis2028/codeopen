# GridFix (working title)

MGRS land-navigation app for Android. Inspired by the category MilGPS defined on iOS,
built independently with its own design and code.

**Status: Milestone 3** — map view with MGRS grid overlay, waypoints on map, ruler,
offline tile cache + area download, MBTiles import. (M1: position readout · M2: compass
navigation, waypoint folders, NATO symbology.)

## Roadmap

- **M1** — Shell + live position readout *(done)*
- **M2** — Waypoints + compass navigation (bearing arrow, distance, back-azimuth, mils) *(done)*
- **M3** — Map view with MGRS grid overlay, offline maps, MBTiles import *(this build)*
- **M4** — GPX import/export, waypoint projection, Play Billing (3-day trial → subscription), store launch

## Tech

Kotlin · Jetpack Compose · Material 3 · platform LocationManager + GNSS status ·
[NGA MGRS library](https://github.com/ngageoint/mgrs-java) (MIT) ·
[osmdroid](https://github.com/osmdroid/osmdroid) (Apache 2.0) · DataStore · GitHub Actions CI

Basemaps: OpenStreetMap (streets), OpenTopoMap (topo), Esri World Imagery (satellite) —
attribution shown in-app. Tile sources may need commercial-use review before store launch.

## Build

Pushes to `main` build a debug APK automatically (Actions tab → latest run → `gridfix-debug-apk` artifact).

Local build: `gradle assembleDebug` with JDK 17 and the Android SDK installed.

## Principles

Offline-first. No accounts, no analytics, no data collection. Not a primary means of navigation.
