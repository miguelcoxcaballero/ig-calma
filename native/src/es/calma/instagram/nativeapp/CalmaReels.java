package es.calma.instagram.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.TextView;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.WeakHashMap;

/** Original native port of reel-gate.js, pinned to Instagram 439.0.0.37.89. */
public final class CalmaReels {
    private static final Map<Activity, Boolean> decorated = new WeakHashMap<Activity, Boolean>();
    private static final AtomicLong launchSequence = new AtomicLong();
    private static final int CLIPS_TAB = 0x7f0b0c41;
    private CalmaReels() {}

    /** Native DM sender, not the author of the shared video, determines permission. */
    public static boolean allowLaunch(Object config, Object session) {
        if (!CalmaConfig.reels()) return true;
        if (config == null || session == null) return false;
        try {
            Object direct = read(config, "A0N");
            if (direct == null) return !locked();
            String sender = (String) read(direct, "A02");
            if (sender == null || sender.isEmpty() || !NativeRelations.isMutual(session, sender)) return false;
            return configureClip(config);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    /** Resolve a cold native relationship cache without blocking the UI or losing the tap. */
    public static boolean launchOrDefer(final String methodName, final Object[] arguments) {
        final long ticket = launchSequence.incrementAndGet();
        final int configIndex = "A09".equals(methodName) ? 2 : 1;
        if (arguments == null || arguments.length <= configIndex + 1) return false;
        final Object config = arguments[configIndex], session = arguments[configIndex + 1];
        if (allowLaunch(config, session)) { noteLaunch(config); return true; }
        final String sender;
        try {
            Object direct = read(config, "A0N");
            sender = direct == null ? null : (String) read(direct, "A02");
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        if (session == null || sender == null || sender.isEmpty()) return false;
        final Object host = arguments["A09".equals(methodName) ? 1 : 0];
        final Activity activity;
        try {
            activity = host instanceof Activity ? (Activity) host : (Activity) host.getClass().getMethod("getActivity").invoke(host);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        if (activity == null) return false;
        final long settings = CalmaConfig.sessionId();
        final long started = SystemClock.uptimeMillis();
        NativeRelations.resolveMutual(session, sender, new Runnable() {
            @Override public void run() {
                if (!NativeRelations.isMutual(session, sender)) return;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (ticket != launchSequence.get() || settings != CalmaConfig.sessionId()
                            || SystemClock.uptimeMillis() - started > 15000 || activity.isFinishing()
                            || activity.isDestroyed() || !activity.hasWindowFocus() || !allowLaunch(config, session)) return;
                        try {
                            try {
                                Object current = activity.getClass().getMethod("getSession").invoke(activity);
                                if (current != null && !NativeRelations.owner(current).equals(NativeRelations.owner(session))) return;
                            } catch (NoSuchMethodException unavailable) { }
                            if (!(host instanceof Activity) && !Boolean.TRUE.equals(host.getClass().getMethod("isAdded").invoke(host))) return;
                            Class<?> plugin = Class.forName("X.03zs", false, session.getClass().getClassLoader());
                            for (Method original : plugin.getDeclaredMethods()) {
                                if (original.getName().equals(methodName) && original.getParameterTypes().length == arguments.length) {
                                    original.invoke(null, arguments);
                                    return;
                                }
                            }
                        } catch (ReflectiveOperationException | RuntimeException unavailable) { }
                    }
                });
            }
        });
        return false;
    }

    /** Keep the supplied clip as the sole source and disable Instagram's chaining. */
    public static void configure(Object config) {
        if (locked() && config != null) configureClip(config);
    }

    private static boolean configureClip(Object config) {
        try {
            String mediaId = (String) read(config, "A1k");
            if (mediaId == null || mediaId.isEmpty()) return false;
            set(config, "A2u", true); // shouldForceDisableTailLoads
            set(config, "A27", false); // enableClipsBackwardsPagination
            set(config, "A28", false); // enableClipsDualPagination
            set(config, "A2o", false); // pullToRefreshEnabled
            set(config, "A2N", false); // isBlendAsASourceInDirectChainingEnabled
            set(config, "A2O", false); // isBlendRecsInDirectChainingEnabled
            set(config, "A20", true); // areTabsDisabled
            set(config, "A3b", false); // showUpsellOnLastItem
            set(config, "A2t", true); // shouldForceDisableFlashCache
            set(config, "A24", true); // disableSyncWithGridStore
            set(config, "A25", true); // disableViewerToGridStoreSync
            set(config, "A2n", false); // pullFromGridStoreOnGhost
            set(config, "A2r", false); // shouldConsiderPreviouslyInsertedItems
            set(config, "A3H", false); // syncMediaAfterHeadMediaLoad
            set(config, "A07", 0); // openedClipIndex
            set(config, "A1N", null); // optional media-list chaining source
            // Keep the existing, genuine Guava type expected by Instagram.
            Field sourceIds = config.getClass().getField("A0G");
            sourceIds.setAccessible(true);
            Object one = sourceIds.getType().getMethod("of", Object.class).invoke(null, mediaId);
            sourceIds.set(config, one);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    public static boolean allowBundle(Bundle arguments, Object session) {
        if (arguments == null) return !locked();
        try {
            arguments.setClassLoader(session.getClass().getClassLoader());
            Object config = arguments.getParcelable("ClipsViewerLauncher.KEY_CONFIG");
            boolean allowed = allowLaunch(config, session);
            if (allowed) noteLaunch(config);
            return allowed;
        } catch (RuntimeException unavailable) { return false; }
    }

    public static String restoredClass(String requested) {
        if (locked() && ("X.05Cb".equals(requested) || "X.01Co".equals(requested) || "X.0AF3".equals(requested))) {
            return "es.calma.instagram.nativeapp.BlockedReelsFragment";
        }
        return requested;
    }

    public static View blockedView(Context context) {
        TextView message = new TextView(context);
        boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        message.setText("Reels desactivados");
        message.setTextColor(dark ? 0xfff5f5f5 : 0xff262626);
        message.setBackgroundColor(dark ? 0xff000000 : 0xffffffff);
        message.setGravity(Gravity.CENTER);
        message.setTextSize(16);
        return message;
    }

    public static boolean locked() { return CalmaConfig.reels() && !NativeFeedBudget.reelsAllowed(); }
    public static boolean tabLocked() {
        boolean blocked = locked();
        if (!blocked) NativeFeedBudget.reelsLaunched();
        return blocked;
    }
    private static void noteLaunch(Object config) {
        try {
            if (config != null && read(config, "A0N") != null) NativeFeedBudget.directLaunched();
            else NativeFeedBudget.reelsLaunched();
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** Called only from Instagram's ClipsViewPagerImpl, never the DM or photo pager. */
    public static void lockPager(Object controller) {
        if (!locked() || controller == null) return;
        try {
            Object pager = read(controller, "A0A");
            if (pager != null) pager.getClass().getMethod("setUserInputEnabled", boolean.class).invoke(pager, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    public static boolean maySelect(int position) { return !locked() || position == 0; }

    /** Hide the stock Reels tab by its verified resource ID; no overlay is added. */
    public static void decorate(Activity activity) {
        if (activity == null || decorated.containsKey(activity)) return;
        final View root = activity.getWindow().getDecorView();
        final WeakReference<Activity> reference = new WeakReference<Activity>(activity);
        decorated.put(activity, Boolean.TRUE);
        final ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private int attempts;
            @Override public void onGlobalLayout() {
                Activity current = reference.get();
                if (current == null || current.isDestroyed() || ++attempts > 40) {
                    if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (current != null) decorated.remove(current);
                    return;
                }
                View tab = root.findViewById(CLIPS_TAB);
                if (tab != null) {
                    int visibility = locked() ? View.GONE : View.VISIBLE;
                    if (tab.getVisibility() != visibility) tab.setVisibility(visibility);
                    if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    decorated.remove(current);
                }
            }
        };
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        root.post(new Runnable() { @Override public void run() { listener.onGlobalLayout(); } });
    }

    private static Object read(Object instance, String name) throws ReflectiveOperationException {
        Field field = instance.getClass().getField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
    private static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        Field field = instance.getClass().getField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
