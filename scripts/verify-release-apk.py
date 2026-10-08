#!/usr/bin/env python3
"""Fail-closed gate on the signed APK, not its filename. SDK tools are the trust boundary."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--previous', type=Path, required=True, help='Previously published APK; upgrade certificate/version gate')
parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk'))))
parser.add_argument('--build-tools', default='35.0.0')
args = parser.parse_args()
tools = args.sdk / 'build-tools' / args.build_tools

def run(tool, *argv):
    try:
        return subprocess.check_output([str(tools / tool), *map(str, argv)], text=True, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as error:
        raise SystemExit(f'{tool} rejected the artifact: {error.output[-2000:]}') from None

def metadata(apk):
    badging = run('aapt', 'dump', 'badging', apk)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match:
        raise SystemExit('Missing APK package/version metadata')
    certificates = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', run('apksigner', 'verify', '--verbose', '--print-certs', apk))
    if not certificates:
        raise SystemExit('Unsigned/unverifiable APK')
    return match.group(1), int(match.group(2)), match.group(3), certificates, badging

package, code, version, certificates, badging = metadata(args.apk)
previous_package, previous_code, _, previous_certificates, _ = metadata(args.previous)
if package != 'ru.billyhargrove.pimobile' or package != previous_package:
    raise SystemExit('Wrong APK package')
if code <= previous_code:
    raise SystemExit('Release versionCode must increase')
if certificates != previous_certificates:
    raise SystemExit('Signing identity changed; cannot publish an in-place upgrade')
if 'application-debuggable' in badging:
    raise SystemExit('Refusing a debuggable/debug APK, including a renamed file')
# Release resource paths may be shortened by AGP; resolve the manifest's real XML resource.
manifest = run('aapt', 'dump', 'xmltree', args.apk, 'AndroidManifest.xml')
reference = re.search(r'android:networkSecurityConfig[^\n]*=@(0x[0-9a-f]+)', manifest)
resources = run('aapt2', 'dump', 'resources', args.apk)
resource = re.search(r'resource (0x[0-9a-f]+) xml/network_security_config\n\s+\(\) \(file\) (\S+) type=XML', resources)
if not reference or not resource or reference.group(1) != resource.group(1):
    raise SystemExit('Cannot resolve the actual APK network policy; refusing publication')
network = run('aapt', 'dump', 'xmltree', args.apk, resource.group(2))
values = re.findall(r'cleartextTrafficPermitted[^\n]*\(type 0x12\)(0x[0-9a-f]+)', network)
if not values or any(int(value, 16) != 0 for value in values) or 'domain-config' in network:
    raise SystemExit('Release TLS policy missing or debug cleartext overrides shipped')
run('zipalign', '-c', '-P', '16', '4', args.apk)
print(json.dumps({'package': package, 'versionName': version, 'versionCode': code,
                  'debuggable': False, 'cleartext': False, 'certificateSha256': certificates,
                  'bytes': args.apk.stat().st_size, 'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest()}, indent=2))
