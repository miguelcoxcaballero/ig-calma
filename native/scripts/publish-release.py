#!/usr/bin/env python3
"""Publish an already-signed APK through the repository's GitHub Actions identity.

Upload credentials in the interactive environment may not cover GitHub's upload
host. Transport chunks on a temporary release branch contain only the exact public
APK. They are reassembled and checked against the signed build's update manifest.
No signing key, user token, source rebuilding or updater changes are involved.
"""
from __future__ import annotations

import hashlib
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def sha(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise SystemExit('Run this publication step in the repository workflow')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest-only', action='store_true', help='Update release metadata after verifying the existing APK digest')
    args = parser.parse_args()
    source = ROOT / 'native/release-input'
    manifest_path = ROOT / 'native/android-update.json'
    manifest = json.loads(manifest_path.read_text())
    assert manifest['required'] is True, 'Inhouse Read will suppress the popup when required is false'
    assert manifest == json.loads((ROOT / 'android-update.json').read_text()), 'Installed APK update channels disagree'
    version = manifest['version']
    assert re.fullmatch(r'\d+\.\d+\.\d+', version)
    repository = os.environ['GH_REPO']
    filename = f'IG-Calma-{version}.apk'
    assert manifest['apkUrl'] == f'https://github.com/{repository}/releases/download/v{version}/{filename}'
    if args.manifest_only:
        release = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repository}/releases/tags/v{version}'], text=True))
        matches = [asset for asset in release['assets'] if asset['name'] == filename]
        assert len(matches) == 1
        asset = matches[0]
        assert asset['browser_download_url'] == manifest['apkUrl']
        assert asset['size'] == manifest['apkSizeBytes']
        assert asset['digest'] == 'sha256:' + manifest['apkSha256']
        subprocess.run(['gh', 'release', 'upload', 'v' + version, str(manifest_path), '--clobber'], check=True)
        print('Release manifest synchronized; existing APK digest verified and binary preserved.')
        return
    transport = json.loads((source / 'transport.json').read_text())
    assert re.fullmatch(r'[0-9a-f]{40}', transport['sourceCommit'])
    target = ROOT / 'native/build/publish'
    target.mkdir(parents=True, exist_ok=True)
    apk = target / filename
    with apk.open('wb') as output:
        for index, part in enumerate(transport['parts']):
            assert part['name'] == f'{filename}.part{index:03d}'
            path = source / part['name']
            assert path.stat().st_size == part['bytes'] and sha(path) == part['sha256']
            with path.open('rb') as chunk:
                shutil.copyfileobj(chunk, output)
    assert apk.stat().st_size == manifest['apkSizeBytes']
    assert sha(apk) == manifest['apkSha256'], 'The reconstructed APK differs from the verified signed build'
    build = json.loads((source / 'build-info.json').read_text())
    assert build['apkSha256'] == manifest['apkSha256'] and build['calmaVersion'] == version
    sums = target / 'SHA256SUMS.txt'
    sums.write_text(f'{manifest["apkSha256"]}  {filename}\n')
    print(f'Exact signed APK verified: {filename}, {apk.stat().st_size} bytes, {manifest["apkSha256"]}', flush=True)
    tag = 'v' + version
    existing = subprocess.run(['gh', 'release', 'view', tag, '--json', 'isDraft'], text=True, capture_output=True)
    if existing.returncode == 0:
        if not json.loads(existing.stdout)['isDraft']:
            raise SystemExit('The release is already public; refusing to replace its APK')
    else:
        subprocess.run(['gh', 'release', 'create', tag, '--target', transport['sourceCommit'],
                        '--title', f'Instagram Calma {version} — prueba nativa',
                        '--notes-file', str(source / 'release-notes.txt'), '--draft'], check=True)
    subprocess.run(['gh', 'release', 'upload', tag, str(apk), str(sums), str(source / 'build-info.json'),
                    str(source / 'verification.json'), str(manifest_path), '--clobber'], check=True)
    subprocess.run(['gh', 'release', 'edit', tag, '--draft=false', '--prerelease=false', '--latest'], check=True)


if __name__ == '__main__':
    main()
