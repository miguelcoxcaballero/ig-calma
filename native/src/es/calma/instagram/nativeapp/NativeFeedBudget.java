package es.calma.instagram.nativeapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

/** Persistent earnings shared across accounts; only foreground For you surfaces spend them. */
public final class NativeFeedBudget {
    private static HourlyCredits credits;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static WeakReference<Object> home = new WeakReference<>(null);
    private static boolean homeResumed, active, reelChain, running;
    private static long wall, elapsed, persisted;
    private static long launchTime;
    private static boolean tabAllowed;
    private NativeFeedBudget() {}
    public static synchronized void init() {
        if (credits != null || CalmaConfig.preferences() == null) return;
        long now = System.currentTimeMillis();
        credits = HourlyCredits.restore(CalmaConfig.preferences().getString("for_you_credits", null), now, 1);
        wall = now; elapsed = SystemClock.elapsedRealtime(); save();
    }
    private static void save() {
        if (credits != null) CalmaConfig.preferences().edit().putString("for_you_credits", credits.save()).apply();
        persisted = SystemClock.elapsedRealtime();
    }
    public static synchronized long balance() { settle(); return credits == null ? 0 : credits.balance(System.currentTimeMillis()); }
    public static synchronized int rate() { init(); return credits == null ? 1 : credits.rate(); }
    public static synchronized void rate(int minutes) {
        init(); settle(); if (credits != null) { credits.rate(System.currentTimeMillis(), minutes); save(); }
        NativeFeedSelector.refreshBalance();
    }
    public static String label(long millis) {
        if (millis <= 0) return "0 min · Bloqueado";
        long seconds = (millis + 999) / 1000;
        return String.format(Locale.ROOT, "%d:%02d min", seconds / 60, seconds % 60);
    }
    public static synchronized void settle() {
        init(); if (credits == null) return;
        long now = System.currentTimeMillis(), mono = SystemClock.elapsedRealtime();
        if (active) credits.spend(wall, now, Math.max(0, mono - elapsed)); else credits.balance(now);
        wall = now; elapsed = mono;
    }
    public static void homeResumed(Object fragment) { settle(); home = new WeakReference<>(fragment); homeResumed = true; changed(); }
    public static void homePaused(Object fragment) { settle(); if (home.get() == fragment) homeResumed = false; changed(); }
    public static void homeHidden(Object fragment, boolean hidden) {
        if (hidden) homePaused(fragment); else homeResumed(fragment);
    }
    static void resumed(Activity current) {
        settle(); activity = new WeakReference<>(current);
        if (!running) { running = true; handler.post(tick); }
        changed();
    }
    static void paused(Activity current) {
        if (activity.get() != current) return;
        settle(); active = false; save(); activity.clear();
        running = false; handler.removeCallbacks(tick);
    }
    private static boolean homeVisible() {
        Object fragment = home.get();
        if (!homeResumed || fragment == null) return false;
        try {
            if (!Boolean.TRUE.equals(StockAccess.call(fragment, "isResumed"))
                    || Boolean.TRUE.equals(StockAccess.call(fragment, "isHidden"))
                    || !Boolean.TRUE.equals(StockAccess.call(fragment, "getUserVisibleHint"))
                    || !Boolean.TRUE.equals(StockAccess.call(fragment, "isMenuVisible"))) return false;
            View view = (View) StockAccess.call(fragment, "getView"); Rect rect = new Rect();
            // The Home and DM pages can both be resumed during a horizontal swipe.
            return view != null && view.isShown() && view.getGlobalVisibleRect(rect)
                    && rect.width() >= view.getWidth() * .9f;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }
    private static boolean clips(Object manager, int depth) throws ReflectiveOperationException {
        if (depth > 8) return false;
        for (Object f : (List<?>) StockAccess.call(manager, "getFragments")) {
            if (f == null || !Boolean.TRUE.equals(StockAccess.call(f, "isResumed"))
                    || Boolean.TRUE.equals(StockAccess.call(f, "isHidden"))
                    || !Boolean.TRUE.equals(StockAccess.call(f, "getUserVisibleHint"))) continue;
            String name = f.getClass().getName();
            if (name.equals("X.05Cb") || name.equals("X.01Co") || name.equals("X.0AF3")) return true;
            if (clips(StockAccess.call(f, "getChildFragmentManager"), depth + 1)) return true;
        }
        return false;
    }
    private static boolean clipVisible(Activity current) {
        try { return clips(StockAccess.call(current, "getSupportFragmentManager"), 0); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }
    /** A DM opened while For you remains selected still follows the single-clip friends rule. */
    public static boolean reelsAllowed() {
        Activity current = activity.get();
        return "BLENDED_FOR_YOU".equals(CalmaConfig.feed()) && balance() > 0 && (homeVisible()
                || (reelChain && ((current != null && clipVisible(current)) || SystemClock.elapsedRealtime() - launchTime < 2000)));
    }
    public static void reelsLaunched() { if (reelsAllowed()) { reelChain = true; launchTime = SystemClock.elapsedRealtime(); } }
    static void changed() {
        Activity current = activity.get();
        boolean forYou = "BLENDED_FOR_YOU".equals(CalmaConfig.feed());
        boolean video = current != null && reelChain && clipVisible(current);
        if (!forYou || (homeVisible() && !video) || (!video && SystemClock.elapsedRealtime() - launchTime >= 2000)) reelChain = false;
        active = current != null && current.hasWindowFocus() && forYou && balance() > 0 && (homeVisible() || video);
        boolean allowed = forYou && balance() > 0;
        if (current != null && allowed != tabAllowed) { tabAllowed = allowed; CalmaReels.decorate(current); }
    }
    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            settle(); Activity current = activity.get();
            if ("BLENDED_FOR_YOU".equals(CalmaConfig.feed()) && balance() <= 0) {
                boolean video = current != null && reelChain && clipVisible(current);
                active = false; reelChain = false;
                if (video) current.onBackPressed();
                NativeFeedSelector.expired(); save();
            }
            changed(); NativeFeedSelector.refreshBalance();
            if (SystemClock.elapsedRealtime() - persisted >= 1000) save();
            handler.postDelayed(this, 500);
        }
    };
}
