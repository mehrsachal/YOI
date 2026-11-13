# P-Quest

Offline tactical map for Android (working name in the brief: SAATHI). Requirements: [docs/REQUIREMENTS.pdf](docs/REQUIREMENTS.pdf).
Built phase by phase; the current state and test results are in [docs/PHASE1_REPORT.md](docs/PHASE1_REPORT.md).

## Build

Needs JDK 17+ and the Android SDK (platform 35, build-tools 35).

    ./gradlew :coords:test :map:testDebugUnitTest :app:assembleDebug

The APK is written to `app/build/outputs/apk/debug/`. Every push also builds it in GitHub Actions (artifact `p-quest-debug-apk`).

## Modules

| Module | Contents |
| --- | --- |
| `coords` | Pure Kotlin: UTM, MGRS, lat/long parsing and formatting, geodesic distance and bearing, date-time group |
| `security` | Android Keystore-wrapped key and SQLCipher database |
| `map` | Tile storage (MBTiles, GeoPackage), the offline tile gateway, area download, elevation, track store, map style |
| `app` | The single map screen, bottom sheets, MGRS grid overlay, track recording service, English and Urdu strings |

Later phases add `symbology`, `cot`, `missions`, `messaging`, `mesh` and `tour`.

## Network behaviour

The map engine can only talk to an in-app gateway (`map/.../MapHub.kt`). The gateway answers from local storage and
contacts a server only when online maps are switched on in Settings and an `https://` tile URL has been entered.
Out of the box the app makes no network requests.

## Tools

- `tools/make_demo_pack.py` builds the bundled demo map: a fictional island in open ocean, so no real terrain is shipped.
- `tools/gen_coord_vectors.py` regenerates the coordinate test vectors from independent libraries (`mgrs`, `pyproj`).
