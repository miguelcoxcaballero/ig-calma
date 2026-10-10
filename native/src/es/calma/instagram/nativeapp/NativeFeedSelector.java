package es.calma.instagram.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Three stock menu models, rendered by Instagram's original IGDS popup and row binder. */
public final class NativeFeedSelector {
    private static final String[] TYPES = {"BLENDED_FOR_YOU", "FAVORITES", "FOLLOWING"};
    private static final String[] LABELS = {"For you", "Favourites", "Following"};
    private static WeakReference<Object> listener = new WeakReference<>(null);
    private static WeakReference<Object> state = new WeakReference<>(null);
    private static WeakReference<PopupWindow> open = new WeakReference<>(null);
    private NativeFeedSelector() {}
    private static Class<?> type(Object context, String name) throws ClassNotFoundException {
        return Class.forName("X." + name, false, context.getClass().getClassLoader());
    }
    private static Object lazy(Object object, String field) throws ReflectiveOperationException {
        return StockAccess.call(StockAccess.get(object, field), "getValue");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Object context, String name) throws ReflectiveOperationException {
        return Enum.valueOf((Class) type(context, "06yP"), name);
    }
    public static List<?> options(Object session, List<?> original) {
        try {
            Constructor<?> ctor = type(session, "06yQ").getConstructor(type(session, "06yP"));
            List<Object> rows = new ArrayList<>();
            for (String name : TYPES) rows.add(ctor.newInstance(enumValue(session, name)));
            return rows;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("CalmaFeed", "Native selector models unavailable", error);
            return original;
        }
    }
    public static Object initial(Object session, List<?> options) {
        NativeFeedBudget.init();
        String owner = NativeRelations.owner(session), selected = saved(owner);
        CalmaConfig.select(selected);
        try {
            for (Object option : options) if (((Enum<?>) StockAccess.get(option, "A01")).name().equals(selected)) return option;
        } catch (ReflectiveOperationException ignored) {}
        return options.get(0);
    }
    private static String saved(String owner) {
        android.content.SharedPreferences p = CalmaConfig.preferences();
        String value = p == null ? "FOLLOWING" : p.getString("feed:" + owner, "FOLLOWING");
        String normalized = CalmaConfig.normalizeFeed(value);
        if (!supported(normalized) || "BLENDED_FOR_YOU".equals(normalized)) normalized = "FOLLOWING";
        if (p != null && !normalized.equals(value)) p.edit().putString("feed:" + owner, normalized).apply();
        return normalized;
    }
    private static boolean supported(String name) { for (String t : TYPES) if (t.equals(name)) return true; return false; }
    public static String savedType(Object preference) {
        // The constructor below supplies the account-scoped selection. Cold startup defaults to Following.
        return CalmaConfig.feed();
    }
    public static boolean openPicker(Object click, View anchor) {
        try {
            Object controller = StockAccess.get(click, "A02");
            if (controller == null || !controller.getClass().getName().equals("X.06mX")) return false;
            Object current = lazy(click, "A00"); state = new WeakReference<>(current);
            StockAccess.set(current, "A01", true);
            PopupWindow popup = (PopupWindow) lazy(current, "A08");
            populate(current, popup);
            popup.setOnDismissListener((PopupWindow.OnDismissListener) current);
            popup.showAsDropDown(anchor, 0, 0);
            open = new WeakReference<>(popup);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("CalmaFeed", "Native selector popup unavailable", error); return false;
        }
    }
    public static List<?> rows(Object current) throws ReflectiveOperationException {
        Context context = (Context) StockAccess.get(current, "A03");
        Object selected = StockAccess.get(StockAccess.get(current, "A00"), "A01");
        return menuRows(context, current, (List<?>) StockAccess.get(current, "A07"), selected, current);
    }
    public static List<?> menuRows(Context context, Object anchor, List<?> options, Object selected, Object current)
            throws ReflectiveOperationException {
        Class<?> callback = type(anchor, "0LUb"), row = type(anchor, "0VTM");
        Constructor<?> cb = callback.getConstructor(int.class, Object.class, Object.class);
        Constructor<?> ctor = row.getConstructor(Drawable.class, Drawable.class, type(anchor, "0nQi"),
                Integer.class, String.class, String.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
        List<Object> rows = new ArrayList<>(); long balance = NativeFeedBudget.balance();
        int i = 0;
        for (Object option : options) {
            Object choice = StockAccess.get(option, "A01"); String name = ((Enum<?>) choice).name();
            if (!supported(name)) continue;
            int index = 0; for (; index < TYPES.length; index++) if (TYPES[index].equals(name)) break;
            int icon = (Integer) StockAccess.get(choice, "A00");
            Drawable check = choice == selected ? context.getDrawable((Integer) StockAccess.get(choice, "A02")) : null;
            String subtitle = index == 0 ? NativeFeedBudget.label(balance) : null;
            Object model = ctor.newInstance(check, icon == 0 ? null : context.getDrawable(icon), cb.newInstance(4, choice, current),
                    null, LABELS[index], subtitle, choice == selected, true, false, index == 0 && balance <= 0, false);
            StockAccess.set(option, "A00", model); rows.add(model); i++;
        }
        if (i != TYPES.length) throw new IllegalStateException("Native selector requires three rows");
        return rows;
    }
    private static void populate(Object current, PopupWindow popup) throws ReflectiveOperationException {
        List<?> rows = rows(current);
        List<Object> nativeRows = (List<Object>) StockAccess.get(current, "A06"); nativeRows.clear(); nativeRows.addAll(rows);
        StockAccess.method(popup.getClass(), "A09", List.class).invoke(popup, rows);
        StockAccess.set(current, "A02", false);
    }
    static void refreshBalance() {
        PopupWindow popup = open.get(); Object current = state.get();
        if (popup != null && popup.isShowing() && current != null) {
            try { populate(current, popup); } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
    }
    public static boolean select(Object target, Object choice) {
        String name = choice instanceof Enum ? ((Enum<?>) choice).name() : "";
        if (!supported(name)) return false;
        if (name.equals("BLENDED_FOR_YOU") && NativeFeedBudget.balance() <= 0) return true;
        try {
            // Resolve everything before changing state; preserve the original native refresh sequence.
            Object current = lazy(target, "A04"), feed = lazy(target, "A03"), refresh = lazy(target, "A07"), header = lazy(target, "A05");
            Object selected = null;
            for (Object option : (List<?>) StockAccess.get(current, "A07")) if (StockAccess.get(option, "A01") == choice) selected = option;
            if (selected == null) throw new IllegalStateException("Missing native feed choice");
            Object reason = type(target, "02pk").getField("A0J").get(null);
            java.lang.reflect.Method clear = StockAccess.method(feed.getClass(), "A13");
            java.lang.reflect.Method load = StockAccess.method(refresh.getClass(), "A01", reason.getClass(), Map.class);
            java.lang.reflect.Method title = StockAccess.method(header.getClass(), "A06", boolean.class);
            NativeFeedBudget.settle(); CalmaConfig.select(name);
            Object user = StockAccess.get(target, "A01");
            android.content.SharedPreferences p = CalmaConfig.preferences();
            if (p != null) p.edit().putString("feed:" + NativeRelations.owner(user), name).apply();
            StockAccess.set(current, "A00", selected); StockAccess.set(current, "A01", true); StockAccess.set(current, "A02", true);
            listener = new WeakReference<>(target); state = new WeakReference<>(current);
            PopupWindow popup = open.get(); if (popup != null) popup.dismiss();
            clear.invoke(feed);
            Map<String, String> params = new HashMap<>(); params.put("feed_type", name);
            if (name.equals("BLENDED_FOR_YOU")) params.put("pagination_source", "feed_recs");
            if (name.equals("FAVORITES")) params.put("pagination_source", "favorites");
            if (name.equals("FOLLOWING")) params.put("pagination_source", "following");
            load.invoke(refresh, reason, params); title.invoke(header, true);
            NativeFeedBudget.changed();
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("CalmaFeed", "Native feed switch failed", error);
            // Do not fall through into the stock contextual route with mismatched filters.
            return true;
        }
    }
    static void expired() {
        Object target = listener.get();
        if (target != null) try { select(target, enumValue(target, "FOLLOWING")); } catch (ReflectiveOperationException ignored) {}
        else CalmaConfig.select("FOLLOWING");
    }
    public static boolean header(Object helper) {
        try {
            Object bar = StockAccess.get(helper, "A06");
            if (bar == null) {
                Object modern = StockAccess.get(helper, "A07"); if (modern == null) return false;
                topBar(modern); return true;
            }
            Object current = lazy(helper, "A0H"); String name = ((Enum<?>) StockAccess.get(StockAccess.get(current, "A00"), "A01")).name();
            int i = 0; for (; i < TYPES.length; i++) if (TYPES[i].equals(name)) break;
            if (i == TYPES.length) return false;
            StockAccess.method(bar.getClass(), "A06", boolean.class, String.class).invoke(bar, true, LABELS[i]);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }
    /** Reuse the stock title/chevron on the newer two-tab header as the same three-item picker. */
    public static void topBar(Object bar) {
        try {
            TextView title = (TextView) StockAccess.get(bar, "A06");
            View secondary = (View) StockAccess.get(bar, "A05");
            if (title == null || secondary == null) return;
            int index = 0; for (; index < TYPES.length; index++) if (TYPES[index].equals(CalmaConfig.feed())) break;
            if (index == TYPES.length) return;
            title.setText(LABELS[index]); title.setSelected(true); secondary.setVisibility(View.GONE);
            View.OnClickListener picker = view -> {
                try {
                    Object action = StockAccess.get(bar, "A03");
                    if (action != null) StockAccess.method(action.getClass(), "DvD", View.class).invoke(action, view);
                } catch (ReflectiveOperationException | RuntimeException error) { android.util.Log.e("CalmaFeed", "Native title picker failed", error); }
            };
            title.setOnClickListener(picker);
            ((View)bar).requireViewById(2131427554).setOnClickListener(picker);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
