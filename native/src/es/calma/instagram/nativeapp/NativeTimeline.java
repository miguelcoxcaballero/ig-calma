package es.calma.instagram.nativeapp;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** A complete, account-scoped snapshot. Native requests and native feed rows throughout. */
final class NativeTimeline {
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "CalmaTimeline"); t.setDaemon(true); return t;
    });
    private static final Map<String, Capture> CAPTURES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Capture> EXECUTING = new ThreadLocal<>();
    private static final Map<String, Snapshot> SAVED = new ConcurrentHashMap<>();
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final String[] COPY_FIELDS = {"A06", "A07", "A09", "A08", "A0A", "A0B", "A0C", "A0I", "A0G", "A0K", "A0J", "A0H", "A0D", "A0E", "A0F", "A01", "A00", "A0M", "A0L", "A0N", "A02", "A05", "A03", "A0O", "A04"};
    static final class Context {
        final Object androidContext, session, request, parameters;
        Context(Object context, Object session, Object request, Object parameters) {
            this.androidContext = context; this.session = session; this.request = request; this.parameters = parameters;
        }
    }
    private static final class Capture {
        final CompletableFuture<Object> result = new CompletableFuture<>();
        volatile Object task;
    }
    static final class Snapshot {
        final List<Object> wrappers, media;
        final long anchor, epoch;
        Snapshot(List<Object> wrappers, List<Object> media, long anchor) {
            this.wrappers = wrappers == null ? null : Collections.unmodifiableList(new ArrayList<>(wrappers));
            this.media = media == null ? null : Collections.unmodifiableList(new ArrayList<>(media));
            this.anchor = anchor; this.epoch = CalmaConfig.sessionId();
        }
        void apply(Object response) throws ReflectiveOperationException {
            StockAccess.set(response, "A0S", wrappers == null ? null : new ArrayList<>(wrappers));
            StockAccess.set(response, "A0U", media == null ? null : new ArrayList<>(media));
            finish(response);
        }
    }
    private NativeTimeline() {}

    static boolean capture(Object response, String owner) throws ReflectiveOperationException {
        String id = (String) StockAccess.get(response, "A0P");
        Capture capture = id == null ? null : CAPTURES.get(owner + ':' + id);
        if (capture == null) capture = EXECUTING.get();
        if (capture == null) return false;
        capture.result.complete(response);
        return true;
    }
    static Snapshot saved(Object session) {
        Snapshot snapshot = SAVED.get(NativeRelations.owner(session));
        return snapshot != null && snapshot.epoch == CalmaConfig.sessionId() ? snapshot : null;
    }
    static boolean internal() { return EXECUTING.get() != null; }
    static void finish(Object response) throws ReflectiveOperationException {
        StockAccess.set(response, "A0a", false);
        StockAccess.set(response, "A0W", false);
        StockAccess.set(response, "A0N", null);
    }

    interface PageSource { Object next(Context context, String cursor, long deadline) throws Exception; }
    static Snapshot load(Object first, Context context, long anchor) throws Exception {
        return load(first, context, anchor, NativeTimeline::fetch);
    }
    static Snapshot load(Object first, Context context, long anchor, PageSource source) throws Exception {
        String owner = NativeRelations.owner(context.session);
        if (NativeRelationLookup.mainThread()) {
            Snapshot cached = SAVED.get(owner);
            if (cached != null && cached.epoch == CalmaConfig.sessionId()) return cached;
            throw new IllegalStateException("Timeline needs a network worker");
        }
        synchronized (LOCKS.computeIfAbsent(owner, ignored -> new Object())) {
            Snapshot previous = SAVED.get(owner);
            String reason = String.valueOf(StockAccess.get(context.request, "A09"));
            boolean refresh = reason.equals("pull_to_refresh") || reason.equals("pill_refresh")
                    || reason.equals("new_follow") || reason.equals("content_refresh");
            if (!refresh && previous != null && previous.epoch == CalmaConfig.sessionId()) return previous;
            if (!refresh && previous == null) {
                Snapshot disk = NativeTimelineStore.read(context, first, anchor);
                if (disk != null) { SAVED.put(owner, disk); return disk; }
            }
            if (NativeRelationLookup.mainThread()) throw new IllegalStateException("Timeline needs a network worker");
            long epoch = CalmaConfig.sessionId();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            List<Object> wrappers = new ArrayList<>(), media = new ArrayList<>();
            boolean hasWrappers = false, hasMedia = false;
            Set<String> cursors = new HashSet<>();
            List<CompletableFuture<Void>> relations = new ArrayList<>();
            ChronologyState chronology = new ChronologyState();
            Object page = first;
            boolean head = true;
            for (;;) {
                if (System.nanoTime() >= deadline) throw new java.util.concurrent.TimeoutException("Timeline synchronization timed out");
                Object w = StockAccess.get(page, "A0S"), m = StockAccess.get(page, "A0U");
                List<?> wl = w instanceof List ? (List<?>) w : Collections.emptyList();
                List<?> ml = m instanceof List ? (List<?>) m : Collections.emptyList();
                hasWrappers |= w instanceof List; hasMedia |= m instanceof List;
                // Stories and other head controls are retained exactly once.
                for (Object row : wl) if (row != null && (head || StockAccess.call(row, "A0A") != null)) wrappers.add(row);
                media.addAll(ml);
                List<String> missing = NativeFeed.missing(wl, ml, context.session, anchor);
                if (!missing.isEmpty()) relations.add(NativeRelationLookup.refresh(context.session, missing));
                boolean boundary = NativeFeed.oldPage(wl, ml, anchor, chronology, head);
                boolean more = Boolean.TRUE.equals(StockAccess.get(page, "A0a"));
                if (!more || boundary) break;
                String cursor = (String) StockAccess.get(page, "A0N");
                if (cursor == null || cursor.isEmpty() || !cursors.add(cursor))
                    throw new IllegalStateException("Timeline pagination did not advance");
                page = source.next(context, cursor, deadline);
                head = false;
            }
            CompletableFuture.allOf(relations.toArray(new CompletableFuture<?>[0]))
                    .get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            // A missing relationship is a retryable load failure, never a negative friendship.
            if (!NativeFeed.missing(wrappers, media, context.session, anchor).isEmpty())
                throw new IllegalStateException("Some friendships are still unresolved");
            NativeFeed.Page filteredWrappers = NativeFeed.filter(wrappers, true, anchor, true, context.session);
            NativeFeed.Page filteredMedia = NativeFeed.filter(media, false, anchor, true, context.session);
            Snapshot result = new Snapshot(hasWrappers ? filteredWrappers.items : null, hasMedia ? filteredMedia.items : null, anchor);
            // Publish only a complete generation. A failed refresh leaves the previous feed intact.
            if (epoch != CalmaConfig.sessionId()) throw new IllegalStateException("Timeline settings changed during synchronization");
            SAVED.put(owner, result);
            NativeTimelineStore.write(context, result);
            return result;
        }
    }

    private static Object fetch(Context context, String cursor, long deadline) throws Exception {
        String id = UUID.randomUUID().toString(), owner = NativeRelations.owner(context.session);
        Capture capture = new Capture();
        String key = owner + ':' + id;
        CAPTURES.put(key, capture);
        java.util.concurrent.Future<?> job = NETWORK.submit(() -> {
            EXECUTING.set(capture);
            try {
                ClassLoader loader = context.session.getClass().getClassLoader();
                Object[] args = new Object[COPY_FIELDS.length];
                for (int i = 0; i < args.length; i++) args[i] = StockAccess.get(context.request, COPY_FIELDS[i]);
                Class<?> reason = Class.forName("X.02pk", false, loader);
                args[2] = reason.getField("A0R").get(null); // PAGINATION
                args[8] = cursor; args[11] = id;
                args[18] = new HashMap<>((Map<?, ?>) parametersMap(args[18]));
                Constructor<?> constructor = null;
                for (Constructor<?> candidate : context.request.getClass().getConstructors())
                    if (candidate.getParameterCount() == 25) constructor = candidate;
                if (constructor == null) throw new NoSuchMethodException("Native timeline request constructor");
                Object request = constructor.newInstance(args);
                Class<?> factoryType = Class.forName("X.02dy", false, loader);
                Object factory = factoryType.getConstructors()[0].newInstance(context.androidContext, context.session, context.parameters);
                Class<?> function = Class.forName("kotlin.jvm.functions.Function1", false, loader);
                Object identity = Proxy.newProxyInstance(loader, new Class<?>[]{function}, (proxy, method, arguments) -> {
                    if (method.getName().equals("invoke")) return arguments[0];
                    if (method.getName().equals("toString")) return "CalmaTimelineParameters";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    return proxy == arguments[0];
                });
                Object task = StockAccess.method(factoryType, "A01", request.getClass(), factoryType, function, int.class, int.class)
                        .invoke(null, request, factory, identity, -20, 0);
                capture.task = task;
                if (!capture.result.isDone()) StockAccess.call(task, "run");
                // Both pinned native transports run synchronously; absent parsed result means HTTP/parser failure.
                if (!capture.result.isDone()) capture.result.completeExceptionally(new IllegalStateException("Native timeline request failed"));
            } catch (ReflectiveOperationException | RuntimeException failure) { capture.result.completeExceptionally(failure); }
            finally { EXECUTING.remove(); }
        });
        try {
            job.get(Math.max(1, Math.min(TimeUnit.SECONDS.toNanos(20), deadline - System.nanoTime())), TimeUnit.NANOSECONDS);
            return capture.result.getNow(null);
        } finally {
            CAPTURES.remove(key, capture);
            if (!job.isDone()) {
                capture.result.completeExceptionally(new java.util.concurrent.CancellationException());
                Object task = capture.task;
                if (task != null) try { StockAccess.call(task, "cancel"); } catch (ReflectiveOperationException ignored) {}
                job.cancel(true);
            }
        }
    }
    private static Map<?, ?> parametersMap(Object value) { return NativeFeed.parameters((Map<?, ?>) value); }
}
