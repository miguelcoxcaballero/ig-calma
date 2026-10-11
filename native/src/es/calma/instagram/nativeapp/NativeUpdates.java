// Shared updater: users default to B; A requires warning acknowledgement and slide confirmation.
// Automatic releases go to A; B publication or promotion requires an explicit user request.
// A includes A+B and offers the highest global versionCode; B includes only B.
// Edit canonical inhouse-services-updater and sync all five apps.
package es.calma.instagram.nativeapp;
/** Lifecycle adapter only; canonical updater is github.com/miguelcoxcaballero/inhouse-services-updater.
 * Sync all five apps after updater changes. Every release goes to A with tiny changelog; B needs explicit user request. */
public final class NativeUpdates {
    private final com.inhouse.services.updater.NativeUpdates updater;
    NativeUpdates(android.content.Context context){updater=new com.inhouse.services.updater.NativeUpdates(context);}
    void onResumed(android.app.Activity activity){updater.onResumed(activity);}
    void onPaused(android.app.Activity activity){updater.onPaused(activity);}
    void onDestroyed(android.app.Activity activity){updater.onDestroyed(activity);}
    void onMemoryPressure(){updater.onMemoryPressure();}
}
