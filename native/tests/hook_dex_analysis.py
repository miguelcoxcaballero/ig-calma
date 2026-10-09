#!/usr/bin/env python3
"""Analyze actual APK hook register data flow; this does not replace an ART boot test."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--morphe', type=Path, required=True)
args = parser.parse_args()
morphe = args.morphe.resolve()
lock = json.loads((ROOT / 'native/tools.lock.json').read_text())
if hashlib.sha256(morphe.read_bytes()).hexdigest() != lock['morphe']['sha256']:
    raise SystemExit('Morphe tool SHA-256 does not match native/tools.lock.json')
with tempfile.TemporaryDirectory(prefix='calma-hook-analysis-') as directory:
    subprocess.run(['java', '-jar', str(ROOT / 'tools/ecj.jar'), '-17', '-proc:none',
                    '-classpath', str(morphe), '-d', directory,
                    str(ROOT / 'native/tests/HookDexAnalysis.java')], check=True)
    subprocess.run(['java', '-Xmx3g', '-cp', directory + ':' + str(morphe),
                    'HookDexAnalysis', str(args.apk.resolve())], check=True)
