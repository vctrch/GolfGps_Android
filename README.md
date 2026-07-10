# GolfGps for Android

A rangefinder in your pocket. GolfGps shows you how far you are from the green on any hole, using free and open golf data.

**Package:** `com.vctrch.golfgps` · **Version:** 1.3

## What it does

- Search thousands of US courses by name or city.
- Pick a course and start a round.
- See live yardage to the green as you move around the hole.
- View each hole on a map with tee and green markers, plus mapping confidence when GPS data is approximate or still loading.

## Where the data comes from

Everything here is built on open data, so there are no paid API subscriptions to worry about.

- **Course search, scorecards, published tee yardages, and course centers** come from [OpenGolfAPI](https://opengolfapi.org).
- **Hole GPS coordinates** come from [OpenStreetMap](https://www.openstreetmap.org) (via the Overpass API), wherever volunteers have mapped the holes.

Both sources are licensed under the [ODbL](https://opendatacommons.org/licenses/odbl/).

## How it's built

- **UI** — Jetpack Compose with Material 3.
- **State** — `ViewModel` exposing `StateFlow` + coroutines.
- **Networking** — Ktor + OkHttp, with kotlinx.serialization for JSON.
- **Maps** — Google Maps Compose in release builds; OpenStreetMap (osmdroid) in debug builds (works on emulators without a Maps API key).
- **Location** — Google Play Services Fused Location.
- **Analytics** — Firebase Analytics.
- **Dependency injection** — Hilt.
- **Code quality** — ktlint and detekt.

### Saving data locally

- **Room** (`GolfGpsCache`) caches course basics and OSM hole JSON (`scorecardJson`, `osmHolesJson`). Offline loads merge cached OSM greens into the scorecard fallback.
- **DataStore** (`golfgps_preferences`) holds user preferences such as the map display style.

### Project layout

```
app/src/main/kotlin/com/vctrch/golfgps/
  MainActivity.kt / GolfGpsApplication.kt / navigation/
  domain/          Models, GeoMath, mapping confidence, course helpers
  data/
    local/         Room cache + DataStore
    remote/        OpenGolf (Ktor), Overpass/OSM parser + tee matcher
    repository/    CourseRepository
    analytics/     Firebase event helpers
    billing/       Play Billing tips
  location/        LocationRepository (Flow) + permission / precise-location status
  feature/
    search/        Course search + info cards
    round/         Active round, mapping UI, unavailable/retry, ViewModel
    map/           Google/OSM hole maps
    support/       Developer tip card
```

## Current status

**Working:**

- Debounced OpenGolf course search
- Lazy course load: scorecard + Room cache, then background OSM enrichment
- OpenGolf published tee yardages merged into the scorecard
- Orphan OSM tee matching using published yardages (`TAGGED` / `MATCHED` / fairway sources)
- Live yards-to-green (gated to on-hole GPS) and yards-to-tee when mapped
- Mapping confidence badges, confidence-colored hole picker, Course mapping card
- Location banners (services off / permission / waiting GPS / precise location)
- Round unavailable screen with retry when course load fails
- OSM Overpass + hole parser merge / gap-fill pipeline
- Dual maps (Google release / OSM debug) with Standard / Satellite / Hybrid
- Offline basics and OSM hole merge from Room
- Reload hole GPS + bounded OSM refinement on location / hole change
- Search info cards (data sources + help map a course) and Play Billing tips
- Firebase Analytics events (search, course select, round start/end, hole, OSM, reload)
- Unit tests for GeoMath, parser, tee yardages, tee matcher, cache, and ViewModel

**Still to come:**

- Android Auto (separate branch)

## Map style

The map style preference on the round map is stored in **DataStore** and surfaced through a picker in the top-right corner of the hole map. Debug builds use OSM tiles so the map works on emulators without a Google Maps API key; release builds use Google Maps when Play Services and a valid key are available.

## Related

- Companion iOS app: [vctrch/GolfGps](https://github.com/vctrch/GolfGps)
