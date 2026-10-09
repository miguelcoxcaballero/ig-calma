package es.calma.instagram.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Original Calma integration with Instagram 439's native Settings2 row model. */
public final class CalmaSettings {
    private static final String ID = "CALMA_FEED_SETTINGS";
    private static final String DESTINATION = "calma:settings:feed";
    private static final Map<Object, Object> contents = new WeakHashMap<>();
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static Object row;
    private static boolean loggedFailure;

    private CalmaSettings() {}

    public static void bind(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        if (context instanceof Activity) activity = new WeakReference<>((Activity) context);
    }

    /** Returns the stock Sections type; Instagram renders and scrolls the added row itself. */
    public static synchronized Object content(Object screenId, Object original) {
        if (!"MAIN_SETTINGS_SCREEN".equals(String.valueOf(screenId)) || original == null
                || !"X.0E72".equals(original.getClass().getName())) return original;
        Object cached = contents.get(original);
        if (cached != null) return cached;
        try {
            List<?> sections = (List<?>) get(original, "A00");
            if (sections.isEmpty()) return original;
            Object first = sections.get(0);
            if (!"X.0R5u".equals(first.getClass().getName())) return original;
            List<?> children = (List<?>) get(first, "A02");
            for (Object child : children) {
                if ("X.0E6t".equals(child.getClass().getName()) && ID.equals(String.valueOf(get(child, "A02")))) return original;
            }
            ArrayList<Object> nextChildren = new ArrayList<>();
            nextChildren.add(row());
            nextChildren.addAll(children);
            Object section = newSection();
            set(section, "A00", get(first, "A00"));
            set(section, "A01", get(first, "A01"));
            set(section, "A02", immutable(nextChildren));
            set(section, "A03", get(first, "A03"));
            ArrayList<Object> nextSections = new ArrayList<>(sections);
            nextSections.set(0, section);
            Object result = type("X.0E72").getConstructor(type("X.03kU"), boolean.class)
                    .newInstance(immutable(nextSections), get(original, "A01"));
            contents.put(original, result);
            return result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // A changed stock schema must leave Instagram's own settings usable.
            if (!loggedFailure) { loggedFailure = true; Log.e("CalmaSettings", "Native settings row unavailable", exception); }
            return original;
        }
    }

    /** Its DEX body is bound to the stock allocation sequence by SettingsPatch. */
    public static Object newSection() { return null; }

    /** Called before the stock coroutine resolves a navigation destination. */
    public static boolean open(Object destination) {
        if (destination == null || !"X.0R4P".equals(destination.getClass().getName())) return false;
        try {
            if (!DESTINATION.equals(get(destination, "A00"))) return false;
        } catch (ReflectiveOperationException exception) { return false; }
        Activity current = activity.get();
        Context context = current != null && !current.isFinishing() ? current : CalmaConfig.context();
        if (context != null) {
            Intent intent = new Intent(context, CalmaSettingsActivity.class);
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
        return true;
    }

    private static Object row() throws ReflectiveOperationException {
        if (row != null) return row;
        Class<?> marker = type("X.0vAE");
        Object identifier = Proxy.newProxyInstance(marker.getClassLoader(), new Class<?>[]{marker}, (proxy, method, args) -> {
            if ("toString".equals(method.getName())) return ID;
            if ("hashCode".equals(method.getName())) return ID.hashCode();
            if ("equals".equals(method.getName())) return proxy == args[0];
            throw new UnsupportedOperationException(method.getName());
        });
        Object literal = type("com.instagram.settings2.core.model.FbtModelSource$Literal")
                .getConstructor(String.class).newInstance("Tu feed");
        Object label = type("com.instagram.settings2.core.model.FbtModel")
                .getConstructor(type("com.instagram.settings2.core.model.FbtModelSource"), type("X.03kU"))
                .newInstance(literal, null);
        Object destination = type("X.0R4P").getConstructor(Object.class).newInstance(DESTINATION);
        for (Constructor<?> constructor : type("X.0E6t").getConstructors()) {
            if (constructor.getParameterTypes().length == 13) {
                row = constructor.newInstance(null, null, identifier, destination, label, null, null, null,
                        null, null, true, false, false);
                return row;
            }
        }
        throw new NoSuchMethodException("NavigationRowUiState constructor");
    }

    private static Object immutable(Iterable<?> items) throws ReflectiveOperationException {
        return type("X.00nM").getMethod("A00", Iterable.class).invoke(null, items);
    }
    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, CalmaSettings.class.getClassLoader());
    }
    private static Object get(Object object, String field) throws ReflectiveOperationException {
        return object.getClass().getField(field).get(object);
    }
    private static void set(Object object, String name, Object value) throws ReflectiveOperationException {
        Field field = object.getClass().getField(name);
        field.set(object, value);
    }
}
