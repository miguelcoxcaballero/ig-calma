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
    private static final ExecutorService PRELOAD = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "CalmaPreload"); t.setDaemon(true); return t;
    });
    private static final Map<String, Stream> STREAMS = new ConcurrentHashMap<>();
    private static final class Stream {
        final long epoch = CalmaConfig.sessionId();
        final CompletableFuture<Snapshot> complete = new CompletableFuture<>();
        final Set<String> wrappers = new HashSet<>(), media = new HashSet<>();
    }
    // Retain the raw head before the native response is filtered or its adapter mutates it.
    public static final class Seed {
        public Object A0S, A0U, A0N;
        public boolean A0a;
        Seed(Object response) throws ReflectiveOperationException {
            Object w = StockAccess.get(response, "A0S"), m = StockAccess.get(response, "A0U");
            A0S = w instanceof List ? new ArrayList<>((List<?>) w) : null;
            A0U = m instanceof List ? new ArrayList<>((List<?>) m) : null;
            A0N = StockAccess.get(response, "A0N");
            A0a = Boolean.TRUE.equals(StockAccess.get(response, "A0a"));
        }
    }
    private static final String[] COPY_FIELDS = {"A06", "A07", "A09", "A08", "A0A", "A0B", "A0C", "A0I", "A0G", "A0K", "A0J", "A0H", "A0D", "A0E", "A0F", "A01", "A00", "A0M", "A0L", "A0N", "A02", "A05", "A03", "A0O", "A04"};
    static final class Context {
        final Object androidContext, session, request, parameters;
        final int mode = CalmaConfig.mode();
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
            this.anchor = anchor; this.epoch = CalmaConfig.contentId();
        }
        void apply(Object response) throws ReflectiveOperationException {
            StockAccess.set(response, "A0S", wrappers == null ? null : new ArrayList<>(wrappers));
            StockAccess.set(response, "A0U", media == null ? null : new ArrayList<>(media));
            finish(response);
        }
        void applyHead(Object response, Object session) throws ReflectiveOperationException {
            Object raw = StockAccess.get(response, "A0S");
            List<Object> controls = new ArrayList<>();
            if (raw instanceof List) for (Object row : NativeFeed.filter((List<?>) raw, true, anchor, true, session).items)
                if (StockAccess.call(row, "A0A") == null) controls.add(row);
            apply(response);
            if (wrappers != null) for (Object row : wrappers)
                if (StockAccess.call(row, "A0A") != null) controls.add(row);
            StockAccess.set(response, "A0S", raw instanceof List || wrappers != null ? controls : null);
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
        Snapshot snapshot = SAVED.get(NativeRelations.owner(session) + ':' + CalmaConfig.mode());
        return snapshot != null && snapshot.epoch == CalmaConfig.contentId() ? snapshot : null;
    }
    static boolean internal() { return EXECUTING.get() != null; }
    static void finish(Object response) throws ReflectiveOperationException {
        StockAccess.set(response, "A0a", false);
        StockAccess.set(response, "A0W", false);
        StockAccess.set(response, "A0N", null);
    }

    interface PageSource { Object next(Context context, String cursor, long deadline) throws Exception; }
    private static String key(Context context) { return NativeRelations.owner(context.session) + ':' + context.mode; }
    private static boolean refresh(Context context) throws ReflectiveOperationException {
        String reason = String.valueOf(StockAccess.get(context.request, "A09"));
        return reason.equals("pull_to_refresh") || reason.equals("pill_refresh")
                || reason.equals("new_follow") || reason.equals("content_refresh");
    }
    /** Cold heads are delivered immediately; pagination and verification warm independently. */
    static Snapshot begin(Object first, Context context, long anchor) throws Exception {
        return begin(first, context, anchor, NativeTimeline::fetch);
    }
    static Snapshot begin(Object first, Context context, long anchor, PageSource source) throws Exception {
        String key = key(context);
        if (!refresh(context)) {
            Snapshot cached = SAVED.get(key);
            if (cached != null && cached.epoch == CalmaConfig.contentId()) return cached;
            Snapshot disk = NativeRelationLookup.mainThread() ? null : NativeTimelineStore.read(context, first, anchor);
            if (disk != null) { SAVED.put(key, disk); return disk; }
        }
        Seed seed = new Seed(first);
        Stream stream = new Stream();
        STREAMS.put(key, stream);
        PRELOAD.execute(() -> {
            CalmaConfig.scope(context.mode);
            try {
                if (stream.epoch != CalmaConfig.sessionId()) throw new IllegalStateException("Feed changed");
                stream.complete.complete(load(seed, context, anchor, source));
            } catch (Exception failure) { stream.complete.completeExceptionally(failure); }
            finally { CalmaConfig.scope(null); }
        });
        return null;
    }
    /** Never await preloading on a native response. Deliver only rows not already on screen. */
    static boolean remaining(Object response, Context context) throws ReflectiveOperationException {
        Stream stream = STREAMS.get(key(context));
        if (stream == null || stream.epoch != CalmaConfig.sessionId()
                || !stream.complete.isDone() || stream.complete.isCompletedExceptionally()) return false;
        Snapshot snapshot = stream.complete.getNow(null);
        if (snapshot == null || snapshot.epoch != CalmaConfig.contentId()) return false;
        synchronized (stream) {
            StockAccess.set(response, "A0S", unseen(snapshot.wrappers, true, stream.wrappers, false));
            StockAccess.set(response, "A0U", unseen(snapshot.media, false, stream.media, false));
        }
        finish(response);
        return true;
    }
    static void delivered(Object response, Context context, boolean head) throws ReflectiveOperationException {
        Stream stream = STREAMS.get(key(context));
        if (stream == null || stream.epoch != CalmaConfig.sessionId()) return;
        synchronized (stream) {
            Object w = StockAccess.get(response, "A0S"), m = StockAccess.get(response, "A0U");
            if (w instanceof List) StockAccess.set(response, "A0S", unseen((List<?>) w, true, stream.wrappers, head));
            if (m instanceof List) StockAccess.set(response, "A0U", unseen((List<?>) m, false, stream.media, head));
        }
    }
    private static List<Object> unseen(List<?> input, boolean wrapped, Set<String> seen, boolean head)
            throws ReflectiveOperationException {
        if (input == null) return null;
        List<Object> result = new ArrayList<>();
        for (Object row : input) {
            Object media = wrapped ? StockAccess.call(row, "A0A") : row;
            if (media == null) { if (head) result.add(row); continue; }
            String id = (String) StockAccess.call(StockAccess.get(media, "A04"), "getId");
            if (id != null && seen.add(id)) result.add(row);
        }
        return result;
    }
    static Snapshot load(Object first, Context context, long anchor) throws Exception {
        return load(first, context, anchor, NativeTimeline::fetch);
    }
    static Snapshot load(Object first, Context context, long anchor, PageSource source) throws Exception {
        String owner = NativeRelations.owner(context.session) + ':' + context.mode;
        if (NativeRelationLookup.mainThread()) {
            Snapshot cached = SAVED.get(owner);
            if (cached != null && cached.epoch == CalmaConfig.contentId()) return cached;
            throw new IllegalStateException("Timeline needs a network worker");
        }
        synchronized (LOCKS.computeIfAbsent(owner, ignored -> new Object())) {
            Snapshot previous = SAVED.get(owner);
            boolean refresh = refresh(context);
            if (!refresh && previous != null && previous.epoch == CalmaConfig.contentId()) return previous;
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
                if (epoch != CalmaConfig.sessionId()) throw new IllegalStateException("Feed changed during preloading");
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
                Map<Object,Object> params = args[18] == null ? new HashMap<>() : new HashMap<>((Map<?, ?>) args[18]);
                params.put("pagination_source", "following"); args[18] = params;
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
}
