# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.quakesphere.SomeTest"

# Lint
./gradlew lint

# Clean
./gradlew clean
```

## Tech Stack

- **Kotlin 1.9.22**, **AGP 8.2.2**, **Gradle 8.7**, **JDK 17** (Eclipse Adoptium, downloaded to `~/.gradle/jdks/`)
- Compose (via `composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }` — NOT the Kotlin 2.0 compose plugin)
- Hilt for DI, KSP for annotation processing, Room for local DB, Retrofit + Gson for networking, DataStore Preferences for settings, WorkManager for background sync

## Architecture

Clean Architecture with three layers:

```
domain/          ← pure Kotlin, no Android deps
  model/         ← Earthquake, EarthquakeSwarm, enums (DepthCategory, MagnitudeCategory)
  repository/    ← EarthquakeRepository interface
  usecase/       ← GetEarthquakesUseCase, SyncEarthquakesUseCase, DetectSwarmsUseCase

data/
  api/           ← Retrofit service + GeoJSON DTOs (EarthquakeResponse)
  db/            ← Room (EarthquakeEntity, EarthquakeDao, EarthquakeDatabase)
  repository/    ← EarthquakeRepositoryImpl (maps entities ↔ domain models)

ui/
  globe/         ← GlobeScreen + GlobeViewModel + GlobeRenderer (OpenGL ES 2.0)
  list/          ← EarthquakeListScreen + EarthquakeListViewModel
  detail/        ← EarthquakeDetailScreen + EarthquakeDetailViewModel
  settings/      ← SettingsScreen + SettingsViewModel
  navigation/    ← NavGraph (single-activity, Compose Navigation)
  theme/         ← Color.kt, Theme.kt, Type.kt

di/              ← AppModule (DataStore), DatabaseModule (Room), NetworkModule (Retrofit)
work/            ← EarthquakeSyncWorker (HiltWorker, periodic background sync)
notification/    ← EarthquakeNotificationManager
```

## Module Layout

Two Gradle modules: `:app` (domain + UI) and `:globe` (reusable OpenGL globe library). The `:globe` module is self-contained — it only exposes `GlobeView`, `GlobeDisplaySettings`, `Marker`, `MarkerStack`, `RippleSpec`, `GeoCoord`. Domain types from `:app` are mapped to library types via `EarthquakeMapper` before being handed to the view.

## Key Design Decisions

**DataStore keys are defined in `SettingsViewModel.companion`** and referenced from `GlobeViewModel` and `EarthquakeSyncWorker` via `SettingsViewModel.KEY_*`. If you add a new setting, define the key there.

**Globe rendering** (`GlobeRenderer.kt`) runs on a GL thread. State passed from the main thread (`earthquakes`, `swarms`, `showContinentLines`, etc.) uses `@Volatile`. The renderer draws in order: stars → globe mesh → continent lines → swarm spines → earthquake markers. Continent line geometry is pre-built in `setupContinentLines()` from `ContinentData.kt` (Natural Earth 110m-scale polylines) and never rebuilt. Swarm spines are built per-frame.

**Swarm detection** (`DetectSwarmsUseCase`) uses a greedy single-pass clustering: 200 km Haversine radius + 48 h time window, minimum 3 events. It runs on `Dispatchers.Default` inside both `GlobeViewModel` and `EarthquakeListViewModel`. Swarm events are excluded from the flat marker pass in the renderer and rendered on radial spines instead.

**Settings flow**: All settings live in a single DataStore instance (`quakesphere_settings`). `GlobeViewModel` uses `flatMapLatest` to re-subscribe to `GetEarthquakesUseCase` whenever `minMagnitude` changes in DataStore.

**Data source**: USGS FDSNWS Event API (`https://earthquake.usgs.gov/fdsnws/event/1/query`). Geometry coordinates are `[lon, lat, depth]` (GeoJSON order — lon is `coords[0]`, lat is `coords[1]`). Data older than 30 days is pruned from Room on every sync.

**Background sync**: `EarthquakeSyncWorker` is a `HiltWorker` scheduled via WorkManager. It reads `minMagnitude` and notification settings directly from DataStore, then fires `EarthquakeNotificationManager` for quakes above threshold in the last 30-minute window.

**Compose compiler**: This project uses Kotlin 1.9.x. Do NOT add `org.jetbrains.kotlin.plugin.compose` — that plugin only exists from Kotlin 2.0+. Compose is enabled via `buildFeatures { compose = true }` + `composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }` in `app/build.gradle.kts`.

**Edge-to-edge**: `enableEdgeToEdge()` is called in `MainActivity`. The globe screen's header uses `.statusBarsPadding()` to clear the status bar. Other screens use `Scaffold` + `TopAppBar` which handles insets automatically.

**Runtime notification permission** (Android 13+): `MainActivity.requestNotificationPermissionIfNeeded()` launches a `RequestPermission` contract on first start. Without this, `EarthquakeSyncWorker`'s `checkSelfPermission(POST_NOTIFICATIONS)` silently returns false and no notifications ever fire — even though the permission is declared in the manifest.

**Replay feature**: `GlobeViewModel.startReplay()` advances a `ReplayState.index` once every `intervalMs` (default 1.5 s). `GlobeScreen` watches that index in `LaunchedEffect`, takes the chronologically-sorted quake list up to that index, calls `view.setMarkers(...)` (cumulative reveal), and calls `view.flyTo(...)` to glide the camera to each quake. Swarms and auto-rotate are suppressed during replay so they don't fight the playback.

**Historic-trends heatmap**: `HeatmapGenerator` (in `:globe`) computes a 720×360 single-channel density grid from the bundled PB2002 plate boundaries by splatting Gaussians (σ 4.5°, cutoff 18°) along each boundary line. This is a deterministic geophysical proxy for M5+ density — no offline data prep needed. The grid is built on a low-priority background thread the first time the GL surface comes up; the texture upload happens on the GL thread in `maybeUploadHeatmap()` once the bytes are ready. **Do not move this back to synchronous `onSurfaceCreated`** — it adds ~3 s to first-frame on phones and the user sees a blank screen for the duration. The heatmap is gated by `showHistoricTrends` (DataStore key `show_historic_trends`) and drawn between `drawGlobe()` and `drawContinentFills()` with `glDepthMask(false)` so continents still occlude it on land.
