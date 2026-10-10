package es.calma.instagram.nativeapp;

import java.util.*;
import java.util.concurrent.*;

/** Collect the original controller's pages; never run another timeline HTTP transport. */
final class NativeTimelineProgress {
    private static final Map<String, Generation> GENERATIONS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CACHE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CalmaTimelineRelations"); t.setDaemon(true); return t;
    });
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CalmaTimelineCache"); t.setDaemon(true); return t;
    });
    private static final Set<String> RELATION_UPDATES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final class Generation {
        final NativeTimeline.Context context;
        final long epoch, anchor;
        final List<Object> wrappers = new ArrayList<>(), media = new ArrayList<>();
        final Set<String> sentWrappers = new HashSet<>(), sentMedia = new HashSet<>(), cursors = new HashSet<>();
        final ChronologyState chronology = new ChronologyState();
        boolean hasWrappers, hasMedia, done, replay, diskChecked, resolving;
        int retries;
        String next;
        volatile NativeTimeline.Snapshot snapshot;
        Generation(NativeTimeline.Context context, long epoch, long anchor) {
            this.context = context; this.epoch = epoch; this.anchor = anchor;
        }
    }
    private NativeTimelineProgress() {}
    private static String key(Object session, int mode) { return NativeRelations.owner(session) + ':' + mode; }
    static void response(Object response, NativeTimeline.Context context, boolean head, long epoch, long now) throws ReflectiveOperationException {
        String key = key(context.session, context.mode);
        Generation g = GENERATIONS.get(key);
        String reason = String.valueOf(StockAccess.get(context.request, "A09"));
        boolean refresh = reason.equals("pull_to_refresh") || reason.equals("pill_refresh") || reason.equals("new_follow");
        if (head && g != null && g.snapshot != null && !refresh && g.snapshot.epoch == CalmaConfig.contentId()) {
            List<String> missing = NativeFeed.missing(g.wrappers, g.media, context.session, now);
            if (missing.isEmpty()) {
                g.snapshot.apply(response, context.session); NativeFeedEnd.append(response, context.session); return;
            }
            // Expired friendship proof requires a fresh generation; never reuse its old positives.

        }
        if (head || g == null || g.epoch != epoch) {
            g = new Generation(context, epoch, now); GENERATIONS.put(key, g);
        }
        final Generation generation = g;
        synchronized (g) {
            if (epoch != CalmaConfig.sessionId()) { NativeTimeline.finish(response); return; }
            Object w = StockAccess.get(response, "A0S"), m = StockAccess.get(response, "A0U");
            List<?> wl = w instanceof List ? (List<?>) w : Collections.emptyList();
            List<?> ml = m instanceof List ? (List<?>) m : Collections.emptyList();
            g.hasWrappers |= w instanceof List; g.hasMedia |= m instanceof List;
            for (Object row : wl) {
                try { if (row != null && (head || StockAccess.call(row, "A0A") != null)) g.wrappers.add(row); }
                catch (ReflectiveOperationException | RuntimeException invalidRow) { /* Other rows remain usable. */ }
            }
            g.media.addAll(ml);
            resolve(key,g);
            // Deliver already-known friends immediately. Unresolved authors remain in the
            // accumulator and are reconsidered on the next page or the completion replay.
            if (g.hasWrappers) StockAccess.set(response, "A0S", delta(NativeFeed.filter(g.wrappers, true, now, true, context.session).items, true, g.sentWrappers, head));
            if (g.hasMedia) StockAccess.set(response, "A0U", delta(NativeFeed.filter(g.media, false, now, true, context.session).items, false, g.sentMedia, head));
            boolean more = Boolean.TRUE.equals(StockAccess.get(response, "A0a"));
            String cursor = (String) StockAccess.get(response, "A0N");
            boolean boundary = NativeFeed.oldPage(wl, ml, now, g.chronology, head);
            g.done = !more || boundary;
            g.next = !g.done && cursor != null && !cursor.isEmpty() && g.cursors.add(cursor) ? cursor : null;
            if (g.done) {
                NativeTimeline.finish(response);
                if (complete(g)) NativeFeedEnd.append(response, context.session);
                else NativeFeedEnd.appendStatus(response, context.session, false);
            }
            if (head && !refresh && !g.diskChecked && !g.done) {
                g.diskChecked = true;
                // Disk decoding and serialization must not hold the native parser/UI.
                DISK.execute(() -> {
                    CalmaConfig.scope(context.mode);
                    try {
                        NativeTimeline.Snapshot disk = NativeTimelineStore.read(context, response, now);
                        synchronized (generation) {
                            if (disk != null && current(key, generation) && generation.snapshot == null) {
                                // Disk contains posts, not current friendship proof. Merge it
                                // into the same accumulator and resolve authors in batches.
                                if (disk.wrappers != null) {
                                    for (Object row : disk.wrappers) {
                                        try { if (StockAccess.call(row,"A0A") != null) generation.wrappers.add(row); }
                                        catch (ReflectiveOperationException | RuntimeException invalidRow) { }
                                    }
                                    generation.hasWrappers = true;
                                }
                                if (disk.media != null) { generation.media.addAll(disk.media); generation.hasMedia = true; }
                                resolve(key, generation);
                                generation.replay = true;
                                NativeTimelinePager.wake(context.session);
                            }
                        }
                    } finally { CalmaConfig.scope(null); }
                });
            }
        }
    }
    /** Deliver each parsed batch's useful results without waiting for other requests or disk IO. */
    static void relationshipChanged(String owner) {
        if (!RELATION_UPDATES.add(owner)) return;
        CACHE.schedule(() -> {
            RELATION_UPDATES.remove(owner);
            for (Map.Entry<String,Generation> entry : GENERATIONS.entrySet()) {
                Generation g=entry.getValue();
                if (g.context.mode!=2 || !entry.getKey().startsWith(owner+':')) continue;
                synchronized (g) {
                    if (!current(entry.getKey(),g)) continue;
                    CalmaConfig.scope(g.context.mode);
                    try {
                        if (g.done) complete(g);
                        g.replay=true;
                        NativeTimelinePager.wake(g.context.session);
                    } catch (ReflectiveOperationException | RuntimeException ignored) { }
                    finally { CalmaConfig.scope(null); }
                }
            }
        },30,TimeUnit.MILLISECONDS);
    }
    private static void resolve(String key, Generation g) {
        if (g.resolving) return;
        List<String> missing = NativeFeed.missing(g.wrappers,g.media,g.context.session,g.anchor);
        if (missing.isEmpty()) return;
        g.resolving = true;
        NativeRelationLookup.refresh(g.context.session,missing).whenComplete((unused,failure) -> CACHE.execute(() -> {
            synchronized (g) {
                g.resolving = false;
                if (!current(key,g)) return;
                CalmaConfig.scope(g.context.mode);
                try {
                    // Publish partial successes now, even while later pages are in flight.
                    if (g.done) complete(g);
                    g.replay = true; NativeTimelinePager.wake(g.context.session);
                    if (failure == null || g.retries++ < 2) CACHE.schedule(() -> {
                        synchronized (g) {
                            if (!current(key,g)) return;
                            CalmaConfig.scope(g.context.mode);
                            try { resolve(key,g); } finally { CalmaConfig.scope(null); }
                        }
                    },failure == null ? 0 : 3200,TimeUnit.MILLISECONDS);
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
                finally { CalmaConfig.scope(null); }
            }
        }));
    }
    private static boolean current(String key, Generation g) { return GENERATIONS.get(key) == g && g.epoch == CalmaConfig.sessionId(); }
    private static boolean complete(Generation g) throws ReflectiveOperationException {
        if (!NativeFeed.missing(g.wrappers, g.media, g.context.session, g.anchor).isEmpty()) return false;
        g.snapshot = new NativeTimeline.Snapshot(
            g.hasWrappers ? NativeFeed.filter(g.wrappers, true, g.anchor, true, g.context.session).items : null,
            g.hasMedia ? NativeFeed.filter(g.media, false, g.anchor, true, g.context.session).items : null, g.anchor);
        NativeTimeline.Snapshot saved = g.snapshot;
        DISK.execute(() -> NativeTimelineStore.write(g.context, saved));
        return true;
    }
    private static List<Object> delta(List<Object> rows, boolean wrapped, Set<String> sent, boolean head) {
        List<Object> result = new ArrayList<>();
        for (Object row : rows) {
            try {
                Object media = wrapped ? StockAccess.call(row, "A0A") : row;
                if (media == null) { if (head) result.add(row); continue; }
                String id = (String) StockAccess.call(StockAccess.get(media, "A04"), "getId");
                if (id != null && sent.add(id)) result.add(row);
            } catch (ReflectiveOperationException | RuntimeException invalidRow) { /* Skip one malformed row. */ }
        }
        return result;
    }
    static String next(Object session) {
        Generation g = GENERATIONS.get(key(session, CalmaConfig.mode()));
        if (g == null) return null;
        synchronized (g) {
            if (g.epoch != CalmaConfig.sessionId()) return null;
            return g.done ? null : g.next;
        }
    }
    static boolean replay(Object session) {
        Generation g = GENERATIONS.get(key(session, CalmaConfig.mode()));
        if (g == null) return false;
        synchronized (g) {
            return g.epoch == CalmaConfig.sessionId() && g.replay;
        }
    }
    static final class ViewPage {
        public List<Object> A0S, A0U;
        public boolean A0a,A0W;
        public String A0N;
    }
    static List<?> local(Object session) throws ReflectiveOperationException {
        Generation g = GENERATIONS.get(key(session,CalmaConfig.mode()));
        if (g == null) return Collections.emptyList();
        synchronized (g) {
            if (!current(key(session,CalmaConfig.mode()),g)) return Collections.emptyList();
            ViewPage page = new ViewPage();
            page.A0S = g.hasWrappers ? NativeFeed.filter(g.wrappers,true,g.anchor,true,session).items : null;
            page.A0U = g.hasMedia ? NativeFeed.filter(g.media,false,g.anchor,true,session).items : null;
            if (g.snapshot != null) g.snapshot.apply(page, session);
            if (g.done) NativeFeedEnd.appendStatus(page,session,g.snapshot != null);
            List<?> rows = page.A0S;
            if (rows == null && page.A0U != null) rows = (List<?>) StockAccess.method(Class.forName("X.04ss",false,session.getClass().getClassLoader()),"A00",List.class).invoke(null,page.A0U);
            return rows == null ? Collections.emptyList() : rows;
        }
    }
    static void rendered(Object session) throws ReflectiveOperationException {
        Generation g = GENERATIONS.get(key(session,CalmaConfig.mode()));
        if (g == null) return;
        synchronized (g) {
            if (!current(key(session,CalmaConfig.mode()),g)) return;
            delta(NativeFeed.filter(g.wrappers,true,g.anchor,true,session).items,true,g.sentWrappers,false);
            delta(NativeFeed.filter(g.media,false,g.anchor,true,session).items,false,g.sentMedia,false);
            g.replay = false;
        }
    }
}
