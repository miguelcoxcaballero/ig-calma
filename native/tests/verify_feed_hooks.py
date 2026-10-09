#!/usr/bin/env python3
"""Check branch destinations in a patched APK, not just hook-string presence."""
import argparse, pathlib, re, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(); p.add_argument('apk', type=pathlib.Path); p.add_argument('--morphe', type=pathlib.Path, required=True); args = p.parse_args()
classpath = str(ROOT/'native/build/tools') + ':' + str(args.morphe)
with tempfile.TemporaryDirectory(prefix='calma-feed-hooks-') as tmp:
    dex = pathlib.Path(tmp)/'feed.dex'
    subprocess.run(['java','-Xmx1g','-cp',classpath,'es.calma.tools.DexSubset',str(args.apk),str(dex),'LX/02px;','LX/0ICa;','LX/0BnS;','LX/02qb;','LX/06gK;','LX/05qX;','LX/03u7;'],check=True,stdout=subprocess.DEVNULL)
    output = subprocess.check_output(['java','-Xmx512m','-cp',classpath,'es.calma.tools.DexInspect',str(dex),'unsafeParseFromJson|A00|A01|A0C|A0E|A0G|bindView'],text=True)
    blocks = re.split(r'(?=^METHOD )', output, flags=re.M)
    feed = next(block for block in blocks if block.startswith('METHOD LX/02px;->unsafeParseFromJson'))
    batch = next(block for block in blocks if block.startswith('METHOD LX/0ICa;->A00'))
    params = next(block for block in blocks if block.startswith('METHOD LX/02qb;->A01'))
    def instructions(block):
        return [(int(m[1],16),m[2]) for line in block.splitlines() if (m:=re.match(r'\s*\d+ @([0-9a-f]+) (.*)',line))]
    feed_ins = instructions(feed)
    feed_returns = {address for address, instruction in feed_ins if instruction.startswith('return-object')}
    assert len(feed_returns) == 2
    for index, (address, instruction) in enumerate(feed_ins):
        if address in feed_returns:
            assert 'NativeFeed;->response(' in feed_ins[index-1][1], 'Feed return missing response hook'
        branch = re.search(r'-> @([0-9a-f]+)',instruction)
        if branch:
            assert int(branch[1],16) not in feed_returns, 'Feed branch bypasses filtering hook'
    status_parser = next(block for block in blocks if block.startswith('METHOD LX/0BnS;->A00('))
    assert 'NativeRelations;->statusField(' in instructions(status_parser)[0][1], 'Native JSON fields must establish presence before primitive defaults are read'
    batch_ins = instructions(batch)
    ends = [address for address,instruction in batch_ins if 'NativeRelations;->endStatus(' in instruction]
    begins = [address for address,instruction in batch_ins if 'NativeRelations;->beginStatus(' in instruction]
    assert len(ends)==len(begins)==1, 'Native batch status capture missing'
    assert any('if-eq ' in instruction and instruction.endswith('-> @'+format(ends[0],'x')) for _,instruction in batch_ins), 'Batch parse branch bypasses status capture'
    batch_targets = {int(m[1],16) for _, instruction in batch_ins if (m := re.search(r'-> @([0-9a-f]+)', instruction))}
    lookup_index = next(i for i, (_, instruction) in enumerate(batch_ins) if 'LX/0223;->A0e(' in instruction)
    assert 'NativeRelations;->beginStatus(' in batch_ins[lookup_index-1][1], 'Native user lookup lacks account/id capture'
    assert batch_ins[lookup_index][0] not in batch_targets, 'Inbound branch bypasses beginStatus capture'
    params_ins = instructions(params)
    params_targets = {int(m[1],16) for _, instruction in params_ins if (m := re.search(r'-> @([0-9a-f]+)', instruction))}
    map_index = next(i for i, (_, instruction) in enumerate(params_ins) if 'LX/02pp;->A0L:Ljava/util/Map;' in instruction)
    assert 'NativeFeed;->parameters(' in params_ins[map_index+1][1], 'Main-feed parameter map bypasses Following selection'
    assert params_ins[map_index+2][1].startswith('move-result-object'), 'Selected map must replace the map register'
    assert params_ins[map_index+3][0] not in params_targets, 'Inbound branch bypasses Following selection'
    assert 'NativeFeed;->context(' in params_ins[0][1], 'Native request context must be captured at entry'
    wire = next(block for block in blocks if block.startswith('METHOD LX/03u7;->A01'))
    assert 'NativeFeed;->wire(' in instructions(wire)[0][1], 'Final HTTP serialization bypasses selected feed'
    delivery = next(block for block in blocks if block.startswith('METHOD LX/05qX;->A0C'))
    assert 'NativeFeed;->delivered(' in instructions(delivery)[0][1], 'Actual request/response delivery bypasses filtering'
    local = next(block for block in blocks if block.startswith('METHOD LX/05qX;->A0E'))
    assert 'NativeFeed;->localRows(' in instructions(local)[0][1], 'Native local/cache UI delivery bypasses filtering'
    end = next(block for block in blocks if block.startswith('METHOD LX/06gK;->bindView'))
    end_ins = instructions(end)
    assert 'NativeFeedEnd;->bind(' in end_ins[0][1]
    assert end_ins[1][1].startswith('move-result v0')
    assert end_ins[2][1].startswith('if-eqz v0')
    assert end_ins[3][1] == 'return-void'
    assert int(re.search(r'-> @([0-9a-f]+)',end_ins[2][1])[1],16) == end_ins[4][0], 'Non-Calma end rows must run original binder'
    controller = next(block for block in blocks if block.startswith('METHOD LX/05qX;->A0G'))
    controller_ins = instructions(controller)
    returns = {address for address, instruction in controller_ins if instruction == 'return-void'}
    assert returns
    for i, (address, instruction) in enumerate(controller_ins):
        if address in returns: assert 'NativeTimelinePager;->completed(' in controller_ins[i-1][1]
        branch = re.search(r'-> @([0-9a-f]+)', instruction)
        if branch: assert int(branch[1],16) not in returns, 'Completion branch bypasses preload hook'
    print('Native feed bytecode: response, request and friendship hooks are reached by actual control flow')
