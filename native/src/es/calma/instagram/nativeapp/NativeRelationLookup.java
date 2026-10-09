package es.calma.instagram.nativeapp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final Map<String, Long> PAUSED = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Void>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Long> ATTEMPTED = new ConcurrentHashMap<>();
    private static final long RETRY_AFTER_MS = 5 * 60_000L;
    private NativeRelationLookup() {}

    static CompletableFuture<Void> refresh(Object session, List<String> input) {
        String owner = NativeRelations.owner(session);
        if (owner.length() == 0) return CompletableFuture.completedFuture(null);
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(input));
        ids.removeIf(id -> id == null || !id.matches("[0-9]+"));
        if (ids.size() > 100) ids = new ArrayList<>(ids.subList(0, 100));
        Collections.sort(ids);
        if (ids.isEmpty()) return CompletableFuture.completedFuture(null);
        String key = owner + ':' + String.join(",", ids);
        synchronized (ACTIVE) {
            CompletableFuture<Void> pending = ACTIVE.get(key);
            if (pending != null) return pending;
            Long attempted = ATTEMPTED.get(key);
            long now = System.currentTimeMillis();
            Long paused = PAUSED.get(owner);
            if (paused != null && now - paused < RETRY_AFTER_MS) return CompletableFuture.completedFuture(null);
            if (attempted != null && now - attempted < RETRY_AFTER_MS) return CompletableFuture.completedFuture(null);
            ATTEMPTED.put(key, now);
            CompletableFuture<Void> result = new CompletableFuture<>();
            ACTIVE.put(key, result);
            if (ATTEMPTED.size() > 2048) ATTEMPTED.entrySet().removeIf(entry -> now - entry.getValue() >= RETRY_AFTER_MS);
            List<String> requestIds = ids;
            AtomicReference<Object> executing = new AtomicReference<>();
            ScheduledFuture<?> deadline = DEADLINES.schedule(() -> {
                if (result.completeExceptionally(new TimeoutException("Friendship lookup timed out"))) {
                    PAUSED.put(owner, System.currentTimeMillis());
                    Object request = executing.get();
                    if (request != null) try { StockAccess.call(request, "cancel"); } catch (ReflectiveOperationException ignored) {}
                    ACTIVE.remove(key, result);
                }
            }, 15, TimeUnit.SECONDS);
            WORKER.execute(() -> {
                try {
                    if (result.isDone()) return;
                    Class<?> requests = Class.forName("X.0BnR", false, session.getClass().getClassLoader());
                    Method factory = StockAccess.method(requests, "A04", session.getClass(), List.class, boolean.class, boolean.class, boolean.class);
                    // Cache=false; include_followed_by=true; notify/update native user cache=true.
                    Object request = factory.invoke(null, session, requestIds, false, true, true);
                    executing.set(request);
                    if (result.isDone()) return;
                    StockAccess.call(request, "run");
                    boolean anyResolved = false;
                    for (String id : requestIds) if (NativeRelations.resolved(session, id)) { anyResolved = true; break; }
                    if (!anyResolved) PAUSED.put(owner, System.currentTimeMillis());
                    result.complete(null);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    PAUSED.put(owner, System.currentTimeMillis());
                    result.completeExceptionally(failure);
                } finally { deadline.cancel(false); ACTIVE.remove(key, result); }
            });
            return result;
        }
    }

    static void awaitOffMainThread(Object session, List<String> ids) {
        if (ids.isEmpty()) return;
        CompletableFuture<Void> request = refresh(session, ids);
        if (mainThread()) return;
        try { request.get(2500, TimeUnit.MILLISECONDS); }
        catch (Exception pendingOrRejected) { /* A later native cache update keeps the verified account data. */ }
    }

    private static boolean mainThread() {
        try {
            Class<?> looper = Class.forName("android.os.Looper");
            return looper.getMethod("myLooper").invoke(null) == looper.getMethod("getMainLooper").invoke(null);
        } catch (ReflectiveOperationException noAndroidRuntime) { return "main".equals(Thread.currentThread().getName()); }
    }
}
