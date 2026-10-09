package es.calma.instagram.nativeapp;

import java.util.*;
import java.util.concurrent.*;

/** Collect the original controller's pages; never run another timeline HTTP transport. */
final class NativeTimelineProgress {
    private static final Map<String, Generation> GENERATIONS = new ConcurrentHashMap<>();
    private static final ExecutorService CACHE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CalmaTimelineCache"); t.setDaemon(true); return t;
    });
    private static final class Generation {
        final NativeTimeline.Context context;
        final long epoch, anchor, started = System.nanoTime();
        final List<Object> wrappers = new ArrayList<>(), media = new ArrayList<>();
        final Set<String> sentWrappers = new HashSet<>(), sentMedia = new HashSet<>(), cursors = new HashSet<>();
        final ChronologyState chronology = new ChronologyState();
        boolean hasWrappers, hasMedia, done, replay, replayed, diskChecked;
        String next;
        NativeTimeline.Snapshot snapshot;
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
            g.snapshot.apply(response); NativeFeedEnd.append(response, context.session); return;
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
            List<String> missing = NativeFeed.missing(g.wrappers, g.media, context.session, now);
            if (!missing.isEmpty()) NativeRelationLookup.refresh(context.session, missing).whenComplete((unused, failure) -> {
                CACHE.execute(() -> {
                    synchronized (generation) {
                        if (!current(key, generation) || !generation.done) return;
                        CalmaConfig.scope(generation.context.mode);
                        try {
                            if (complete(generation)) { generation.replay = true; NativeTimelinePager.wake(generation.context.session); }
                        } catch (ReflectiveOperationException | RuntimeException ignored) { /* Next refresh can retry. */ }
                        finally { CalmaConfig.scope(null); }
                    }
                });
            });
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
                CACHE.execute(() -> {
                    CalmaConfig.scope(context.mode);
                    try {
                        NativeTimeline.Snapshot disk = NativeTimelineStore.read(context, response, now);
                        synchronized (generation) {
                            if (disk != null && current(key, generation) && generation.snapshot == null) {
                                generation.snapshot = disk; generation.done = true; generation.replay = true;
                                NativeTimelinePager.wake(context.session);
                            }
                        }
                    } finally { CalmaConfig.scope(null); }
                });
            }
        }
    }
    private static boolean current(String key, Generation g) { return GENERATIONS.get(key) == g && g.epoch == CalmaConfig.sessionId(); }
    private static boolean complete(Generation g) throws ReflectiveOperationException {
        if (g.snapshot != null) return true;
        if (!NativeFeed.missing(g.wrappers, g.media, g.context.session, g.anchor).isEmpty()) return false;
        g.snapshot = new NativeTimeline.Snapshot(
            g.hasWrappers ? NativeFeed.filter(g.wrappers, true, g.anchor, true, g.context.session).items : null,
            g.hasMedia ? NativeFeed.filter(g.media, false, g.anchor, true, g.context.session).items : null, g.anchor);
        NativeTimeline.Snapshot saved = g.snapshot;
        CACHE.execute(() -> NativeTimelineStore.write(g.context, saved));
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
            if (g.epoch != CalmaConfig.sessionId() || System.nanoTime() - g.started > TimeUnit.SECONDS.toNanos(90)) return null;
            return g.done ? null : g.next;
        }
    }
    static boolean replay(Object session) {
        Generation g = GENERATIONS.get(key(session, CalmaConfig.mode()));
        if (g == null) return false;
        synchronized (g) {
            if (g.epoch != CalmaConfig.sessionId() || !g.replay || g.replayed || g.snapshot == null) return false;
            g.replayed = true; return true;
        }
    }
}
