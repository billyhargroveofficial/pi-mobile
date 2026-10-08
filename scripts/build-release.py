#!/usr/bin/env python3
"""Build/sign only release. Signing material lives in a private local JSON, never Gradle/Git/APK."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--previous', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--signing', type=Path, default=Path.home() / '.config/pi-mobile/release-signing.json')
args = parser.parse_args()
if args.signing.stat().st_mode & 0o077:
    raise SystemExit('Signing config must be private (0600)')
config = json.loads(args.signing.read_text())
keystore = Path(config['keystore'])
if keystore.stat().st_mode & 0o077:
    raise SystemExit('Keystore must be private (0600)')
env = os.environ.copy()
sdk = Path(env.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
if not env.get('JAVA_HOME'):
    raise SystemExit('Set JAVA_HOME to JDK17 before building')
subprocess.run(['./gradlew', 'testReleaseUnitTest', 'assembleRelease', 'assembleReleaseAndroidTest', 'lintRelease',
                '-PpiTestBuildType=release', '-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=768m -XX:+UseG1GC -Dfile.encoding=UTF-8'],
               cwd=ROOT / 'android', env=env, check=True)
folder = ROOT / 'android/app/build/outputs/apk/release'
metadata = json.loads((folder / 'output-metadata.json').read_text())
if metadata.get('variantName') != 'release' or len(metadata['elements']) != 1:
    raise SystemExit('Wrong/multiple variant outputs; refusing publication')
element = metadata['elements'][0]
unsigned = folder / element['outputFile']
args.output.mkdir(parents=True, exist_ok=True)
output = args.output / f"Pi-Mobile-{element['versionName']}.apk"
tools = sdk / 'build-tools/35.0.0'
env['PI_SIGN_STORE_PASSWORD'] = config['storePassword']
env['PI_SIGN_KEY_PASSWORD'] = config['keyPassword']
with tempfile.TemporaryDirectory(prefix='pi-release-') as directory:
    aligned = Path(directory) / 'release-aligned.apk'
    # AGP leaves both release and release instrumentation unsigned without a Gradle signingConfig.
    # Only the app APK is publishable; the separately signed test APK stays in local evidence.
    test_source = ROOT / 'android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk'
    for source, destination in [(unsigned, output), (test_source, args.output / 'release-androidTest.apk')]:
        subprocess.run([str(tools / 'zipalign'), '-f', '-P', '16', '4', str(source), str(aligned)], check=True)
        subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(keystore), '--ks-key-alias', config['alias'],
                        '--ks-pass', 'env:PI_SIGN_STORE_PASSWORD', '--key-pass', 'env:PI_SIGN_KEY_PASSWORD',
                        '--out', str(destination), str(aligned)], env=env, check=True)
# This checks actual signatures/version/manifest/TLS/16K alignment, including a renamed-debug rejection.
subprocess.run(['python3', str(ROOT / 'scripts/verify-release-apk.py'), str(output),
                '--previous', str(args.previous), '--sdk', str(sdk)], env=env, check=True)
print(f'Release APK verified: {output}')
