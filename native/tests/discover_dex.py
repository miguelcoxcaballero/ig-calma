#!/usr/bin/env python3
"""Inspect every real Explore/search/SERP return path in the signed APK."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('apk',type=Path)
p.add_argument('--morphe',type=Path,required=True)
args=p.parse_args()
with tempfile.TemporaryDirectory(prefix='calma-discover-dex-') as tmp:
    subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-17','-proc:none','-classpath',str(args.morphe),'-d',tmp,str(ROOT/'native/tests/DiscoverDexCheck.java')],check=True)
    subprocess.run(['java','-Xmx1g','-cp',tmp+':'+str(args.morphe),'DiscoverDexCheck',str(args.apk)],check=True)
