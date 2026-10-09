package es.calma.instagram.nativeapp;

import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Advance only the stock controller, after its original completion callback. */
public final class NativeTimelinePager {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, WeakReference<Object>> CONTROLLERS = new ConcurrentHashMap<>();
    private static final Map<String, String> ATTEMPTED = new ConcurrentHashMap<>();
    private static final Set<String> QUEUED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private NativeTimelinePager() {}
    public static void completed(Object controller) {
        try {
            Object session = StockAccess.get(controller, "A0X");
            CONTROLLERS.put(NativeRelations.owner(session), new WeakReference<>(controller));
            wake(session);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
    static void visible() {
        for (WeakReference<Object> ref : CONTROLLERS.values()) {
            Object controller = ref.get();
            if (controller != null) try { wake(StockAccess.get(controller, "A0X")); }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
    }
    static void wake(Object session) {
        String owner = NativeRelations.owner(session);
        long epoch = CalmaConfig.sessionId();
        if (owner.isEmpty() || !QUEUED.add(owner)) return;
        MAIN.postDelayed(() -> {
            QUEUED.remove(owner);
            if (epoch != CalmaConfig.sessionId() || CalmaConfig.mode() == 0 || !NativeFeedBudget.timelineVisible()) return;
            WeakReference<Object> ref = CONTROLLERS.get(owner);
            Object controller = ref == null ? null : ref.get();
            if (controller == null) return;
            try {
                String cursor = NativeTimelineProgress.next(session);
                boolean replay = NativeTimelineProgress.replay(session);
                if (cursor == null && !replay) return;
                String attempt = epoch + ":" + (replay ? "head" : cursor);
                if (attempt.equals(ATTEMPTED.put(owner, attempt))) return;
                ClassLoader loader = controller.getClass().getClassLoader();
                Class<?> reasonType = Class.forName("X.02pk", false, loader);
                Object reason = reasonType.getField(replay ? "A0J" : "A0R").get(null);
                Object trigger = Class.forName("X.08cS", false, loader).getConstructor(String.class).newInstance("calma_timeline_preload");
                Map<String,String> params = Collections.singletonMap("pagination_source", "following");
                // GRO, called by A0J, owns the native in-flight/cursor guards. A rejected
                // request is left to the next stock completion or normal scroll retry.
                StockAccess.method(controller.getClass(), "A0J", Class.forName("X.0AHw", false, loader), reasonType, String.class, Map.class)
                    .invoke(controller, trigger, reason, replay ? null : cursor, params);
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* Native scroll remains available. */ }
        }, 120);
    }
}
