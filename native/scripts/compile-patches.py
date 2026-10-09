#!/usr/bin/env python3
"""Compile Calma's own Kotlin patches offline against the generic Morphe API.

No third-party patch bundle or feature sources are downloaded or included.
The Kotlin compiler is the one already pinned by scripts/setup-kotlin.py.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'native'


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--morphe', type=Path, required=True)
    parser.add_argument('--source', type=Path, default=NATIVE / 'patches')
    parser.add_argument('--extension', type=Path,
                        help='Original Calma extension DEX to include as calma-extension.dex')
    parser.add_argument('--output', type=Path, default=NATIVE / 'build' / 'calma-patches.jar')
    args = parser.parse_args()
    args.morphe = args.morphe.resolve()
    args.output = args.output.resolve()
    lock = json.loads((NATIVE / 'tools.lock.json').read_text())
    actual = hashlib.sha256(args.morphe.read_bytes()).hexdigest()
    if actual != lock['morphe']['sha256']:
        raise SystemExit('Morphe tool SHA-256 does not match native/tools.lock.json')
    sources = sorted(args.source.rglob('*.kt'))
    if not sources:
        raise SystemExit(f'No original Kotlin patch sources in {args.source}')
    if not (ROOT / 'tools/kotlin/compiler.jar').is_file():
        raise SystemExit('Run python3 scripts/setup-kotlin.py first')
    extension_bytes = None
    if args.extension:
        extension_bytes = args.extension.read_bytes()
        if not extension_bytes.startswith(b'dex\n'):
            raise SystemExit('--extension must be a compiled DEX file')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='calma-patch-compile-') as tmp:
        classes = Path(tmp) / 'classes'
        classes.mkdir()
        command = [
            'java', '-cp', str(ROOT / 'tools/kotlin/*'),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-Xskip-metadata-version-check', '-no-stdlib', '-no-reflect',
            '-jvm-target', '17', '-classpath', str(args.morphe),
            '-d', str(classes), *map(str, sources),
        ]
        subprocess.run(command, check=True)
        target = Path(tmp) / 'patches.jar'
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            entries = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*') if p.is_file()}
            if extension_bytes is not None:
                entries['calma-extension.dex'] = extension_bytes
            for p in (ROOT / 'app/assets/updates').rglob('*'):
                if p.is_file():
                    data = p.read_bytes()
                    if p.name == 'updater.js':
                        stable = b'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/main/android-update.json'
                        native = b'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/native-instagram/native/android-update.json'
                        if data.count(stable) != 1:
                            raise SystemExit('Inhouse manifest configuration changed; review native channel binding')
                        # Channel configuration only: keep the original Inhouse updater logic.
                        data = data.replace(stable, native)
                    entries['calma-res/assets/updates/' + p.relative_to(ROOT / 'app/assets/updates').as_posix()] = data
            resources = NATIVE / 'resources'
            if resources.is_dir():
                for p in resources.rglob('*'):
                    if p.is_file():
                        entries['calma-res/' + p.relative_to(resources).as_posix()] = p.read_bytes()
            for name, data in sorted(entries.items()):
                info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        args.output.write_bytes(target.read_bytes())
    subprocess.run(['java', '-jar', str(args.morphe), 'list-patches', '--patches', str(args.output)], check=True)
    print(f'Compiled {len(sources)} original patch source(s): {args.output}')
    print(f'SHA-256: {hashlib.sha256(args.output.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
