#!/usr/bin/env python3
from pathlib import Path
import subprocess, tempfile
ROOT = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='calma-credits-') as directory:
    subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-17','-proc:none','-d',directory,
        str(ROOT/'native/src/es/calma/instagram/nativeapp/HourlyCredits.java'),
        str(ROOT/'native/tests/HourlyCreditsTest.java')],check=True)
    subprocess.run(['java','-cp',directory,'es.calma.instagram.nativeapp.HourlyCreditsTest'],check=True)
