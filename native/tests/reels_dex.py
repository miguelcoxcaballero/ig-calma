#!/usr/bin/env python3
"""Check native Reel guards and return control flow in a built APK."""
from pathlib import Path
import argparse
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--morphe', type=Path, required=True)
args = parser.parse_args()
classpath = str(ROOT / 'native/build/tools') + ':' + str(args.morphe.resolve())
java = ['java', '-Xmx1g', '-cp', classpath]
classes = ['LX/03zs;', 'LX/019Z;', 'LX/05Bx;', 'LX/01xt;', 'LX/00dl;',
           'Lcom/instagram/clips/intf/ClipsViewerConfig;',
           'Les/calma/instagram/nativeapp/BlockedReelsFragment;']
with tempfile.TemporaryDirectory(prefix='calma-reels-dex-') as directory:
    subset = Path(directory) / 'reels.dex'
    subprocess.run(java + ['es.calma.tools.DexSubset', str(args.apk.resolve()), str(subset), *classes],
                   stdout=subprocess.DEVNULL, check=True)
    result = subprocess.check_output(java + ['es.calma.tools.DexInspect', str(subset),
        r'^A00$|^A03$|^A06$|^A09$|^A0[A-C]$|^A0[NORSW]$|^<init>$|^getSession$|^getModuleName$|^onCreateView$'], text=True)
methods = {}
for match in re.finditer(r'METHOD ([^\n]+)\n(.*?)(?=\nMETHOD |\nCLASS |\Z)', result, re.S):
    header, body = match.groups()
    methods[header.split(' FLAGS ')[0]] = body

def method(prefix):
    found = [body for name, body in methods.items() if name.startswith(prefix)]
    assert len(found) == 1, (prefix, len(found))
    return found[0]

for prefix, callback in [('LX/019Z;->A0W(', 'lockPager'),
                         ('Lcom/instagram/clips/intf/ClipsViewerConfig;-><init>(', 'configure')]:
    body = method(prefix)
    instructions = [line for line in body.splitlines() if '@' in line]
    branch_targets = {int(address, 16) for address in re.findall(r' -> @([0-9a-f]+)', body)}
    returns = 0
    for index, line in enumerate(instructions):
        if ' return-void' not in line:
            continue
        returns += 1
        assert index and 'CalmaReels;->' + callback in instructions[index - 1], (prefix, line)
        address = int(re.search(r'@([0-9a-f]+)', line)[1], 16)
        assert address not in branch_targets, (prefix, 'branch bypasses callback', line)
    assert returns, prefix

for name in ['A09', 'A0A', 'A0B']:
    body = method('LX/03zs;->' + name + '(')
    assert 'CalmaReels;->launchOrDefer(Ljava/lang/String;[Ljava/lang/Object;)Z' in body
    assert body.index('CalmaReels;->launchOrDefer') < body.index('if-nez') < body.index('return-void')
for name in ['A0O', 'A0N', 'A0R', 'A0S']:
    body = method('LX/019Z;->' + name + '(')
    assert 'CalmaReels;->locked()Z' in body and 'CalmaReels;->lockPager' in body
    assert body.index('CalmaReels;->locked') < body.index('if-eqz') < body.index('return-void')
assert 'CalmaReels;->maySelect(I)Z' in method('LX/019Z;->A03(')
assert 'CalmaReels;->allowLaunch' in method('LX/03zs;->A06(')
factory = method('LX/05Bx;->A0A(')
assert factory.index('CalmaReels;->allowBundle') < factory.index('BlockedReelsFragment;-><init>') < factory.index('return-object')
assert 'LX/00Nv;->A03(Landroid/os/Bundle;LX/02rJ;)V' in factory
assert 'BlockedReelsFragment;-><init>' in method('LX/01xt;->A0C(')
assert 'CalmaReels;->tabLocked()Z' in method('LX/01xt;->A0C('), 'Tab launch must start the same For you consumption policy as a viewer launch'
assert 'CalmaReels;->restoredClass' in method('LX/00dl;->A00(')
assert 'CLASS Les/calma/instagram/nativeapp/BlockedReelsFragment; EXTENDS LX/03z9;' in result
assert 'LX/03z9;-><init>()V' in method('Les/calma/instagram/nativeapp/BlockedReelsFragment;-><init>(')
assert 'LX/00Nv;->A02' in method('Les/calma/instagram/nativeapp/BlockedReelsFragment;->getSession(')
assert 'CalmaReels;->blockedView' in method('Les/calma/instagram/nativeapp/BlockedReelsFragment;->onCreateView(')
print('Native Reels DEX guards, valid fallback fragment and branch-preserving return hooks verified')
