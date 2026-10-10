package es.calma.instagram.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.atomic.AtomicLong;

/** The same local preferences as the web client, without a web dependency. */
public final class CalmaConfig {
    private static volatile SharedPreferences preferences;
    private static volatile Context application;
    private static final AtomicLong session = new AtomicLong(1);
    private static final AtomicLong content = new AtomicLong(1);
    private static volatile String feed = "FOLLOWING";
    private static final ThreadLocal<Integer> responseMode = new ThreadLocal<>();

    private CalmaConfig() {}

    public static synchronized void init(Context context) {
        if (preferences != null || context == null) return;
        application = context.getApplicationContext();
        preferences = application.getSharedPreferences("calma", Context.MODE_PRIVATE);
        if (!preferences.getBoolean("native_following_feed_initialized", false)) {
            SharedPreferences.Editor edit = preferences.edit().putInt("mode", 1).remove("limit");
            for (java.util.Map.Entry<String, ?> entry : preferences.getAll().entrySet())
                if (entry.getKey().startsWith("feed:") && "RECENTS".equals(entry.getValue()))
                    edit.putString(entry.getKey(), "FOLLOWING");
            edit.putBoolean("native_48h_initialized", true)
                    .putBoolean("native_following_feed_initialized", true).apply();
        }
    }

    public static Context context() { return application; }
    /** Legacy selections also resolve to Following after upgrading. */
    public static int mode() {
        Integer scoped = responseMode.get();
        return scoped != null ? scoped : modeFor(feed);
    }
    static int modeFor(String name) { return "FOLLOWING".equals(normalizeFeed(name)) ? 1 : 0; }
    static void scope(Integer mode) { if (mode == null) responseMode.remove(); else responseMode.set(mode == 2 ? 1 : mode); }
    public static String feed() { return feed; }
    static String normalizeFeed(String name) { return "RECENTS".equals(name) ? "FOLLOWING" : name; }
    static synchronized void select(String name) {
        name = normalizeFeed(name);
        if (!feed.equals(name)) { feed = name; newSession(); }
    }
    static SharedPreferences preferences() { return preferences; }

    public static boolean reels() {
        SharedPreferences p = preferences;
        return p == null || p.getBoolean("reels", true);
    }
    public static void setReels(boolean value) {
        SharedPreferences p = preferences;
        if (p != null && reels() != value) { p.edit().putBoolean("reels", value).apply(); content.incrementAndGet(); newSession(); }
    }
    public static long sessionId() { return session.get(); }
    public static long contentId() { return content.get(); }
    public static long feedWindowSeconds() { return 48L * 60L * 60L; }
    public static void newSession() { session.incrementAndGet(); }
}
