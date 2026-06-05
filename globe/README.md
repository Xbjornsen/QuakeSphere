# :globe

A small, self-contained 3D globe rendering library for Android — built on
OpenGL ES 2 via `GLSurfaceView`, with a Kotlin-first public API and zero
third-party runtime dependencies beyond Gson (used only for the bundled
GeoJSON loader).

It was extracted from the QuakeStation app and remains useful for any
"plot stuff on a 3D Earth" use case: flight paths, shipping routes,
station alerts, weather observations, etc.

## What you get

- A drag-to-rotate, pinch-to-zoom **`GlobeView`** that drops into any
  `View` hierarchy (or `AndroidView` in Compose).
- Real continents drawn from bundled Natural Earth 1:110m land polygons,
  triangulated with a Kotlin port of Mapbox's earcut and recursively
  subdivided so they hug the sphere curvature even at max zoom.
- **Day/night**: a real-time UTC subsolar point drives a soft dimming
  overlay on the actual nighttime hemisphere, layered on top of a fixed
  view-light so the visible side is always readable.
- **Three layers** for consumer data:
  - `Marker` — a single billboarded glow on the surface
  - `MarkerStack` — a radial spine of stacked markers (useful for
    clusters that would overlap on the surface)
  - `RippleSpec` — an animated set of expanding rings around a point
- Tap callbacks fire on the **main thread** with the `Marker` that was hit.

## Quick start

```kotlin
val globe = GlobeView(context).apply {
    // Render one marker
    setMarkers(listOf(
        Marker(
            id      = "sydney",
            coord   = GeoCoord(lat = -33.86, lon = 151.21),
            color   = 0xFFFF8800.toInt(),
            sizeHint = 1.4f
        )
    ))

    // React to taps
    onMarkerClick = { hit -> Log.d("globe", "tapped ${hit.id}") }

    // Optional cosmetic toggles
    displaySettings = GlobeDisplaySettings(
        showContinentLines = true,
        showStars          = true,
        autoRotate         = false
    )
}
```

From Jetpack Compose:

```kotlin
AndroidView(
    factory = { ctx ->
        GlobeView(ctx).apply { onMarkerClick = { /* … */ } }
    },
    update = { view ->
        view.setMarkers(myMarkers)
        view.setStacks(myStacks)
        view.setRipples(myRipples)
    }
)
```

## Public API surface

| Type                   | Purpose |
|------------------------|---------|
| `GlobeView`            | The view. All consumer interaction goes here. |
| `GeoCoord`             | A lat/lon point. |
| `Marker`               | A single dot on the globe. |
| `MarkerStack`          | A group of markers stacked on a radial spine. |
| `RippleSpec`           | A point that emits animated expanding rings. |
| `GlobeDisplaySettings` | Cosmetic toggles (continent lines, stars, auto-rotate). |

Everything in `com.quakesphere.globe.internal.*` is `internal` Kotlin
visibility and *not* part of the public contract — including the
renderer, the GeoJSON loader, and the earcut implementation.

## What's bundled

- `res/raw/ne_110m_land.geojson` — 138 KB. Natural Earth 1:110m land
  polygons (public domain). Parsed once at first surface creation and
  cached as GL buffers for the rest of the session.

## What it deliberately doesn't do

- **No raster Earth texture** (Blue Marble, etc.). Continents are vector
  polygons. This keeps the APK ~5 MB lighter and gives clean
  highlight/outline rendering, at the cost of "satellite imagery" look.
- **No tiles, no LOD streaming.** Single static dataset.
- **No annotation layers.** If you want text labels over markers, do it
  yourself in Compose by projecting `GeoCoord` to screen with the
  renderer's MVP — that escape hatch is intentionally not in the public
  API yet.

## Building

```
./gradlew :globe:assembleDebug
```

Consume from another module with `implementation(project(":globe"))`.

## Origin

Lifted from the QuakeStation app's globe view. The "renderer" type still
carries some earthquake-tracker-flavoured naming inside (`pulsing`,
`MarkerStack`, etc. were inspired by swarm visualisation) but nothing
in the public API references domain concepts — it's pure geo + colour.
