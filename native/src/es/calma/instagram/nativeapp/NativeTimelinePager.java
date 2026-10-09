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
    private static final Map<String, Object> HEADS = new ConcurrentHashMap<>();
    private static final Map<String, Long> EPOCHS = new ConcurrentHashMap<>();
    private static final Set<String> QUEUED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private NativeTimelinePager() {}
    public static void delivery(Object controller, Object request, boolean head) {
        try {
            Object session = StockAccess.get(controller,"A0X"); String owner = NativeRelations.owner(session);
            CONTROLLERS.put(owner,new WeakReference<>(controller));
            if (head || !Long.valueOf(CalmaConfig.sessionId()).equals(EPOCHS.get(owner))) {
                HEADS.put(owner,request); EPOCHS.put(owner,CalmaConfig.sessionId());
                ATTEMPTED.remove(owner);
            }
            wake(session);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
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
                ClassLoader loader = controller.getClass().getClassLoader();
                if (replay && Long.valueOf(epoch).equals(EPOCHS.get(owner))) {
                    List<?> rows = NativeTimelineProgress.local(session);
                    if (!rows.isEmpty() && HEADS.get(owner) != null) {
                        // Original LOCAL delivery updates the adapter without another HTTP request.
                        StockAccess.method(controller.getClass(),"A0E",Class.forName("X.08KU",false,loader),List.class,boolean.class,boolean.class)
                            .invoke(controller,HEADS.get(owner),rows,true,true);
                        NativeTimelineProgress.rendered(session);
                    }
                }
                if (cursor == null) return;
                String attempt = epoch + ":" + cursor;
                if (attempt.equals(ATTEMPTED.get(owner))) return;
                Class<?> reasonType = Class.forName("X.02pk", false, loader);
                Object reason = reasonType.getField("A0R").get(null);
                Object trigger = Class.forName("X.08cS", false, loader).getConstructor(String.class).newInstance("calma_timeline_preload");
                Map<String,String> params = Collections.singletonMap("pagination_source", "following");
                // GRO, called by A0J, owns the native in-flight/cursor guards. A rejected
                // request is left to the next stock completion or normal scroll retry.
                Object accepted = StockAccess.method(controller.getClass(), "A0J", Class.forName("X.0AHw", false, loader), reasonType, String.class, Map.class)
                    .invoke(controller, trigger, reason, cursor, params);
                if (Boolean.TRUE.equals(accepted)) ATTEMPTED.put(owner,attempt);
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* Native scroll remains available. */ }
        }, 120);
    }
}
