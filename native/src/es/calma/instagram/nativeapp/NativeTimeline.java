package es.calma.instagram.nativeapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local snapshots of pages delivered by Instagram. This class cannot issue requests. */
final class NativeTimeline {
    static final class Context {
        final Object androidContext, session, request, parameters;
        final int mode = CalmaConfig.mode();
        Context(Object context, Object session, Object request, Object parameters) {
            this.androidContext = context; this.session = session; this.request = request; this.parameters = parameters;
        }
    }
    static final class Snapshot {
        final List<Object> wrappers, media;
        final long anchor, epoch;
        Snapshot(List<Object> wrappers, List<Object> media, long anchor) {
            this.wrappers = wrappers == null ? null : Collections.unmodifiableList(new ArrayList<>(wrappers));
            this.media = media == null ? null : Collections.unmodifiableList(new ArrayList<>(media));
            this.anchor = anchor; this.epoch = CalmaConfig.contentId();
        }
        void apply(Object response, Object session) throws ReflectiveOperationException {
            // A completed snapshot preserves posts, not permission to show their authors forever.
            long now = System.currentTimeMillis() / 1000L;
            StockAccess.set(response, "A0S", wrappers == null ? null : NativeFeed.filter(wrappers, true, now, true, session).items);
            StockAccess.set(response, "A0U", media == null ? null : NativeFeed.filter(media, false, now, true, session).items);
            finish(response);
        }
    }
    private NativeTimeline() {}
    static void finish(Object response) throws ReflectiveOperationException {
        StockAccess.set(response, "A0a", false);
        StockAccess.set(response, "A0W", false);
        StockAccess.set(response, "A0N", null);
    }

}
