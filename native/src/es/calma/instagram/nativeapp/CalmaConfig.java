package es.calma.instagram.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.atomic.AtomicLong;

/** The same local preferences as the web client, without a web dependency. */
public final class CalmaConfig {
    private static volatile SharedPreferences preferences;
    private static volatile Context application;
    private static final AtomicLong session = new AtomicLong(1);

    private CalmaConfig() {}

    public static synchronized void init(Context context) {
        if (preferences != null || context == null) return;
        application = context.getApplicationContext();
        preferences = application.getSharedPreferences("calma", Context.MODE_PRIVATE);
        if (!preferences.getBoolean("native_friends_feed_initialized", false)) {
            preferences.edit().putInt("mode", 2).remove("limit")
                    .putBoolean("native_48h_initialized", true)
                    .putBoolean("native_friends_feed_initialized", true).apply();
        }
    }

    public static Context context() { return application; }
    /** The Home feed always requires mutual follow, including before preferences load. */
    public static int mode() { return 2; }

    public static boolean reels() {
        SharedPreferences p = preferences;
        return p == null || p.getBoolean("reels", true);
    }
    public static void setReels(boolean value) {
        SharedPreferences p = preferences;
        if (p != null && reels() != value) { p.edit().putBoolean("reels", value).apply(); newSession(); }
    }
    public static long sessionId() { return session.get(); }
    public static long feedWindowSeconds() { return 48L * 60L * 60L; }
    public static void newSession() { session.incrementAndGet(); }
}
