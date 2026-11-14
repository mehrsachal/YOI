# Unit TAK server kit

Stands up a TAK server for P-Quest (and ATAK / WinTAK / iTAK) on a unit laptop or a Raspberry Pi 5, and issues
what each device needs to connect. **Not yet run against a live server: treat as a starting point and test before a field trial.**

## Bring the server up

    cp .env.example .env        # set SERVER_IP to this machine's address on the unit network
    ./issue_client.sh ALPHA-1   # first run also creates the unit CA (./ca) and the server certificate (./certs)
    docker compose up -d

Needs Docker, `openssl` and `zip`. The compose file uses the FreeTAKServer image; check its documentation for the
certificate file names your image version expects under `/opt/fts/certs` and rename the files in `./certs` to match.
For the official TAK Server, load the unit CA and server certificate from `./ca` and `./certs` the same way.

## Issue a device

    ./issue_client.sh BRAVO-2

writes `out/BRAVO-2.zip`. Copy it to the phone (USB or SD card) and import it in P-Quest with
**Menu → Server → Import connection package**. The user never handles certificates. The same file imports into ATAK.

`ca/ca.key` is the unit's root of trust: keep this machine physically controlled, and never copy that file off it.

## Enrolment by QR code (official TAK Server only)

With certificate enrolment enabled on port 8446:

    pip install qrcode
    ./enrol_qr.py 192.168.10.1 alpha-1 <one-time-password> ca/ca.pem --png alpha-1.png

The user scans it with **Menu → Server → Scan enrolment QR**. The code carries the CA fingerprint, so the phone
only talks to a server signed by the unit CA. FreeTAKServer has no enrolment API; use connection packages for it.

## Ports

| Port | Use |
| --- | --- |
| 8089/tcp | CoT over TLS with client certificates (all P-Quest needs) |
| 8443/tcp | Mission / Data Sync API over TLS |
| 8446/tcp | Certificate enrolment (official TAK Server only) |
