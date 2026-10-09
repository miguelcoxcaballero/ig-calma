package es.calma.instagram.nativeapp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Cached access to the exact stock build checked by the patcher. */
final class StockAccess {
    private static final ConcurrentHashMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private StockAccess() {}
    static Object get(Object target, String name) throws ReflectiveOperationException {
        return field(target.getClass(), name).get(target);
    }
    static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        field(target.getClass(), name).set(target, value);
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        String key = type.getName() + '#' + name;
        Field field = FIELDS.get(key);
        if (field == null) {
            field = type.getField(name);
            field.setAccessible(true);
            FIELDS.put(key, field);
        }
        return field;
    }
    static Object call(Object target, String name) throws ReflectiveOperationException {
        return method(target.getClass(), name).invoke(target);
    }
    static Object callString(Object target, String name, String argument) throws ReflectiveOperationException {
        return method(target.getClass(), name, String.class).invoke(target, argument);
    }
    static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        StringBuilder key = new StringBuilder(type.getName()).append('#').append(name);
        for (Class<?> argument : arguments) key.append(':').append(argument.getName());
        Method method = METHODS.get(key.toString());
        if (method == null) {
            method = type.getMethod(name, arguments);
            method.setAccessible(true);
            METHODS.put(key.toString(), method);
        }
        return method;
    }
}
