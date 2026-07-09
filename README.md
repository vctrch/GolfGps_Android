# GolfGps for Android

A rangefinder in your pocket. GolfGps shows you how far you are from the green on any hole, using free and open golf data. It's a Kotlin + Jetpack Compose port of the [GolfGps](https://github.com/vctrch/GolfGps) iOS app.

**Package:** `com.vctrch.golfgps` · **Version:** 1.3 (kept in step with the iOS release)

## What it does

- Search thousands of US courses by name or city.
- Pick a course and start a round.
- See live yardage to the green as you move around the hole.
- View each hole on a map with tee and green markers.

## Where the data comes from

Everything here is built on open data, so there are no paid API subscriptions to worry about.

- **Course search, scorecards, and course centers** come from [OpenGolfAPI](https://opengolfapi.org).
- **Hole GPS coordinates** come from [OpenStreetMap](https://www.openstreetmap.org) (via the Overpass API), wherever volunteers have mapped the holes.

Both sources are licensed under the [ODbL](https://opendatacommons.org/licenses/odbl/).

## How it's built

If you've worked with a modern Android app, this stack will feel familiar:

- **UI** — Jetpack Compose with Material 3.
- **State** — `ViewModel` exposing `StateFlow` + coroutines.
- **Networking** — Ktor + OkHttp, with kotlinx.serialization for JSON.
- **Maps** — Google Maps Compose in release builds; OpenStreetMap (osmdroid) in debug builds.
- **Location** — Google Play Services Fused Location.
- **Dependency injection** — Hilt.
- **Code quality** — ktlint and detekt.

### Saving data locally

The app keeps two kinds of local data so it works well offline and remembers your settings:

- **Room** (`GolfGpsCache`) caches course basics and OSM hole JSON (`scorecardJson`, `osmHolesJson`). Offline loads merge cached OSM greens into the scorecard fallback — same idea as iOS SwiftData `CourseDataCache`.
- **DataStore** (`golfgps_preferences`) holds user preferences such as the map display style.

### Project layout

```
app/src/main/kotlin/com/vctrch/golfgps/
  MainActivity.kt / GolfGpsApplication.kt / navigation/
  domain/          Models, GeoMath, mapping confidence, course helpers
  data/
    local/         Room cache + DataStore
    remote/        OpenGolf (Ktor), Overpass/OSM parser
    repository/    CourseRepository
    billing/       Play Billing tips
  location/        LocationRepository (Flow)
  feature/
    search/        Course search + info cards
    round/         Active round, mapping UI, ViewModel
    map/           Google/OSM hole maps
    support/       Developer tip card
```

## How far along is it (vs. the iOS app)

**Already working (core parity):**

- Debounced OpenGolf course search
- Lazy course load: scorecard + Room cache, then background OSM enrichment
- Live yards-to-green (gated to on-hole GPS) and yards-to-tee when mapped
- Mapping confidence badges, confidence-colored hole picker, Course mapping card
- OSM Overpass + `OSMHoleParser` merge / gap-fill pipeline
- Dual maps (Google release / OSM debug) with Standard / Satellite / Hybrid
- Offline basics **and** OSM hole merge from Room
- Reload hole GPS + bounded OSM refinement on location / hole change
- Search info cards (data sources + help map a course) and Play Billing tips
- GeoMath, ktlint, detekt, solid unit-test coverage for parser / cache / ViewModel

**Still to come:**

- Android Auto (iOS CarPlay equivalent)
- OpenGolf published tee yardages / tee-matcher refinements (partial on Android today)
- Location permission / precise-location banners matching iOS polish
- Full unit-test parity with iOS `GolfGpsTests/`
- Firebase analytics event instrumentation (dependency present)
## A note on map style and CarPlay/Android Auto

The map style preference on the round map is stored in **DataStore** and surfaced through a picker in the top-right corner of the hole map. **Android Auto is not set up** in this project — the same practical trade-off the iOS app makes with CarPlay, which is limited to Apple's fixed POI map.

## Related projects

- iOS app: [vctrch/GolfGps](https://github.com/vctrch/GolfGps)
- App Store bundle / Play package: `com.vctrch.golfgps`
