package es.calma.instagram.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/** Original native port of reel-gate.js, pinned to Instagram 439.0.0.37.89. */
public final class CalmaReels {
    private static final Map<Activity, Boolean> decorated = new WeakHashMap<Activity, Boolean>();
    private static final List<Original> originals = new ArrayList<>();
    private static final String[] PIN_FIELDS = {"A1k","A2u","A27","A28","A2o","A2N","A2O","A20","A3b","A2t","A24","A25","A2n","A2r","A3H","A07","A1N","A0G"};
    private static final class Original {
        final WeakReference<Object> config;
        final Object[] values = new Object[PIN_FIELDS.length];
        Original(Object config) throws ReflectiveOperationException {
            this.config = new WeakReference<>(config);
            for (int i=0;i<values.length;i++) values[i]=read(config,PIN_FIELDS[i]);
        }
        void restore(Object target) throws ReflectiveOperationException {
            for (int i=0;i<values.length;i++) set(target,PIN_FIELDS[i],values[i]);
        }
    }
    private static Original original(Object config, boolean create) throws ReflectiveOperationException {
        synchronized (originals) {
            for (Iterator<Original> it=originals.iterator();it.hasNext();) {
                Original saved=it.next();Object target=saved.config.get();
                if(target==null)it.remove();else if(target==config)return saved;
            }
            if(!create)return null;
            Original saved=new Original(config);originals.add(saved);return saved;
        }
    }
    private static boolean restore(Object config) {
        try {
            Original saved=original(config,false);
            if(saved!=null){saved.restore(config);synchronized(originals){originals.remove(saved);}}
            return true;
        } catch(ReflectiveOperationException | RuntimeException unavailable){return false;}
    }
    private static final int CLIPS_TAB = 0x7f0b0c41;
    private CalmaReels() {}

    private static boolean single(Object config) throws ReflectiveOperationException {
        return CalmaConfig.reels() && (read(config,"A0N")!=null || !NativeFeedBudget.reelsAllowed());
    }
    /** Prefetch must not pin a config before the user selects a feed or spends credits. */
    public static boolean allowPrefetch(Object config,Object session) {
        if (!CalmaConfig.reels()) return true;
        if (config==null || session==null) return false;
        try { return !single(config) || selectedId(config)!=null; }
        catch(ReflectiveOperationException | RuntimeException unavailable){return false;}
    }
    /** Any explicitly opened clip can play; only the next/previous clip is restricted. */
    public static boolean allowLaunch(Object config, Object session) {
        if (!CalmaConfig.reels()) return config==null || restore(config);
        if (config==null || session==null) return false;
        try { return single(config) ? configureClip(config) : restore(config); }
        catch(ReflectiveOperationException | RuntimeException unavailable){return false;}
    }
    public static boolean launchOrDefer(String methodName,Object[] arguments) {
        int configIndex="A09".equals(methodName)?2:1;
        if(arguments==null || arguments.length<=configIndex+1)return false;
        Object config=arguments[configIndex],session=arguments[configIndex+1];
        if(!allowLaunch(config,session))return false;
        noteLaunch(config);return true;
    }
    private static String selectedId(Object config) throws ReflectiveOperationException {
        Object explicit=read(config,"A1k");
        if(explicit instanceof String && !((String)explicit).isEmpty())return (String)explicit;
        Object source=read(config,"A0G");int index=(Integer)read(config,"A07");
        if(source instanceof List && index>=0 && index<((List<?>)source).size()) {
            Object id=((List<?>)source).get(index);
            if(id instanceof String && !((String)id).isEmpty())return (String)id;
        }
        return null;
    }

    private static boolean configureClip(Object config) {
        try {
            String mediaId = selectedId(config);
            if (mediaId == null) return false;
            original(config,true);
            set(config,"A1k",mediaId);
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
            restore(config); return false;
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
        message.setText("Abre un Reel desde el feed o un mensaje");
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
        if (!pagerLocked(controller) || controller == null) return;
        try {
            Object pager = read(controller, "A0A");
            if (pager != null) pager.getClass().getMethod("setUserInputEnabled", boolean.class).invoke(pager, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    public static boolean pagerLocked(Object controller) {
        if(!CalmaConfig.reels())return false;
        try { Object config=read(controller,"A0P");return config==null?locked():single(config); }
        catch(ReflectiveOperationException | RuntimeException unavailable){return locked();}
    }
    public static boolean maySelect(Object controller,int position) { return !pagerLocked(controller) || position == 0; }

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
