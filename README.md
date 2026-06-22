# GolfGps (Android)

Kotlin + Jetpack Compose port of the [GolfGps](https://github.com/vctrch/GolfGps) iOS app for Android.

| Source | What you get | License |
|--------|----------------|---------|
| [OpenGolfAPI](https://opengolfapi.org) | Course search, scorecard, course center | ODbL |
| [OpenStreetMap](https://www.openstreetmap.org) (Overpass) | Hole GPS where mappers added it | ODbL |

**Package:** `com.vctrch.golfgps` · **Version:** 1.3 (aligned with iOS)

## Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose, Material 3 |
| State | ViewModel + `StateFlow` |
| HTTP | Retrofit + OkHttp + kotlinx.serialization |
| Local storage | **Room** (course cache) + **DataStore** (user prefs, e.g. map style) |
| Location | Google Play Services Fused Location |
| Maps | Google Maps Compose (release) · **OpenStreetMap** (debug / emulator) |
| DI | Hilt |
| Lint | **ktlint** + **detekt** |

## Project layout

Kotlin-first source under `app/src/main/kotlin/`, organized by layer and feature:

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

Typical imports use package wildcards where a file needs several related types, e.g. `import com.vctrch.golfgps.domain.*`.

## Local storage

| Store | Purpose | iOS equivalent |
|-------|---------|----------------|
| **Room** `GolfGpsCache` | Offline course basics + OSM hole JSON blobs | SwiftData `CourseDataCache` |
| **DataStore** `golfgps_preferences` | Map display style and future settings | `@AppStorage` / UserDefaults |

Room uses the same JSON-in-column strategy as iOS (`scorecardJson`, `osmHolesJson`).

## Setup

1. Install Android Studio Ladybug+ and JDK 17.
2. Copy `local.properties.example` → `local.properties`:
   ```properties
   sdk.dir=/path/to/Android/sdk
   MAPS_API_KEY=your_google_maps_android_key
   ```
   On macOS, `sdk.dir` is often `$HOME/Library/Android/sdk`.
3. Open the project in Android Studio (**File → Open**, select this repo root).
4. Sync Gradle, run on emulator or device (API 26+).
5. Grant location when prompted.

## Emulator testing (no physical device)

**Debug builds use OpenStreetMap tiles** — no Google Maps API key or Play Services required for the hole map. Run the **debug** variant on any emulator with internet access.

### Maps

- **Debug:** OSM map with tee/green markers (works on standard emulators).
- **Release:** Google Maps (requires a valid `MAPS_API_KEY` and a Play Store system image if testing on emulator).

### GPS / yardage on emulator

1. Start a course and open the active round.
2. In the emulator toolbar: **⋯ → Location** (or Extended Controls → Location).
3. Set coordinates near the course, or load a GPX route, then click **Set Location**.

Yardage updates once the app receives a location fix. The app also reads the last known location on startup.

### If you want Google Maps in debug

Use an AVD with the **Google Play** system image (not “Google APIs” only), add your debug SHA-1 to the Maps API key in Google Cloud Console, and set `USE_OSM_MAP` to `false` in the `debug` build type in `app/build.gradle.kts`.

## Lint

```bash
./scripts/lint.sh
# or
./gradlew ktlintCheck detekt test
```

## MVP status vs iOS

**Included in this scaffold:**
- Course search (OpenGolfAPI, debounced)
- Load course basics + Room offline cache
- Active round UI with yardage, hole picker, Google Map (tee/green markers)
- GeoMath port + unit test
- Golf theme colors matching iOS `GolfTheme`
- Map display style picker (DataStore-backed, mirrors iOS `@AppStorage`)
- ktlint + detekt

**Not yet ported (follow-up):**
- OSM Overpass + `OSMHoleParser` merge pipeline
- Google Play Billing tips (`SupportDeveloperCard` is a placeholder)
- Android Auto (iOS CarPlay equivalent — separate integration)
- Full unit test parity with `GolfGpsTests/`

## CarPlay / map style note

Map style preference on the round map is stored in **DataStore** and exposed via a top-right picker on the hole map. **Android Auto is not configured** in this project (same practical split as iOS CarPlay using Apple's fixed POI map).

## Related

- iOS app: [vctrch/GolfGps](https://github.com/vctrch/GolfGps)
- Android app: [vctrch/GolfGps_Android](https://github.com/vctrch/GolfGps_Android)
- App Store bundle / Play package: `com.vctrch.golfgps`
