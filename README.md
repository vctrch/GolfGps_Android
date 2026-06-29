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
- **State** — `ViewModel` exposing `StateFlow`.
- **Networking** — Retrofit + OkHttp, with kotlinx.serialization for JSON.
- **Maps** — Google Maps Compose in release builds; OpenStreetMap in debug builds (so you don't need an API key to test).
- **Location** — Google Play Services Fused Location.
- **Dependency injection** — Hilt.
- **Code quality** — ktlint and detekt.

### Saving data locally

The app keeps two kinds of local data so it works well offline and remembers your settings:

- **Room** (`GolfGpsCache`) caches course basics and the raw OSM hole JSON. This mirrors the iOS app's SwiftData `CourseDataCache`, and uses the same trick of storing JSON in a column (`scorecardJson`, `osmHolesJson`).
- **DataStore** (`golfgps_preferences`) holds user preferences such as the map display style — the equivalent of `@AppStorage` / `UserDefaults` on iOS.

### Project layout

The source is Kotlin-first under `app/src/main/kotlin/`, organized by layer and feature:

```
app/src/main/kotlin/com/vctrch/golfgps/
  MainActivity.kt              Single-activity entry point
  GolfGpsApplication.kt        Hilt application
  navigation/                  Root Compose tree (GolfGpsApp)
  domain/                      Models, GeoMath, course helpers
  data/
    local/                     Room cache + DataStore preferences
    remote/                    OpenGolfAPI Retrofit client
    repository/                CourseRepository
  location/                    LocationRepository
  di/                          Hilt modules
  ui/theme/                    Material 3 theme + golf palette
  feature/
    search/                    Course search screen
    round/                     Active round screen + RoundViewModel
    map/                       Google/OSM hole maps
```

Files that need several related types tend to use a package wildcard import, e.g. `import com.vctrch.golfgps.domain.*`.

## Getting started

You'll need **Android Studio Ladybug or newer** and **JDK 17**.

1. Create your local config by copying the example:
   ```properties
   # local.properties
   sdk.dir=/path/to/Android/sdk
   MAPS_API_KEY=your_google_maps_android_key
   ```
   On macOS, `sdk.dir` is usually `$HOME/Library/Android/sdk`.
2. Open the project in Android Studio (**File → Open**, then pick this repo's root).
3. Let Gradle sync, then run on an emulator or device (API 26+).
4. Grant location access when the app asks.

## Testing without a physical device

Good news: you don't need a real phone, and you don't even need a Google Maps key to try it out.

**Debug builds use OpenStreetMap tiles**, so the hole map works on any emulator with an internet connection — no API key or Play Services required.

- **Debug builds:** OSM map with tee/green markers. Works on a standard emulator.
- **Release builds:** Google Maps, which needs a valid `MAPS_API_KEY` (and a Play Store system image if you want to test it on an emulator).

### Faking your location for yardage

Yardage only updates once the app gets a location fix, so on an emulator you'll want to set a location near the course:

1. Start a course and open the active round.
2. In the emulator toolbar, go to **⋯ → Location** (Extended Controls → Location).
3. Enter coordinates near the course (or load a GPX route) and click **Set Location**.

The app also reads your last known location on startup.

### Using Google Maps in a debug build

If you specifically want Google Maps while debugging:

1. Use an AVD with the **Google Play** system image (not just "Google APIs").
2. Add your debug SHA-1 to the Maps API key in the Google Cloud Console.
3. Set `USE_OSM_MAP` to `false` in the `debug` build type in `app/build.gradle.kts`.

## Linting

```bash
./scripts/lint.sh
# or run the checks directly:
./gradlew ktlintCheck detekt test
```

## How far along is it (vs. the iOS app)

**Already working:**

- Course search via OpenGolfAPI (debounced as you type)
- Loading course basics with a Room offline cache
- Active round screen with yardage, hole picker, and a map showing tee/green markers
- The `GeoMath` distance logic ported over, with a unit test
- Golf theme colors matching the iOS `GolfTheme`
- Map display style picker backed by DataStore (mirrors the iOS `@AppStorage`)
- ktlint + detekt set up

**Still to come:**

- OSM Overpass + `OSMHoleParser` merge pipeline
- Google Play Billing tips are wired up in `SupportDeveloperCard`; on devices/emulators without the Play Store, debug builds show placeholder tips so the card still renders
- Android Auto (the equivalent of iOS CarPlay — a separate integration)
- Full unit-test parity with `GolfGpsTests/`

## A note on map style and CarPlay/Android Auto

The map style preference on the round map is stored in **DataStore** and surfaced through a picker in the top-right corner of the hole map. **Android Auto is not set up** in this project — the same practical trade-off the iOS app makes with CarPlay, which is limited to Apple's fixed POI map.

## Related projects

- iOS app: [vctrch/GolfGps](https://github.com/vctrch/GolfGps)
- App Store bundle / Play package: `com.vctrch.golfgps`
