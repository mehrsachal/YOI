#!/usr/bin/env python3
"""Prints the P-Quest enrolment QR code for an official TAK Server that has certificate enrolment enabled (port 8446).

    ./enrol_qr.py <server> <username> <one-time-password> <unit-ca.pem>  [--png out.png]

The code carries the SHA-256 of the unit CA, so the device accepts only that server. Needs: pip install qrcode
FreeTAKServer has no certificate enrolment API: for it, use issue_client.sh and import the package instead."""
import base64, hashlib, re, sys, urllib.parse
args = [a for a in sys.argv[1:] if not a.startswith('--')]
if len(args) != 4: sys.exit(__doc__)
host, user, token, ca = args
pem = open(ca).read()
der = base64.b64decode(re.search(r'-----BEGIN CERTIFICATE-----(.+?)-----END CERTIFICATE-----', pem, re.S).group(1))
q = urllib.parse.quote
link = f"pquest://enroll?h={q(host)}&p=8089&e=8446&u={q(user)}&t={q(token)}&ca={hashlib.sha256(der).hexdigest()}"
print(link)
import qrcode
code = qrcode.QRCode(); code.add_data(link)
if '--png' in sys.argv: code.make_image().save(sys.argv[sys.argv.index('--png') + 1])
else: code.print_ascii()
