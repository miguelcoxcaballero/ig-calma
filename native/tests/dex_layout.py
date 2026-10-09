#!/usr/bin/env python3
"""Reject the stale DEX startup metadata shipped in native 0.4.0."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('extension', ROOT / 'native/scripts/compile-extension.py')
extension = importlib.util.module_from_spec(spec)
spec.loader.exec_module(extension)


def ordinal(name: str) -> int:
    return 0 if name == 'classes.dex' else int(name[7:-4]) - 1


def verify(apk_path: Path, stock_path: Path) -> dict:
    with zipfile.ZipFile(apk_path) as apk, zipfile.ZipFile(stock_path) as stock:
        stock_names = sorted((name for name in stock.namelist() if re.fullmatch(r'classes\d*\.dex', name)), key=ordinal)
        apk_names = sorted((name for name in apk.namelist() if re.fullmatch(r'classes\d*\.dex', name)), key=ordinal)
        assert apk_names == stock_names + [f'classes{len(stock_names) + 1}.dex'], 'Original DEX partition was changed'
        for name in stock_names:
            expected = extension.dex_classes(stock.read(name))
            actual = extension.dex_classes(apk.read(name))
            assert expected == actual, f'Stock class membership changed in {name}'
        metadata = apk.read('assets/secondary-program-dex-jars/metadata.txt').decode().splitlines()
        assert metadata.pop(0) == '.root_relative'
        assert len(metadata) == len(apk_names) - 1, 'Stale DEX metadata count'
        for name, line in zip(apk_names[1:], metadata):
            filename, digest, canary = line.split(' ')
            assert filename == name, f'Missing or reordered DEX file: {filename}'
            data = apk.read(name)
            assert hashlib.sha1(data).hexdigest() == digest, f'Stale DEX hash: {name}'
            assert canary == f'secondary.dex{ordinal(name):02d}.Canary'
            descriptor = 'L' + canary.replace('.', '/') + ';'
            assert descriptor in extension.dex_classes(data), f'Canary points to the wrong DEX: {name}'
        manifest = apk.read('assets/secondary-program-dex-jars/dex_manifest.txt').decode().splitlines()
        original_manifest = stock.read('assets/secondary-program-dex-jars/dex_manifest.txt').decode().splitlines()
        assert manifest[:-1] == original_manifest, 'Stock loader flags changed'
        assert len(manifest) == len(apk_names), 'Stale DEX manifest count'
        for index, line in enumerate(manifest):
            assert line.startswith(f'Lsecondary/dex{index:02d}/Canary;,ordinal={index},')
        for name in apk_names:
            data = apk.read(name)
            for offset in (64, 80, 88):
                assert struct.unpack_from('<I', data, offset)[0] <= 65535, f'DEX reference overflow: {name}'
        assert 'assets/dexopt/baseline.prof' not in apk.namelist(), 'Stale stock method profile retained'
        assert 'assets/dexopt/baseline.profm' not in apk.namelist(), 'Stale stock profile metadata retained'
    return {'stockDexPartitions': len(stock_names), 'extensionDexPartitions': 1, 'loaderMetadataSynchronized': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--stock', type=Path, required=True)
    args = parser.parse_args()
    print(verify(args.apk, args.stock))
