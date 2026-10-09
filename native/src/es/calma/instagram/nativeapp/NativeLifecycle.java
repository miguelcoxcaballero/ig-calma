package es.calma.instagram.nativeapp;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Bundle;
import es.calma.instagram.UpdateActivity;

/** Attaches the existing updater to native Instagram Activity lifecycle events. */
final class NativeLifecycle implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    private final NativeUpdates updates;

    NativeLifecycle(Application application) {
        updates = new NativeUpdates(application);
    }

    @Override public void onActivityResumed(Activity activity) {
        // This screen can resume while Instagram is still installing its WebView provider.
        if (activity.getClass().getName().equals("com.instagram.process.asyncinit.IgSplashScreenActivity")) return;
        if (!(activity instanceof UpdateActivity)
                && activity.getClass().getName().startsWith("com.instagram.")) {
            CalmaConfig.init(activity.getApplicationContext());
            CalmaReels.decorate(activity);
            updates.onResumed(activity);
        }
    }
    @Override public void onActivityPaused(Activity activity) { updates.onPaused(activity); }
    @Override public void onActivityDestroyed(Activity activity) { updates.onDestroyed(activity); }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onConfigurationChanged(Configuration configuration) {}
    @Override public void onLowMemory() { updates.onMemoryPressure(); }
    @Override public void onTrimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) updates.onMemoryPressure();
    }
}
