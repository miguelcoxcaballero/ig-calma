#!/usr/bin/env python3
"""Check the signed APK's ad guard, rejection value and organic continuation."""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--morphe', type=Path, required=True)
args = parser.parse_args()
classpath = str(ROOT / 'native/build/tools') + ':' + str(args.morphe)
with tempfile.TemporaryDirectory(prefix='calma-ads-dex-') as temporary:
    dex = Path(temporary) / 'ads.dex'
    subprocess.run(['java', '-Xmx1g', '-cp', classpath, 'es.calma.tools.DexSubset',
                    str(args.apk), str(dex), 'LX/08S0;',
                    'Les/calma/instagram/nativeapp/NativeAds;',
                    'Les/calma/instagram/nativeapp/NativeFeed;',
                    'Les/calma/instagram/nativeapp/NativeDiscover;'],
                   check=True, stdout=subprocess.DEVNULL)
    dump = subprocess.check_output(['java', '-Xmx512m', '-cp', classpath,
                                    'es.calma.tools.DexInspect', str(dex),
                                    'E1e|story|filter|resolveMissing|permittedMedia|resolveAuthors'], text=True)
    blocks = re.split(r'(?=^METHOD )', dump, flags=re.M)
    body = next(block for block in blocks if block.startswith('METHOD LX/08S0;->E1e('))
    instructions = [(int(match[1], 16), match[2]) for line in body.splitlines()
                    if (match := re.match(r'\s*\d+ @([0-9a-f]+) (.*)', line))]
    hooks = [i for i, (_, instruction) in enumerate(instructions) if 'NativeAds;->story(' in instruction]
    assert len(hooks) == 1, 'Story insertion must have one sponsored guard'
    start = hooks[0]
    window = instructions[start:start + 9]
    assert len(window) == 9
    reel = re.search(r'v(\d+)', window[0][1])[1]
    scratch = re.fullmatch(r'move-result v(\d+)', window[1][1])[1]
    assert reel != scratch and int(scratch) < 11, 'Guard cannot overwrite the reel or parameter registers'
    assert re.search(r'iget-object v' + reel + r', v\d+ LX/04sN;->A0U:LX/03sn;', instructions[start - 1][1])
    branch = re.fullmatch(r'if-eqz v' + scratch + r' -> @([0-9a-f]+)', window[2][1])
    assert branch and int(branch[1], 16) == window[7][0], 'Organic branch must continue through the original predicate'
    assert window[3][1] == 'const/16 v' + scratch + ' #8', 'Use native content_invalid, not a successful insertion'
    assert 'Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;' in window[4][1]
    assert window[5][1] == 'move-result-object v' + scratch
    assert window[6][1] == 'return-object v' + scratch
    assert window[7][1] == 'invoke-virtual {v' + reel + '} LX/03sn;->A14()Z'
    assert window[8][1] == 'move-result v' + scratch, 'Original predicate result stays paired'
    protected = {address for address, _ in window[1:8]}
    for address, instruction in instructions:
        target = re.search(r'-> @([0-9a-f]+)', instruction)
        if target and int(target[1], 16) in protected:
            assert address == window[2][0], 'Incoming branch bypasses sponsored rejection'
    for name in ('NativeFeed', 'NativeDiscover'):
        selected = '\n'.join(block for block in blocks if block.startswith('METHOD Les/calma/instagram/nativeapp/' + name + ';'))
        assert 'NativeAds;->media(' in selected, name + ' does not inspect sponsored media'
    helper = next(block for block in blocks if block.startswith('METHOD Les/calma/instagram/nativeapp/NativeAds;->story('))
    flags = int(re.search(r' FLAGS (\d+)', helper)[1])
    assert flags & 1 and flags & 8, 'Story helper must be public static'
    print('Native ads DEX: sponsored Story rejection, organic continuation and feed/Discover predicates verified')
