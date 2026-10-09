package es.calma.instagram.nativeapp;

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
    private NativeDiscover() {}

    /** Only X.094e's Explore response is hooked; shared media/profile/DM parsers are untouched. */
    public static void explore(Object response, Object parser) {
        filterGrid(response, parser, "A06", true);
    }

    /** The dedicated search media_grid parser is used by submitted-keyword SERPs. */
    public static void searchGrid(Object response, Object parser) {
        filterGrid(response, parser, "A05", false);
    }

    private static void filterGrid(Object response, Object parser, String field, boolean followingOnly) {
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
            if (followingOnly) resolveAuthors(session, media);
            List<Object> allowed = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Object tile : tiles) {
                try {
                    Object value = media(tile);
                    if (value == null || !permittedMedia(value, session, followingOnly)) continue;
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

    /** Used only by the main Search provider; blank queries never show history or suggestions. */
    public static boolean hasQuery(String query) {
        if (query == null) return false;
        for (int i = 0; i < query.length(); i++) {
            char value = query.charAt(i);
            if (!Character.isWhitespace(value) && !Character.isSpaceChar(value)) return true;
        }
        return false;
    }

    private static void resolveAuthors(Object session, List<?> media) {
        LinkedHashSet<String> pending = new LinkedHashSet<>();
        for (Object value : media) try {
            if (NativeAds.media(value)) continue;
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
    private static boolean permittedMedia(Object media, Object session, boolean followingOnly) throws ReflectiveOperationException {
        if (NativeAds.media(media)) return false;
        Object dictionary = StockAccess.get(media, "A04");
        if (CalmaConfig.reels() && "clips".equals(StockAccess.call(dictionary, "A7W"))) return false;
        if (!followingOnly) return true;
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
            // A09 is the dedicated AD tile payload, even when its advertiser
            // is followed or its Media has no injected subtree yet.
            return StockAccess.get(tile, "A08");
        } catch (ReflectiveOperationException | RuntimeException unknownTile) { return null; }
    }

    /** Strip mixed recommendation/clip siblings from otherwise permitted stock tiles. */
    private static Object cleanTile(Object original, Object media) throws ReflectiveOperationException {
        for (Constructor<?> constructor : type("X.032D").getConstructors()) {
            if (constructor.getParameterTypes().length == 8) {
                Object result = constructor.newInstance(StockAccess.get(original, "A00"), null,
                        media, null, null, false, false, false);
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
