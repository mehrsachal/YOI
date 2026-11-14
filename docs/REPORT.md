# P-Quest 1.0.0 — build and test report

All features in the requirements were built in one pass, as instructed (no phase gates). This report says what was
verified, how, and what could not be verified here.

## The one thing to know first

**The app has never run on a phone or emulator.** The build machine has no Android device and cannot run an emulator.
It compiles, and every piece of logic that can run off-device is tested, but the screens, map rendering, GPS,
Bluetooth, the encrypted database and the Keystore have only ever been compiled. Expect first-run defects; the first
job is an install on a test phone.

## Automated tests: 73, all passing

| Module | Tests | What they prove |
| --- | --- | --- |
| coords | 11 | UTM within 1 mm of pyproj and MGRS identical to the `mgrs` library on 54 points world-wide; go-to within 1 m; all formats parse; Vincenty against a published geodetic line; line of sight; grid convergence; off-route distance |
| cot | 12 | Position, GeoChat, receipt, alert and map-item CoT round-trip; real ATAK-format messages decode; malformed and hostile XML is rejected; LoRa packets stay under 200 bytes including encryption |
| missions | 18 | Last-writer-wins merge and conflict log (8 cases); data package and connection package; QR links; KML/KMZ, GPX, GeoJSON, CSV, Shapefile; the certificate request (also accepted by `openssl req -verify`) |
| mesh | 6 | Outgoing frames byte-identical to the official Meshtastic protobuf encoding; official frames decode; per-mission encryption rejects tampering and wrong keys |
| security | 2 | PIN wrapping: right PIN opens, wrong PIN or altered data does not |
| symbology | 6 | Run through the real mil-sym renderer: every picker symbol draws, hostile is red and friendly blue, every tactical graphic renders, full-set search works |
| map | 11 | Run on real SQLite and bitmaps: pack import, the tile gateway (refuses any non-local URL), elevation, slope and contour tiles, offline demo download, GeoTIFF reading and tiling; the generated style passes the MapLibre style validator |
| app | 7 | QR codes drawn and read back; quick-reference card content in both languages; **the TAK link completes a mutual-TLS handshake with a local TLS server using a package from `server/issue_client.sh`, exchanges CoT both ways, and refuses a server not signed by the unit CA** |

## Acceptance checks from the requirements

None of the five phase checks can be signed off, because each needs phones, a TAK server or radios.

| Check | Status |
| --- | --- |
| 1. Download 10 km, reboot in airplane mode, no blank tiles; go-to MGRS within 1 m | Go-to accuracy proven by test. Download and offline serving proven off-device with the demo source. Not run on a phone. |
| 2. 20 symbols and a phase line exported to KMZ open correctly in Google Earth and ATAK | KMZ and data package round-trip in tests. Not opened in Google Earth or ATAK. |
| 3. Three phones and ATAK on FreeTAKServer see each other in 15 s; offline edit syncs | TLS link and merge logic proven. Not run against FreeTAKServer or ATAK. |
| 4. Two phones with LoRa radios exchange position and text at 2 km | Frame format matches the official encoding. No radio was available. |
| 5. Tour under 7 minutes; security checklist; no outbound traffic in a packet capture | Not run. By construction nothing is sent with online maps off, but no capture was taken. |

Screenshots: none, for the same reason.

## Not verified at all

- Everything on screen, including Urdu right-to-left layout and the Nastaliq font.
- TAK Server Mission API calls and certificate enrolment (written from the published API, never run against a server).
- `server/docker-compose.yml` (the image tag, environment names and certificate file names need checking against the FreeTAKServer version used).
- Bluetooth pairing and traffic with a real Meshtastic radio.
- Battery use (target 5 % per hour) and 72-hour offline operation.
- `tools/to_mbtiles.py` (GDAL was not available to run it).
- The minified release APK. The debug APK is the one to test first.

## Where the build differs from the requirements

| Requirement | What was built | Why |
| --- | --- | --- |
| Room on SQLCipher | SQLCipher used directly | Six small tables; avoids an annotation processor in the build |
| Scan QR codes | The camera app takes a photo, which is then decoded | No camera permission and no extra library; a live scanner can replace it |
| Attachments: photo and map snapshot | Map picture only, over TAK Server only | Server-less transfer between phones needs plain HTTP, which the security rules forbid; photo capture not built |
| Mesh relays CoT | Positions, text, receipts and alerts go over mesh; map items do not | Items do not fit the 200-byte packet reliably |
| Custom icon slot | Unit signs are labelled badges (PQT, POST, NX by default), editable by admin | The mandatory conventional signs are still an open question |
| Mission roles | Owner, editor, viewer are enforced in the app | A modified client could ignore them; TAK Server roles are not read |
| Draw a box to download | Tap two opposite corners | A dragged box needs precision that gloves do not allow |
| Drag to move | Long-press, Move, then drag anywhere | Same reason: the finger need not hit the symbol |
| Go dark stops mesh beacons | The app stops transmitting and disconnects the radio | The radio's own beacons stop only when the radio is switched off |
| Grid on Survey of Pakistan sheets | Not built | Waiting for the projection definition; sheets can be converted with `tools/to_mbtiles.py` |
| App lock after 5 minutes | Lock screen returns; tracking and reporting continue behind it | Position reports must not stop when the screen locks |
| Separate `messaging` and `tour` modules | Packages inside `app` | Their testable logic is in `cot` and `missions` |

Other choices made where the document was silent: map is north-up with rotation off; distances are metric; the PIN is
mandatory and ten wrong attempts wipe the app; the off-route alert is fixed at 50 m; GeoTIFF import handles 8-bit
images in lat/long, web mercator or UTM (others go through the converter); DEM tiles use Terrarium encoding; the
hidden wipe gesture is a 3-second hold on the title of the lock screen; the demo map is a fictional island so no real
ground appears anywhere in the app, tests or documents.

## Open questions

1. Does "Messi" in the brief mean messaging plus mesh, as assumed?
2. Which conventional signs are mandatory, and what do they look like?
3. Which sheet series and projection should the grid support?
4. Which LoRa radios, frequencies and channel settings are approved?
5. TAK Server or FreeTAKServer? Enrolment by QR works only with the official server.
6. Which map and imagery sources are licensed for offline military use? None is configured by default.
7. Is wiping after ten wrong PINs wanted, or a lock-out instead?
8. Who holds the release signing key?

## Bill of materials

`docs/sbom.json` lists the 102 libraries in the release APK. All are Apache-2.0 or BSD; nine have no licence in their
published metadata and should be confirmed by hand (OkHttp, Okio, Kotlin stdlib, coroutines and annotations are
Apache-2.0; SQLCipher is BSD-style; AndroidSVG is Apache-2.0). The bundled Urdu font is Noto Nastaliq Urdu (OFL 1.1).
There is no Google Play Services, Firebase, analytics, crash-reporting or advertising component.
