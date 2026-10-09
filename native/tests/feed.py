#!/usr/bin/env python3
import pathlib, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
PACKAGE = pathlib.Path('es/calma/instagram/nativeapp')
with tempfile.TemporaryDirectory(prefix='calma-native-feed-') as tmp:
    root = pathlib.Path(tmp)
    stub = root / PACKAGE / 'CalmaConfig.java'; stub.parent.mkdir(parents=True)
    stub.write_text('package es.calma.instagram.nativeapp; public final class CalmaConfig { public static int testMode=1; public static long epoch=1; public static long sessionId(){return epoch;} public static long contentId(){return epoch;} public static String feed(){return testMode==0?"BLENDED_FOR_YOU":"RECENTS";} public static void scope(Integer mode){} public static int mode(){return testMode;} public static boolean reels(){return true;} }')
    end = root / PACKAGE / 'NativeFeedEnd.java'
    end.write_text('package es.calma.instagram.nativeapp; final class NativeFeedEnd {static void append(Object r,Object s){} static void appendStatus(Object r,Object s,boolean complete){}}')
    source = ROOT / 'native/src' / PACKAGE
    subprocess.run(['java', '-jar', str(ROOT/'tools/ecj.jar'), '-17', '-proc:none', '-d', str(root), str(stub), str(end), *[str(source/name) for name in ['StockAccess.java', 'NativeAds.java', 'NativeRelationLookup.java', 'NativeRelations.java', 'RecentFeedPolicy.java', 'ChronologyState.java', 'NativeFeed.java', 'NativeTimeline.java', 'NativeTimelineStore.java']], str(ROOT/'native/tests/NativeFeedTest.java'), str(ROOT/'native/tests/NativeTimelineTest.java')],check=True)
    subprocess.run(['java','-cp',str(root),'es.calma.instagram.nativeapp.NativeFeedTest'],check=True)
    subprocess.run(['java','-cp',str(root),'es.calma.instagram.nativeapp.NativeTimelineTest'],check=True)
