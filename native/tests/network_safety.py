#!/usr/bin/env python3
"""Run the production relationship code against an instrumented stock transport.

Unknown Explore authors must remain unknown without starting an authenticated
request. This uses the actual account-scoped relationship cache, including cached
negative answers, so disabling the transport cannot silently admit strangers.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = Path('es/calma/instagram/nativeapp')
fixtures = {
    'X/_BnR.java': '''package X;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import es.calma.instagram.nativeapp.NetworkSafetyTest.Session;
public final class _BnR {
    public static final AtomicInteger factories = new AtomicInteger();
    public static final AtomicInteger executions = new AtomicInteger();
    public static Task A04(Session session,List<String> ids,boolean a,boolean b,boolean c) {
        factories.incrementAndGet(); return new Task();
    }
    public static final class Task {
        public void run() { executions.incrementAndGet(); }
        public void cancel() { }
    }
}''',
    str(PACKAGE / 'NativeTimelineProgress.java'): '''package es.calma.instagram.nativeapp;
final class NativeTimelineProgress { static void relationshipChanged(String owner) { } }
''',
    str(PACKAGE / 'NetworkSafetyTest.java'): '''package es.calma.instagram.nativeapp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class NetworkSafetyTest {
    public static final class Session {
        private final String id;
        Session(String id) { this.id=id; }
        public String getUserId() { return id; }
    }
    public static final class Status {
        public boolean A0H;
        public Boolean A02;
        Status(boolean following,Boolean followedBy) { A0H=following; A02=followedBy; }
    }
    private static int checks;
    private static void check(boolean ok,String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void known(Session session,String id,boolean following,Boolean followedBy) {
        Status status=new Status(following,followedBy);
        NativeRelations.beginStatus(session,id,status);
        NativeRelations.statusField(status,"following");
        NativeRelations.endStatus(status);
    }
    private static void success(CompletableFuture<Void> result,String message) {
        check(result.isDone() && !result.isCompletedExceptionally(),message);
        result.join();
    }
    private static void unavailable(CompletableFuture<Void> result,String message) {
        check(result.isDone() && result.isCompletedExceptionally(),message);
        try { result.join(); throw new AssertionError("Unknown relationship reported as resolved"); }
        catch (CompletionException expected) { }
    }
    private static int counter(String name) throws Exception {
        return ((AtomicInteger) Class.forName("X.0BnR").getField(name).get(null)).get();
    }
    public static void main(String[] arguments) throws Exception {
        Session first=new Session("101"), second=new Session("202");
        known(first,"1",true,true);
        known(first,"2",false,false);
        known(first,"3",true,null);
        success(NativeRelationLookup.refresh(first,Collections.emptyList()),"empty request completes locally");
        success(NativeRelationLookup.refresh(first,Arrays.asList("1","1","2")),"cached positives and negatives complete locally");
        check(NativeRelations.isFollowing(first,"1"),"known following remains visible");
        check(!NativeRelations.isFollowing(first,"2"),"known negative is not turned into permission");
        unavailable(NativeRelationLookup.refresh(first,Arrays.asList("99")),"unknown relationship is unavailable immediately");
        unavailable(NativeRelationLookup.refresh(first,Arrays.asList("1","99")),"partial cache cannot claim a complete lookup");
        unavailable(NativeRelationLookup.refresh(first,Arrays.asList("3")),"incomplete native proof cannot claim a complete lookup");
        unavailable(NativeRelationLookup.refresh(second,Arrays.asList("1")),"cache is isolated by requesting account");
        unavailable(NativeRelationLookup.refresh(new Session(""),Arrays.asList("1")),"missing account cannot use another session");
        unavailable(NativeRelationLookup.refresh(null,Arrays.asList("1")),"absent session cannot start authentication");
        check(!NativeRelations.resolved(first,"99"),"unknown author remains unknown");
        NativeRelationLookup.awaitOffMainThread(first,Arrays.asList("99"));
        check(counter("factories")==0 && counter("executions")==0,"main-thread calls never create or run a stock request");

        ExecutorService workers=Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> work=new ArrayList<>();
            for (int thread=0;thread<4;thread++) work.add(workers.submit(() -> {
                for (int i=0;i<100;i++) {
                    CompletableFuture<Void> result=NativeRelationLookup.refresh(first,Arrays.asList("99","100"));
                    if (!result.isDone() || !result.isCompletedExceptionally())
                        throw new AssertionError("Repeated background request waits or fabricates success");
                    NativeRelationLookup.awaitOffMainThread(first,Arrays.asList("99","100"));
                }
            }));
            for (Future<?> result:work) result.get(2,TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        check(counter("factories")==0 && counter("executions")==0,"repeated concurrent calls never issue authenticated network requests");
        known(second,"99",true,true);
        success(NativeRelationLookup.refresh(second,Arrays.asList("99")),"ordinary native cache updates can resolve a previously unknown author");
        unavailable(NativeRelationLookup.refresh(first,Arrays.asList("99")),"native cache updates remain account scoped");
        check(counter("factories")==0 && counter("executions")==0,"all relationship checks use native data already received");
        System.out.println("Network safety: "+checks+" checks; no authenticated relationship requests or background retries");
    }
}''',
}

with tempfile.TemporaryDirectory(prefix='calma-network-safety-') as temporary:
    work = Path(temporary)
    for name, source in fixtures.items():
        file = work / 'src' / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(source)
    for name in ('NativeRelationLookup.java', 'NativeRelations.java', 'StockAccess.java'):
        source = ROOT / 'native/src' / PACKAGE / name
        (work / 'src' / PACKAGE / name).write_text(source.read_text())
    classes = work / 'classes'
    classes.mkdir()
    subprocess.run([
        'java', '-jar', str(ROOT / 'tools/ecj.jar'), '-1.8', '-proc:none',
        '-d', str(classes), *map(str, (work / 'src').rglob('*.java')),
    ], check=True)
    # Java source identifiers cannot start with a digit; preserve the real stock
    # binary name so a regression to reflective A04/run genuinely hits the spy.
    for file in list(classes.rglob('*.class')):
        data = file.read_bytes().replace(b'X/_BnR', b'X/0BnR')
        target = Path(str(file).replace('/X/_BnR', '/X/0BnR'))
        target.write_bytes(data)
        if target != file:
            file.unlink()
    subprocess.run([
        'java', '-cp', str(classes),
        'es.calma.instagram.nativeapp.NetworkSafetyTest',
    ], check=True, timeout=15)
