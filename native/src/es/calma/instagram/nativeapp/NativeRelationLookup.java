package es.calma.instagram.nativeapp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeoutException;

/** Bounded calls through Instagram's own authenticated friendship request and parser. */
final class NativeRelationLookup {
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "CalmaRelations"); thread.setDaemon(true); return thread;
    });
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CalmaRelationTimeout"); thread.setDaemon(true); return thread;
    });

    private static final Map<String, CompletableFuture<Void>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Long> ATTEMPTED = new ConcurrentHashMap<>();
    private static final long RETRY_AFTER_MS = 3_000L;
    private NativeRelationLookup() {}

    static CompletableFuture<Void> refresh(Object session, List<String> input) {
        String owner = NativeRelations.owner(session);
        if (owner.length() == 0) return CompletableFuture.completedFuture(null);
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(input));
        ids.removeIf(id -> id == null || !id.matches("[0-9]+"));
        ids.removeIf(id -> NativeRelations.verified(session, id));
        List<CompletableFuture<Void>> waiting = new ArrayList<>();
        List<String> fresh = new ArrayList<>();
        synchronized (ACTIVE) {
            long now = System.currentTimeMillis();
            for (String id : ids) {
                String key = owner + ':' + id;
                CompletableFuture<Void> pending = ACTIVE.get(key);
                if (pending != null) { waiting.add(pending); continue; }
                Long attempted = ATTEMPTED.get(key);
                if (attempted != null && now - attempted < RETRY_AFTER_MS) {
                    CompletableFuture<Void> rejected = new CompletableFuture<>();
                    rejected.completeExceptionally(new IllegalStateException("Friendship lookup retry pending"));
                    waiting.add(rejected); continue;
                }
                CompletableFuture<Void> result = new CompletableFuture<>();
                ACTIVE.put(key, result); waiting.add(result); fresh.add(id);
            }
            for (int start = 0; start < fresh.size(); start += 50) {
                List<String> batch = new ArrayList<>(fresh.subList(start, Math.min(start + 50, fresh.size())));
                Map<String,CompletableFuture<Void>> promises = new java.util.HashMap<>();
                for (String id : batch) promises.put(id, ACTIVE.get(owner + ':' + id));
                WORKER.execute(() -> execute(session, owner, batch, promises));
            }
        }
        return CompletableFuture.allOf(waiting.toArray(new CompletableFuture<?>[0]));
    }
    private static void execute(Object session, String owner, List<String> ids, Map<String,CompletableFuture<Void>> promises) {
        AtomicReference<Object> executing = new AtomicReference<>();
        ScheduledFuture<?> deadline = DEADLINES.schedule(() -> {
            finish(owner, promises, new TimeoutException("Friendship lookup timed out"));
            Object request = executing.get();
            if (request != null) try { StockAccess.call(request, "cancel"); } catch (ReflectiveOperationException ignored) {}
        }, 15, TimeUnit.SECONDS);
        try {
            Class<?> requests = Class.forName("X.0BnR", false, session.getClass().getClassLoader());
            Method factory = StockAccess.method(requests, "A04", session.getClass(), List.class, boolean.class, boolean.class, boolean.class);
            Object request = factory.invoke(null, session, ids, false, true, true);
            executing.set(request);
            StockAccess.call(request, "run");
            for (String id : ids) {
                CompletableFuture<Void> result = promises.get(id);
                if (NativeRelations.verified(session, id)) result.complete(null);
                else result.completeExceptionally(new IllegalStateException("Incomplete friendship response"));
            }
            finish(owner, promises, null);
        } catch (ReflectiveOperationException | RuntimeException failure) { finish(owner, promises, failure); }
        finally { deadline.cancel(false); }
    }
    private static void finish(String owner, Map<String,CompletableFuture<Void>> promises, Throwable failure) {
        for (Map.Entry<String,CompletableFuture<Void>> entry : promises.entrySet()) {
            String key = owner + ':' + entry.getKey();
            CompletableFuture<Void> result = entry.getValue();
            if (failure != null) result.completeExceptionally(failure);
            if (result.isCompletedExceptionally()) ATTEMPTED.put(key,System.currentTimeMillis());
            else ATTEMPTED.remove(key);
            ACTIVE.remove(key,result);
        }
        if (ATTEMPTED.size() > 2048) {
            long now = System.currentTimeMillis();
            ATTEMPTED.entrySet().removeIf(e -> now-e.getValue() >= RETRY_AFTER_MS);
        }
    }

    static void awaitOffMainThread(Object session, List<String> ids) {
        if (ids.isEmpty()) return;
        CompletableFuture<Void> request = refresh(session, ids);
        if (mainThread()) return;
        try { request.get(16, TimeUnit.SECONDS); }
        catch (Exception pendingOrRejected) { /* A later native cache update keeps the verified account data. */ }
    }

    static boolean mainThread() {
        try {
            Class<?> looper = Class.forName("android.os.Looper");
            return looper.getMethod("myLooper").invoke(null) == looper.getMethod("getMainLooper").invoke(null);
        } catch (ReflectiveOperationException noAndroidRuntime) { return "main".equals(Thread.currentThread().getName()); }
    }
}
