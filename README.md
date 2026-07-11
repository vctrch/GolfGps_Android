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
    auto/          Android Auto (POI templates + ActiveRoundSession)
    support/       Developer tip card
```

## Current status

**Working:**

- Debounced OpenGolf course search
- Lazy course load with instant cached-round open, then background refresh
- Course cache validation (rejects wrong-facility / drifted OSM geometry)
- OpenGolf published tee yardages merged into the scorecard
- Orphan OSM tee matching using published yardages (`TAGGED` / `MATCHED` / fairway sources)
- Gap-fill context through OSM load → merge (orphan greens/tees for inferred holes)
- Live yards-to-green (gated to on-hole GPS) and yards-to-tee when mapped
- GPS fix quality filtering (stale / inaccurate / regressive fixes)
- Mapping confidence badges, confidence-colored hole picker, Course mapping card
- Location banners (services off / permission / waiting GPS / precise location)
- Round unavailable screen with retry when course load fails
- OSM Overpass retry + hole parser merge / gap-fill pipeline
- Dual maps (Google release / OSM debug) with Standard / Satellite / Hybrid
- Unmapped-hole map loading overlay
- Offline basics and validated OSM hole merge from Room
- Reload hole GPS + bounded OSM refinement on location / hole change
- Search info cards (data sources + help map a course, including recent course)
- Play Billing tips and Firebase Analytics events
- Android Auto POI templates (idle + active round yardage / hole nav on the dash)
- Unit tests for GeoMath, parser, tee yardages, tee matcher, cache validation, location filter, Auto session, and ViewModel

## Map style and Android Auto

The map style preference on the round map is stored in **DataStore** and surfaced through a picker in the top-right corner of the hole map. Debug builds use OSM tiles so the map works on emulators without a Google Maps API key; release builds use Google Maps when Play Services and a valid key are available.

**Android Auto** uses the Car App Library POI category (`PlaceListMapTemplate`) — host-rendered map with green/tee markers and live yardage, similar in spirit to iOS CarPlay’s driving-task POI template. Start a round on the phone; the dash shows the current hole, Previous/Next, and optional tee pin. Test with the Desktop Head Unit (DHU).

## Related

- Companion iOS app: [vctrch/GolfGps](https://github.com/vctrch/GolfGps)
