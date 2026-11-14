# P-Quest

Offline tactical map for Android: MGRS navigation, MIL-STD-2525D / APP-6D symbols, missions, TAK-compatible messaging
and LoRa mesh fallback. Requirements: [docs/REQUIREMENTS.pdf](docs/REQUIREMENTS.pdf). What is built, what is tested and
what is still unverified: [docs/REPORT.md](docs/REPORT.md).

## Build

Needs JDK 17+ and the Android SDK (platform 35, build-tools 35).

    ./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew :app:assembleRelease      # minified; signed with your key if the properties below are set

Release signing reads `pquestKeystore`, `pquestKeystorePassword`, `pquestKeyAlias` and `pquestKeyPassword` from
`~/.gradle/gradle.properties`; without them the release build is signed with the debug key. Every push also builds
both APKs in GitHub Actions. Library versions are pinned and the Gradle wrapper is committed, so the build can be
repeated from source; the bill of materials is [docs/sbom.json](docs/sbom.json) (`tools/make_sbom.py`).

## Modules

| Module | Contents |
| --- | --- |
| `coords` | Pure Kotlin: UTM, MGRS, lat/long, geodesic distance and bearing, grid convergence, line of sight, date-time group |
| `cot` | Pure Kotlin: Cursor-on-Target XML (positions, GeoChat, receipts, alerts, map items) and the compact LoRa encoding |
| `missions` | Pure Kotlin: last-writer-wins merge with conflict log, TAK data packages, QR links, KML/KMZ/GPX/GeoJSON/CSV/Shapefile, certificate request |
| `mesh` | Meshtastic client protocol, per-mission AES-GCM, Bluetooth LE radio link |
| `symbology` | mil-sym-android wrapper, the 49-symbol picker, tactical graphics, full-set search |
| `security` | PIN vault (PBKDF2 inside Android Keystore), duress profile, biometric unlock, wipe, SQLCipher |
| `map` | MBTiles / GeoPackage / GeoTIFF packs, the offline tile gateway, area download, elevation, slope, contours, tracks |
| `app` | The single map screen and its sheets, the CoT engine and links, the tour, English and Urdu strings |

Messaging and the tour live in `app` (`net/`, `Tour.kt`) rather than in modules of their own: their logic that can be
tested alone is already in `cot` and `missions`.

## Network behaviour

Out of the box the app makes no network requests. The map engine can only reach an in-app gateway that answers from
local storage; it contacts a tile server only when an admin switches online maps on and enters an `https://` source.
All other traffic goes through one CoT engine, to the unit's TAK Server (TLS, pinned to the unit CA), to the local
Wi-Fi multicast group, or to a paired LoRa radio. **GO DARK** closes all of it in one tap.

## Tools

- `server/` — Docker Compose and scripts for a unit TAK server, client certificates and enrolment QR codes.
- `tools/to_mbtiles.py` — converts scanned sheets, Sentinel-2 / Earth Engine exports and DEMs into MBTiles packs (needs GDAL).
- `tools/make_demo_pack.py` — builds the bundled demo map: a fictional island in open ocean, so no real terrain ships.
- `tools/gen_coord_vectors.py`, `tools/gen_test_tiffs.py` — regenerate test fixtures from independent libraries.
