# Community Golf for Android

A rangefinder in your pocket. Community Golf shows how far you are from the green on any hole, using free and open golf data — and lets golfers improve that data in-app.

**Package / applicationId:** `com.vctrch.golfgps`  
**Min / target SDK:** 26 / 36 · **Version:** 1.3

## What it does

- Search thousands of US courses by name or city
- Start a round and see live yardage to the green (and tee when mapped)
- Hole map with tee/green markers, Standard / Satellite / Hybrid styles, and mapping confidence
- Contribute updates via OpenGolf: sign in, mark tee/green/pin on the map, suggest course-fact corrections
- Android Auto dash yardage while a round is active
- Optional one-time tips via Google Play Billing

## Where the data comes from

- **OpenGolfAPI** — course search, scorecards, published tee yardages, course centers, contribution APIs
- **OpenStreetMap** (Overpass) — hole GPS wherever volunteers have mapped them

Both are [ODbL](https://opendatacommons.org/licenses/odbl/).

## Tech stack

| Area | Choice |
|------|--------|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | single-module app · Hilt · `ViewModel` + `StateFlow` + coroutines |
| Networking | Ktor + OkHttp · kotlinx.serialization |
| Local data | Room (`GolfGpsCache`) · DataStore preferences · EncryptedSharedPreferences (OpenGolf tokens) |
| Maps | Google Maps Compose (release) · osmdroid / OSM tiles (debug; no Maps key required) |
| Location | Play Services Fused Location |
| Auth / contribute | OpenGolf OAuth (PKCE + email OTP) · moments & corrections over HTTPS |
| Monetization | Play Billing Library (consumable in-app tips) |
| Car | Android Auto Car App Library (`PlaceListMapTemplate` POI) |
| Observability | Firebase Analytics · Firebase Crashlytics (opt-out in privacy card) |
| Quality / release | ktlint · detekt · R8 minify (release) · GitHub Actions CI |

### Project layout

```
app/src/main/kotlin/com/vctrch/golfgps/
  MainActivity.kt / GolfGpsApplication.kt / navigation/
  domain/          Models, GeoMath, mapping confidence, course helpers
  data/
    local/         Room cache + DataStore
    remote/        OpenGolf catalog (Ktor), Overpass/OSM parser + tee matcher
    opengolf/      Contribute auth (PKCE), secure token store, moments/corrections
    repository/    CourseRepository
    analytics/     Firebase event helpers
    billing/       Play Billing tips
  location/        LocationRepository + permission / precise-location status
  feature/
    search/        Course search + info / privacy / help-map cards
    round/         Active round, mapping UI, unavailable/retry, ViewModel
    map/           Google/OSM hole maps + place-mode contribute
    contribute/    Sign-in, terms, map marks, course corrections
    auto/          Android Auto (POI templates + ActiveRoundSession)
    support/       Developer tip card
```

## Features (current)

- Debounced OpenGolf course search; lazy course load with cached-round open + background refresh
- Cache validation; published tee yardages; orphan OSM tee matching; gap-fill for inferred holes
- Live yards-to-green (on-hole GPS) and yards-to-tee; GPS fix quality filtering
- Mapping confidence UI; location banners; round unavailable + retry
- Dual maps (Google release / OSM debug); map style in DataStore; OSM attribution
- OpenGolf contribute: email OTP sign-in, terms acceptance, place-mode marks, corrections, OSM links
- Android Auto idle + active-round templates
- Optional tips (`tip_small` / `tip_medium` / `tip_large`); usage/crash opt-out
- Unit tests for GeoMath, OSM parser, tee matching, cache validation, location filter, Auto session, OpenGolf contribute core, ViewModels

## OpenGolf contributions

Sign in with OpenGolf (email one-time code), accept terms when challenged, mark **tee / green / pin** on the hole map, or suggest course facts from **Course details & account**. Needs `OPENGOLF_API_KEY` in `local.properties` (same idea as iOS `OpenGolfAPIKey`).

## Tips (Google Play Billing)

Tips are **consumable one-time in-app products**. The app queries and consumes:

| Product ID | Suggested use |
|------------|----------------|
| `tip_small` | Small tip (e.g. $1.99) |
| `tip_medium` | Medium tip (e.g. $4.99) |
| `tip_large` | Large tip (e.g. $9.99) |

IDs must match exactly (`BillingRepository.TIP_PRODUCT_IDS`). Debug builds show placeholder buttons when Play Billing isn’t available; **release builds only show tips after Play returns real product details**.

### What you need to do in Play Console

1. **Merchant / payments** — Finish Play Console payments profile / merchant account so the app can sell digital goods in your countries.
2. **Create the products** — Monetize → Products → In-app products → create `tip_small`, `tip_medium`, `tip_large` as **Managed / one-time** products, mark them **consumable** (or ensure the app’s consume flow is allowed; this app always consumes after purchase so users can tip again).
3. **Activate** each product and set price / free trial N/A (one-time purchase).
4. **License testers** — Setup → License testing: add your Google accounts so internal/closed testers can buy without being charged (or use test card flows as Play documents).
5. **App must be on Play** — Billing product queries work for packages uploaded to Play (internal testing track is enough). A sideloaded release APK with a matching `applicationId` still needs the app listing + products on that package.
6. **Data safety / policy** — Declare that the app uses Google Play’s billing system for optional tips; keep privacy policy URL current (`PRIVACY_POLICY_URL` + Play Console Data safety).

No server-side receipt validation is implemented; tips are thank-you purchases only.

## Local setup

Copy [`local.properties.example`](local.properties.example) → `local.properties`:

| Key | Purpose |
|-----|---------|
| `MAPS_API_KEY` | Google Maps (release). Without it, release falls back to OSM. |
| `PRIVACY_POLICY_URL` | Public HTTPS policy for Play + in-app link. |
| `OPENGOLF_API_KEY` | Developer key for contribute writes. |
| `OPENGOLF_CLIENT_ID` / `OPENGOLF_REDIRECT_URI` | OAuth identity (defaults match iOS). |
| `RELEASE_STORE_*` | Optional upload keystore for signed release AABs. |

Also:

- Host [`docs/privacy-policy.md`](docs/privacy-policy.md) (or your own) and paste the URL into Play Data safety + `PRIVACY_POLICY_URL`.
- CI: ktlint, detekt, unit tests on `develop` / `main` PRs (`.github/workflows/ci.yml`).

### Map style and Android Auto

Map style lives in DataStore (picker on the hole map). Debug uses OSM tiles; release uses Google Maps when Play Services + a valid key are available.

Android Auto: start a round on the phone; the dash shows hole yardage, Previous/Next, optional tee pin. Test with the Desktop Head Unit (DHU).

## Agent contributors

Cursor agents open PRs as a dedicated GitHub user (not the repo owner). Setup: [`.cursor/agent-github-setup.md`](.cursor/agent-github-setup.md).

## Related

- Companion iOS app: [vctrch/GolfGps](https://github.com/vctrch/GolfGps)
