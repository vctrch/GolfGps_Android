# GolfGps (Android)

Kotlin + Jetpack Compose port of [GolfGps](../GolfGps) for Android.

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
| Maps | Google Maps Compose |
| DI | Hilt |
| Lint | **ktlint** + **detekt** |

## Project layout

```
app/src/main/java/com/vctrch/golfgps/
  data/local/       Room (GolfGpsCache) + DataStore preferences
  data/remote/      OpenGolfAPI Retrofit client
  data/repository/  CourseRepository
  domain/           GeoMath, models, course helpers
  location/         LocationRepository
  ui/search/        Course search screen
  ui/round/         Active round + map
  viewmodel/        RoundViewModel
```

Mirrors the iOS app architecture (`OpenGolfAPI → cache → RoundViewModel + location → UI`).

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
   sdk.dir=/Users/you/Library/Android/sdk
   MAPS_API_KEY=your_google_maps_android_key
   ```
3. Open `/Users/john/Workbench/GolfGps_Android` in Android Studio.
4. Sync Gradle, run on emulator or device (API 26+).
5. Grant location when prompted.

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

- iOS repo: `/Users/john/Workbench/GolfGps`
- App Store bundle / Play package: `com.vctrch.golfgps`
