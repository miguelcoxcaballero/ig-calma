#!/usr/bin/env python3
"""Run packaging regressions with the same namespace-unaware DOM as Morphe."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='calma-package-tests-') as temporary:
    out = Path(temporary) / 'classes'
    out.mkdir()
    compiler = ROOT / 'tools/kotlin/*'
    stdlib = ROOT / 'tools/kotlin/stdlib.jar'
    subprocess.run([
        'java', '-cp', str(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', str(stdlib),
        '-d', str(out), str(ROOT / 'native/patches/CloneNames.kt'),
        str(ROOT / 'native/tests/CloneNamesTest.kt'),
    ], check=True)
    subprocess.run(['java', '-cp', f'{out}:{stdlib}', 'es.calma.patches.CloneNamesTestKt'], check=True)
