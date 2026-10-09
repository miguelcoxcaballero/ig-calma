package es.calma.instagram.nativeapp;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Original port of Calma's Discover rule at native response and search-render boundaries. */
public final class NativeDiscover {
    private static final String[] SINGLE = {"A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08"};
    private static final String[] LISTS = {"A0C", "A0D", "A0F"};
    private static final ThreadLocal<WeakReference<Object>> SEARCH = new ThreadLocal<>();
    private NativeDiscover() {}

    /** Only X.094e's Explore response is hooked; shared media/profile/DM parsers are untouched. */
    public static void explore(Object response, Object parser) {
        filterGrid(response, parser, "A06");
    }

    /** The dedicated search media_grid parser is used by submitted-keyword SERPs. */
    public static void searchGrid(Object response, Object parser) {
        filterGrid(response, parser, "A05");
    }

    private static void filterGrid(Object response, Object parser, String field) {
        if (response == null) return;
        try {
            Object session = StockAccess.get(parser, "A01");
            List<?> sections = list(StockAccess.get(response, field));
            List<Object> tiles = new ArrayList<>();
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object section : sections) collect(section, tiles, visited, 0);
            List<Object> media = new ArrayList<>();
            for (Object tile : tiles) {
                Object value = media(tile);
                if (value != null) media.add(value);
            }
            resolveAuthors(session, media);
            List<Object> allowed = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Object tile : tiles) {
                try {
                    Object value = media(tile);
                    if (value == null || !permittedMedia(value, session)) continue;
                    Object dictionary = StockAccess.get(value, "A04");
                    String id = (String) StockAccess.call(dictionary, "getId");
                    if (id == null || id.length() == 0 || !seen.add(id)) continue;
                    allowed.add(cleanTile(tile, value));
                } catch (ReflectiveOperationException | RuntimeException invalidTile) { /* Unverified tiles stay absent. */ }
            }
            List<Object> filtered = new ArrayList<>();
            if (!allowed.isEmpty()) filtered.add(grid(allowed));
            StockAccess.set(response, field, filtered);
            // The native cursor, session paging token and more_available are preserved,
            // including when one server page contains no followed accounts.
        } catch (ReflectiveOperationException | RuntimeException unexpectedShape) {
            clear(response, field);
        }
    }

    /** Keep top/accounts SERP's raw and rendered account collections in agreement. */
    public static void serpEntities(Object response, Object parser) {
        if (response == null) return;
        try {
            Object session = StockAccess.get(parser, "A01");
            String name = response.getClass().getName();
            String rawField = "X.0YCE".equals(name) || "X.0YCJ".equals(name) ? "A01" : "A00";
            StockAccess.set(response, rawField, users(list(StockAccess.get(response, rawField)), session));
            StockAccess.set(response, "A0A", users(list(StockAccess.get(response, "A0A")), session));
            // These modules carry preview images without a verifiable media author.
            // Resolve the declaring base explicitly: subclass A01 is the raw list.
            type("X.0WDs").getField("A01").set(response, null);
            type("X.0WDs").getField("A02").set(response, null);
            clear(response, "A0C");
            if ("X.0YCJ".equals(name)) clear(response, "A00");
        } catch (ReflectiveOperationException | RuntimeException unexpectedShape) {
            clear(response, "A0A");
        }
    }

    /** Resolves REST typeahead account status off the UI thread before it enters the search cache. */
    public static void searchResponse(Object response, Object parser) {
        if (response == null) return;
        try {
            Object session = StockAccess.get(parser, "A01");
            StockAccess.set(response, "A02", users(list(StockAccess.get(response, "A02")), session));
        } catch (ReflectiveOperationException | RuntimeException unexpectedShape) { clear(response, "A02"); }
    }

    /** Paired immediately with searchState at the native main-search provider's return. */
    public static void searchOwner(Object provider) {
        SEARCH.remove();
        try { SEARCH.set(new WeakReference<>(StockAccess.get(provider, "A00"))); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { /* Fail closed below. */ }
    }

    /** Filters cached, recent and GraphQL results and their parallel native metadata together. */
    public static void searchState(Object state) {
        try {
            WeakReference<Object> reference = SEARCH.get();
            Object session = reference == null ? null : reference.get();
            List<?> items = list(StockAccess.get(state, "A00"));
            List<?> metadata = list(StockAccess.get(state, "A01"));
            List<Object> kept = new ArrayList<>(), keptMetadata = new ArrayList<>();
            if (items.size() == metadata.size()) {
                resolveUsers(session, items);
                for (int i = 0; i < items.size(); i++) {
                    Object user = rowUser(items.get(i));
                    if (permittedUser(user, session)) {
                        kept.add(items.get(i)); keptMetadata.add(metadata.get(i));
                    }
                }
            }
            StockAccess.set(state, "A00", kept);
            StockAccess.set(state, "A01", keptMetadata);
        } catch (ReflectiveOperationException | RuntimeException unexpectedShape) {
            clear(state, "A00"); clear(state, "A01");
        } finally { SEARCH.remove(); }
    }

    private static List<Object> users(List<?> items, Object session) {
        resolveUsers(session, items);
        List<Object> result = new ArrayList<>();
        for (Object item : items) if (permittedUser(rowUser(item), session)) result.add(item);
        return result;
    }

    private static Object rowUser(Object row) {
        if (row == null) return null;
        try {
            String name = row.getClass().getName();
            if ("X.0I7G".equals(name)) row = StockAccess.get(row, "A00");
            if ("X.0C9d".equals(row.getClass().getName())) return StockAccess.get(row, "A01");
            if ("X.0YCr".equals(row.getClass().getName())) return StockAccess.get(row, "A07");
            if ("com.instagram.user.model.User".equals(row.getClass().getName())) return row;
        } catch (ReflectiveOperationException | RuntimeException unknownRow) { /* Unverified result. */ }
        return null;
    }

    private static void resolveUsers(Object session, List<?> items) {
        LinkedHashSet<String> pending = new LinkedHashSet<>();
        for (Object item : items) unresolved(rowUser(item), session, pending);
        NativeRelationLookup.awaitOffMainThread(session, new ArrayList<>(pending));
    }
    private static void resolveAuthors(Object session, List<?> media) {
        LinkedHashSet<String> pending = new LinkedHashSet<>();
        for (Object value : media) try {
            Object dictionary = StockAccess.get(value, "A04");
            unresolved(StockAccess.call(dictionary, "A33"), session, pending);
            for (Object author : list(StockAccess.call(dictionary, "A8F"))) unresolved(author, session, pending);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        NativeRelationLookup.awaitOffMainThread(session, new ArrayList<>(pending));
    }
    private static void unresolved(Object user, Object session, Set<String> pending) {
        if (user == null || NativeRelations.known(session, user, 1) || pending.size() >= 100) return;
        try {
            String id = (String) StockAccess.call(user, "getId");
            if (id != null && !NativeRelations.known(session, NativeRelations.user(session, id), 1)) pending.add(id);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    private static boolean permittedUser(Object user, Object session) {
        if (user == null || session == null) return false;
        if (NativeRelations.known(session, user, 1)) return NativeRelations.permitted(session, user, 1, false);
        try { return NativeRelations.isFollowing(session, (String) StockAccess.call(user, "getId")); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
    }
    private static boolean permittedMedia(Object media, Object session) throws ReflectiveOperationException {
        Object dictionary = StockAccess.get(media, "A04");
        if (CalmaConfig.reels() && "clips".equals(StockAccess.call(dictionary, "A7W"))) return false;
        if (permittedUser(StockAccess.call(dictionary, "A33"), session)) return true;
        for (Object collaborator : list(StockAccess.call(dictionary, "A8F")))
            if (permittedUser(collaborator, session)) return true;
        return false;
    }

    private static void collect(Object section, List<Object> tiles, Set<Object> visited, int depth)
            throws ReflectiveOperationException {
        if (section == null || depth > 6 || visited.size() >= 500 || !visited.add(section)) return;
        Object layout = StockAccess.get(section, "A01");
        if (layout == null) return;
        for (String field : SINGLE) { Object item = StockAccess.get(layout, field); if (item != null) tiles.add(item); }
        for (String field : LISTS) tiles.addAll(list(StockAccess.get(layout, field)));
        for (Object nested : list(StockAccess.get(layout, "A0E"))) collect(nested, tiles, visited, depth + 1);
        collect(StockAccess.get(layout, "A09"), tiles, visited, depth + 1);
    }
    private static Object media(Object tile) {
        if (tile == null) return null;
        try {
            Object media = StockAccess.get(tile, "A08");
            return media != null ? media : StockAccess.get(tile, "A09");
        } catch (ReflectiveOperationException | RuntimeException unknownTile) { return null; }
    }

    /** Strip mixed recommendation/clip siblings from otherwise permitted stock tiles. */
    private static Object cleanTile(Object original, Object media) throws ReflectiveOperationException {
        for (Constructor<?> constructor : type("X.032D").getConstructors()) {
            if (constructor.getParameterTypes().length == 8) {
                boolean primary = StockAccess.get(original, "A08") != null;
                Object result = constructor.newInstance(StockAccess.get(original, "A00"), null,
                        primary ? media : null, primary ? null : media, null, false, false, false);
                StockAccess.call(result, "A01");
                return result;
            }
        }
        throw new NoSuchMethodException("DiscoveryItem constructor");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object grid(List<Object> tiles) throws ReflectiveOperationException {
        Object layout = type("X.032B").getConstructor(List.class).newInstance(tiles);
        Object info = type("X.031Y").getConstructor(Boolean.class, Double.class, Integer.class, Integer.class)
                .newInstance(false, 1.0, 3, 3);
        Object kind = Enum.valueOf((Class) type("X.02P5"), "MEDIA_GRID");
        return type("X.024Z").getConstructor(type("X.031Y"), type("X.032B"), type("X.02P5"))
                .newInstance(info, layout, kind);
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, true, NativeDiscover.class.getClassLoader()); }
    private static List<?> list(Object value) { return value instanceof List ? (List<?>) value : Collections.emptyList(); }
    private static void clear(Object target, String field) {
        if (target == null) return;
        try { StockAccess.set(target, field, new ArrayList<>()); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
}
