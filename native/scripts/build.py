#!/usr/bin/env python3
"""Verify stock input, apply Calma's own patches, sign, and generate its update manifest.

No upstream patch bundle is used. The signing key must already exist; this script
never generates a replacement key that would break installed Calma updates.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'native'


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(*args: str | Path, capture: bool = False, env: dict | None = None) -> str:
    result = subprocess.run(list(map(str, args)), cwd=ROOT, check=True,
                            text=True, stdout=subprocess.PIPE if capture else None,
                            env=env)
    return result.stdout or ''


def finalize(apk: Path, build_tools: Path, morphe: Path) -> None:
    """Recheck an existing signed build before preparing its release files."""
    stock = json.loads((NATIVE / 'stock.lock.json').read_text())
    config = (NATIVE / 'src/es/calma/instagram/nativeapp/CalmaBuild.java').read_text()
    version = re.search(r'VERSION = "([^"]+)"', config).group(1)
    version_code = int(re.search(r'VERSION_CODE = (\d+)', config).group(1))
    build = NATIVE / 'build'
    split_dir = build / 'verified-splits'
    bundle = build / 'calma-patches.jar'
    dist = apk.parent
    assert apk.name == f'IG-Calma-{version}.apk'
    run('python3', NATIVE / 'scripts/verify-apk.py', '--apk', apk,
        '--stock', split_dir / 'base.apk', '--build-tools', build_tools)
    for check in ('verify_feed_hooks.py', 'reels_dex.py', 'discover_dex.py', 'hook_dex_analysis.py'):
        run('python3', NATIVE / 'tests' / check, apk, '--morphe', morphe)
    run('python3', NATIVE / 'tests/updater.py', '--apk', apk)
    manifest = {
        # Inhouse Read treats false as "do not offer", not an optional popup.
        'version': version, 'versionCode': version_code, 'required': True,
        'apkUrl': f'https://github.com/miguelcoxcaballero/ig-calma/releases/download/v{version}/{apk.name}',
        'apkSha256': digest(apk), 'apkSizeBytes': apk.stat().st_size,
        'releaseNotes': 'Corrige el empaquetado de arranque y sincroniza los archivos que usa el cargador de Instagram.',
    }
    payload = json.dumps(manifest, indent=2, ensure_ascii=False) + '\n'
    for destination in (ROOT / 'android-update.json', NATIVE / 'android-update.json'):
        destination.write_text(payload)
    (dist / 'SHA256SUMS.txt').write_text(f'{manifest["apkSha256"]}  {apk.name}\n')
    (dist / 'build-info.json').write_text(json.dumps({
        'calmaVersion': version, 'calmaVersionCode': version_code,
        'stock': stock, 'patchBundleSha256': digest(bundle),
        'extensionSha256': digest(build / 'classes.dex'), 'apkSha256': manifest['apkSha256'],
        'deviceTesting': 'Not performed: no Android device or Instagram session available',
    }, indent=2) + '\n')
    print(f'Verified signed APK: {apk}')
    print(f'Update manifest: {NATIVE / "android-update.json"}')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stock-apkm', type=Path, required=True)
    parser.add_argument('--morphe', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True, help='Android SDK build-tools 36.0.0 or newer')
    parser.add_argument('--merged', type=Path, help='Optional already-verified merged input matching stock.lock.json')
    parser.add_argument('--keystore', type=Path, default=ROOT / 'build/local-signing.p12')
    args = parser.parse_args()
    for key, value in vars(args).items():
        if isinstance(value, Path):
            setattr(args, key, value.resolve())
    stock = json.loads((NATIVE / 'stock.lock.json').read_text())
    tool = json.loads((NATIVE / 'tools.lock.json').read_text())['morphe']
    if digest(args.stock_apkm) != stock['apkmSha256']:
        raise SystemExit('Stock APKM hash does not match the verified source')
    if digest(args.morphe) != tool['sha256']:
        raise SystemExit('Generic Morphe tool hash does not match the pinned tool')
    if not args.keystore.is_file():
        raise SystemExit('Existing Calma signing key required; no new key will be created')
    config = (NATIVE / 'src/es/calma/instagram/nativeapp/CalmaBuild.java').read_text()
    version = re.search(r'VERSION = "([^"]+)"', config).group(1)
    version_code = int(re.search(r'VERSION_CODE = (\d+)', config).group(1))
    build = NATIVE / 'build'
    split_dir = build / 'verified-splits'
    split_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.stock_apkm) as archive:
        for filename, expected in stock['splits'].items():
            data = archive.read(filename)
            if hashlib.sha256(data).hexdigest() != expected:
                raise SystemExit(f'Unrecognized stock split: {filename}')
            target = split_dir / filename
            target.write_bytes(data)
            verification = run(args.build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', target, capture=True)
            if not all(cert in verification for cert in stock['signerSha256'].values()):
                raise SystemExit(f'Stock signer lineage mismatch: {filename}')
    if args.merged:
        if digest(args.merged) != stock['verifiedMergedSha256']:
            raise SystemExit('Merged stock input differs from the verified input')
        merged = args.merged
    else:
        merger = build / 'merge-splits.jar'
        run('java', '-cp', ROOT / 'tools/kotlin/*', 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-Xskip-metadata-version-check', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
            '-classpath', args.morphe, '-d', merger, NATIVE / 'tools/MergeSplits.kt')
        merged = build / 'verified-stock-merged.apk'
        run('java', '-cp', f'{merger}:{args.morphe}', 'es.calma.tools.MergeSplitsKt', split_dir, merged)
    run('python3', NATIVE / 'scripts/compile-extension.py', '--stock', split_dir / 'base.apk')
    bundle = build / 'calma-patches.jar'
    run('python3', NATIVE / 'scripts/compile-patches.py', '--morphe', args.morphe,
        '--extension', build / 'classes.dex', '--output', bundle)
    run('java', '-jar', ROOT / 'tools/ecj.jar', '-source', '17', '-target', '17', '-proc:none',
        '-classpath', args.morphe, '-d', build / 'tools',
        NATIVE / 'tools/DexSubset.java', NATIVE / 'tools/DexInspect.java')
    unsigned = build / 'patched.apk'
    unsigned.unlink(missing_ok=True)
    run('java', '-Xmx8g', '-jar', args.morphe, 'patch', '--patches', bundle,
        '--bytecode-mode', 'FULL', '--unsigned', '--result-file', build / 'patch-result.json',
        '--out', unsigned, '--temporary-files-path', build / 'release-work', merged)
    if not unsigned.is_file():
        raise SystemExit('Patch process did not produce an APK')
    repaired = build / 'layout-restored.apk'
    run('python3', NATIVE / 'scripts/repair-dex-layout.py', '--stock', split_dir / 'base.apk',
        '--input', unsigned, '--output', repaired, '--morphe', args.morphe)
    aligned = build / 'aligned.apk'
    run(args.build_tools / 'zipalign', '-f', '-P', '16', '4', repaired, aligned)
    dist = NATIVE / 'build/dist'
    dist.mkdir(exist_ok=True)
    apk = dist / f'IG-Calma-{version}.apk'
    signing_env = os.environ.copy()
    signing_env.setdefault('CALMA_KEYSTORE_PASSWORD', 'localbuild')
    run(args.build_tools / 'apksigner', 'sign', '--ks', args.keystore, '--ks-key-alias', 'calma',
        '--ks-pass', 'env:CALMA_KEYSTORE_PASSWORD', '--key-pass', 'env:CALMA_KEYSTORE_PASSWORD',
        '--out', apk, aligned, env=signing_env)
    finalize(apk, args.build_tools, args.morphe)


if __name__ == '__main__':
    main()
