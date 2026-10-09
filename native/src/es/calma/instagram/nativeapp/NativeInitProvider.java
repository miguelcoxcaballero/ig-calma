package es.calma.instagram.nativeapp;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.util.concurrent.atomic.AtomicBoolean;

/** Main-process initialization; no change to Instagram's Application class. */
public final class NativeInitProvider extends ContentProvider {
    private static final AtomicBoolean installed = new AtomicBoolean();

    @Override public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        Context application = context.getApplicationContext();
        if (!(application instanceof Application)) return false;
        if (installed.compareAndSet(false, true)) {
            CalmaConfig.init(application);
            NativeFeedBudget.init();
            NativeLifecycle lifecycle = new NativeLifecycle((Application) application);
            ((Application) application).registerActivityLifecycleCallbacks(lifecycle);
            application.registerComponentCallbacks(lifecycle);
        }
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
}
