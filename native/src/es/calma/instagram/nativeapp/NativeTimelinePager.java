package es.calma.instagram.nativeapp;

import android.os.Handler;
import android.os.Looper;
import android.graphics.Rect;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Replay already-loaded rows locally. Only Instagram's normal scrolling loads another page. */
public final class NativeTimelinePager {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, WeakReference<Object>> CONTROLLERS = new ConcurrentHashMap<>();
    private static final Map<String, Object> HEADS = new ConcurrentHashMap<>();
    private static final Map<String, Long> EPOCHS = new ConcurrentHashMap<>();
    private static final Set<String> QUEUED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private NativeTimelinePager() {}
    /** Visibility is observed only for UI state, never to trigger a network request. */
    static boolean visible(Object controller) {
        if (!CalmaConfig.enabled()) return false;
        try {
            Object fragment=StockAccess.get(StockAccess.get(controller,"A0Y"),"A01");
            if (!Boolean.TRUE.equals(StockAccess.call(fragment,"isResumed"))
                    || Boolean.TRUE.equals(StockAccess.call(fragment,"isHidden"))) return false;
            View view=(View)StockAccess.call(fragment,"getView"); Rect area=new Rect();
            return view!=null && view.getWindowVisibility()==View.VISIBLE && view.isShown()
                    && view.getGlobalVisibleRect(area) && view.getWidth()>0 && area.width()>=view.getWidth()*.9f;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    public static void delivery(Object controller, Object request, boolean head) {
        if (!CalmaConfig.enabled()) return;
        try {
            Object session = StockAccess.get(controller,"A0X"); String owner = NativeRelations.owner(session);
            CONTROLLERS.put(owner,new WeakReference<>(controller));
            if (head || !Long.valueOf(CalmaConfig.sessionId()).equals(EPOCHS.get(owner))) {
                HEADS.put(owner,request); EPOCHS.put(owner,CalmaConfig.sessionId());
            }
            wake(session);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
    public static void completed(Object controller) {
        if (!CalmaConfig.enabled()) return;
        try {
            Object session = StockAccess.get(controller, "A0X");
            CONTROLLERS.put(NativeRelations.owner(session), new WeakReference<>(controller));
            wake(session);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
    static void visible() {
        if (!CalmaConfig.enabled()) return;
        for (WeakReference<Object> ref : CONTROLLERS.values()) {
            Object controller = ref.get();
            if (controller != null) try { wake(StockAccess.get(controller, "A0X")); }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
    }
    static void wake(Object session) {
        if (!CalmaConfig.enabled()) return;
        String owner = NativeRelations.owner(session);
        long epoch = CalmaConfig.sessionId();
        if (owner.isEmpty() || !QUEUED.add(owner)) return;
        MAIN.postDelayed(() -> {
            QUEUED.remove(owner);
            if (!CalmaConfig.enabled() || epoch != CalmaConfig.sessionId() || CalmaConfig.mode() == 0) return;
            WeakReference<Object> ref = CONTROLLERS.get(owner);
            Object controller = ref == null ? null : ref.get();
            if (controller == null) return;
            try {
                if (!NativeTimelineProgress.replay(session)
                        || !Long.valueOf(epoch).equals(EPOCHS.get(owner))) return;
                ClassLoader loader = controller.getClass().getClassLoader();
                List<?> rows = NativeTimelineProgress.local(session);
                if (!rows.isEmpty() && HEADS.get(owner) != null) {
                    // Original LOCAL delivery updates the adapter without another HTTP request.
                    StockAccess.method(controller.getClass(),"A0E",Class.forName("X.08KU",false,loader),List.class,boolean.class,boolean.class)
                        .invoke(controller,HEADS.get(owner),rows,true,true);
                    NativeTimelineProgress.rendered(session);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* Native scrolling remains available. */ }
        }, 0);
    }
}
