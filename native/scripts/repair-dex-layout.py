#!/usr/bin/env python3
"""Keep stock DEX membership and regenerate the loader manifests for our APK."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
METADATA = 'assets/secondary-program-dex-jars/metadata.txt'
MANIFEST = 'assets/secondary-program-dex-jars/dex_manifest.txt'
PROFILES = {'assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'}


def ordinal(name: str) -> int:
    return 0 if name == 'classes.dex' else int(name[7:-4]) - 1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stock', type=Path, required=True)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--morphe', type=Path, required=True)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve(), 'Use a separate output to preserve the source on failure'
    with tempfile.TemporaryDirectory(prefix='calma-dex-layout-') as temporary:
        directory = Path(temporary)
        compiled = directory / 'tools'
        compiled.mkdir()
        dexes = directory / 'dexes'
        dexes.mkdir()
        subprocess.run(['java', '-jar', str(ROOT / 'tools/ecj.jar'), '-source', '17', '-target', '17', '-proc:none',
                        '-classpath', str(args.morphe), '-d', str(compiled),
                        str(ROOT / 'native/tools/RestoreDexLayout.java')], check=True)
        subprocess.run(['java', '-Xmx8g', '-cp', f'{compiled}:{args.morphe}', 'es.calma.tools.RestoreDexLayout',
                        str(args.stock), str(args.input), str(dexes)], check=True)
        entries = sorted(dexes.glob('classes*.dex'), key=lambda path: ordinal(path.name))
        metadata = ['.root_relative']
        for entry in entries[1:]:
            digest = hashlib.sha1(entry.read_bytes()).hexdigest()
            metadata.append(f'{entry.name} {digest} secondary.dex{ordinal(entry.name):02d}.Canary')
        with zipfile.ZipFile(args.stock) as stock:
            manifest = stock.read(MANIFEST).decode().splitlines()
        assert len(manifest) == len(entries) - 1, 'Unexpected stock DEX manifest'
        manifest.append(f'Lsecondary/dex{len(entries) - 1:02d}/Canary;,ordinal={len(entries) - 1},coldstart=1,extended=0,primary=0,scroll=0,background=0')
        with zipfile.ZipFile(args.input) as source, zipfile.ZipFile(args.output, 'w') as output:
            for item in source.infolist():
                if re.fullmatch(r'classes\d*\.dex', item.filename) or item.filename in {METADATA, MANIFEST} | PROFILES:
                    continue
                output.writestr(item, source.read(item))
            for entry in entries:
                output.write(entry, entry.name, compress_type=zipfile.ZIP_DEFLATED)
            output.writestr(METADATA, '\n'.join(metadata) + '\n', compress_type=zipfile.ZIP_DEFLATED)
            output.writestr(MANIFEST, '\n'.join(manifest) + '\n', compress_type=zipfile.ZIP_DEFLATED)
        # Stock profiles encode the old DEX checksums and method indices. Android
        # must generate profiles for this APK rather than install that stale data.
        subprocess.run(['python3', str(ROOT / 'native/tests/dex_layout.py'), str(args.output),
                        '--stock', str(args.stock)], check=True)


if __name__ == '__main__':
    main()
