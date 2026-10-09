#!/usr/bin/env python3
import pathlib, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
PACKAGE = pathlib.Path('es/calma/instagram/nativeapp')
with tempfile.TemporaryDirectory(prefix='calma-native-feed-') as tmp:
    root = pathlib.Path(tmp)
    stub = root / PACKAGE / 'CalmaConfig.java'; stub.parent.mkdir(parents=True)
    stub.write_text('package es.calma.instagram.nativeapp; public final class CalmaConfig { public static int testMode=1; public static long epoch=1; public static long sessionId(){return epoch;} public static int mode(){return testMode;} public static boolean reels(){return true;} }')
    source = ROOT / 'native/src' / PACKAGE
    subprocess.run(['java', '-jar', str(ROOT/'tools/ecj.jar'), '-17', '-proc:none', '-d', str(root), str(stub), *[str(source/name) for name in ['StockAccess.java', 'NativeRelationLookup.java', 'NativeRelations.java', 'RecentFeedPolicy.java', 'ChronologyState.java', 'NativeFeed.java']], str(ROOT/'native/tests/NativeFeedTest.java')],check=True)
    subprocess.run(['java','-cp',str(root),'es.calma.instagram.nativeapp.NativeFeedTest'],check=True)
