#!/usr/bin/env bash
# Issues one client certificate signed by the unit CA and builds what a P-Quest (or ATAK) device imports:
#   out/<callsign>.zip  connection data package (import in P-Quest: Menu > Server > Import connection package)
#   out/<callsign>.txt  SHA-256 of the unit CA, for checking by eye
# The CA is created on first use in ./ca and never leaves this machine. Needs: openssl, zip.
set -euo pipefail
CALLSIGN="${1:?usage: issue_client.sh <callsign> [server-address] [port]}"
cd "$(dirname "$0")"
[ -f .env ] && . ./.env
SERVER="${2:-${SERVER_IP:?set SERVER_IP in .env or pass the server address}}"
PORT="${3:-8089}"
PASS="${P12_PASSWORD:-atakatak}"
mkdir -p ca out certs
if [ ! -f ca/ca.key ]; then
  openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes -keyout ca/ca.key -out ca/ca.pem -subj "/O=Unit/CN=Unit TAK CA"
  # Server certificate signed by the same CA; copied to ./certs for the server container.
  openssl req -newkey rsa:2048 -nodes -keyout certs/server.key -out ca/server.csr -subj "/O=Unit/CN=${SERVER}"
  openssl x509 -req -in ca/server.csr -CA ca/ca.pem -CAkey ca/ca.key -CAcreateserial -days 825 -sha256 -out certs/server.pem
  cp ca/ca.pem certs/ca.pem
  chmod 600 ca/ca.key certs/server.key
  echo "Created the unit CA and server certificate. Restart the server: docker compose restart"
fi
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
openssl req -newkey rsa:2048 -nodes -keyout "$WORK/client.key" -out "$WORK/client.csr" -subj "/O=Unit/CN=${CALLSIGN}"
openssl x509 -req -in "$WORK/client.csr" -CA ca/ca.pem -CAkey ca/ca.key -CAcreateserial -days 365 -sha256 -out "$WORK/client.pem"
# 3DES/SHA1 containers are the ones every Android version (and ATAK) can open.
P12="-keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg SHA1"
openssl pkcs12 -export $P12 -out "$WORK/${CALLSIGN}.p12" -inkey "$WORK/client.key" -in "$WORK/client.pem" -certfile ca/ca.pem -passout "pass:${PASS}" -name "$CALLSIGN"
openssl pkcs12 -export $P12 -nokeys -out "$WORK/truststore.p12" -in ca/ca.pem -passout "pass:${PASS}" -name ca
cat > "$WORK/config.pref" <<PREF
<?xml version='1.0' encoding='ASCII' standalone='yes'?>
<preferences>
  <preference version="1" name="cot_streams">
    <entry key="count" class="class java.lang.Integer">1</entry>
    <entry key="description0" class="class java.lang.String">Unit TAK Server</entry>
    <entry key="enabled0" class="class java.lang.Boolean">true</entry>
    <entry key="connectString0" class="class java.lang.String">${SERVER}:${PORT}:ssl</entry>
  </preference>
  <preference version="1" name="com.atakmap.app_preferences">
    <entry key="caLocation" class="class java.lang.String">cert/truststore.p12</entry>
    <entry key="caPassword" class="class java.lang.String">${PASS}</entry>
    <entry key="certificateLocation" class="class java.lang.String">cert/${CALLSIGN}.p12</entry>
    <entry key="clientPassword" class="class java.lang.String">${PASS}</entry>
    <entry key="locationCallsign" class="class java.lang.String">${CALLSIGN}</entry>
  </preference>
</preferences>
PREF
mkdir -p "$WORK/MANIFEST"
cat > "$WORK/MANIFEST/manifest.xml" <<MAN
<MissionPackageManifest version="2">
  <Configuration><Parameter name="uid" value="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)"/><Parameter name="name" value="${CALLSIGN} connection"/><Parameter name="onReceiveDelete" value="true"/></Configuration>
  <Contents><Content ignore="false" zipEntry="config.pref"/><Content ignore="false" zipEntry="truststore.p12"/><Content ignore="false" zipEntry="${CALLSIGN}.p12"/></Contents>
</MissionPackageManifest>
MAN
( cd "$WORK" && zip -q -r "${CALLSIGN}.zip" config.pref truststore.p12 "${CALLSIGN}.p12" MANIFEST )
cp "$WORK/${CALLSIGN}.zip" out/
openssl x509 -in ca/ca.pem -outform DER | openssl dgst -sha256 | awk '{print $NF}' > "out/${CALLSIGN}.txt"
echo "Wrote out/${CALLSIGN}.zip  (unit CA SHA-256: $(cat "out/${CALLSIGN}.txt"))"
