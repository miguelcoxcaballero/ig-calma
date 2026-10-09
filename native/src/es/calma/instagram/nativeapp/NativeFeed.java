package es.calma.instagram.nativeapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Calma filtering at the stock feed response boundary, before RecyclerView sees items. */
public final class NativeFeed {
    private static final Map<String, Request> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<String, ChronologyState> CHRONOLOGY = new ConcurrentHashMap<>();
    private static final class Request {
        final boolean head, following;
        Request(boolean head, boolean following) { this.head = head; this.following = following; }
    }
    public static void request(Object session, Object request) {
        try {
            String owner = NativeRelations.owner(session);
            String id = (String) StockAccess.get(request, "A0H");
            if (owner.length() > 0 && id != null) {
                if (REQUESTS.size() > 128) REQUESTS.clear();
                Map<?, ?> params = (Map<?, ?>) StockAccess.get(request, "A0L");
                Map<?, ?> selected = parameters(params);
                REQUESTS.put(owner + ':' + id, new Request(StockAccess.get(request, "A0G") == null,
                        selected != null && "following".equals(selected.get("pagination_source"))));
            }
        } catch (ReflectiveOperationException | RuntimeException missingRequestContext) { /* Cached responses still filter. */ }
    }
    private static final String[] SUGGESTION_FIELDS = {
        "A0N", "A0O", "A0P", "A0Q", "A0R", "A0S", "A0T", "A0U", "A0K", "A0W", "A04", "A0J",
        "A0L", "A0V", "A0e", "A0d", "A0H", "A0M", "A0j", "A0b", "A0h", "A0Z", "A0a", "A0c", "A0g", "A0i"
    };
    private NativeFeed() {}

    /** Called only for the stock main-feed request's parameter map. */
    public static Map<?, ?> parameters(Map<?, ?> original) {
        if (CalmaConfig.mode() == 0) return original;
        Object source = original == null ? null : original.get("pagination_source");
        // Respect other explicitly selected native feeds (for example Favorites).
        if (source != null && !"feed_recs".equals(source) && !"following".equals(source)) return original;
        Map<Object, Object> result = original == null ? new HashMap<>() : new HashMap<>(original);
        result.put("pagination_source", "following");
        return result;
    }

    /** The parser carries the actual requesting account, including cached response parsing. */
    public static void response(Object response, Object parser) {
        if (response == null || parser == null) return;
        try {
            Object session = StockAccess.get(parser, "A01");
            String owner = NativeRelations.owner(session);
            if (owner.length() == 0) return;
            long now = System.currentTimeMillis() / 1000L;
            // Ads stay disabled in every feed mode, including native/default.
            // These are the stock response's client-insertion controls.
            StockAccess.set(response, "A0E", Boolean.TRUE);
            StockAccess.set(response, "A0J", Integer.valueOf(0));
            if (CalmaConfig.mode() != 0) {
                // Native response keys disable_client_insertions and suggested_users:
                // prevent a second client-side suggestion source after response filtering.
                StockAccess.set(response, "A08", null);
            }
            String responseId = (String) StockAccess.get(response, "A0P");
            Request request = responseId == null ? null : REQUESTS.remove(owner + ':' + responseId);
            Object source = StockAccess.get(response, "A0O");
            boolean following = "following".equals(source) || (source == null && request != null && request.following);
            Object wrappers = StockAccess.get(response, "A0S");
            Object media = StockAccess.get(response, "A0U");
            boolean exhausted = true, sawPosts = false;
            Page wrapperPage = null, mediaPage = null;
            if (wrappers instanceof List) {
                resolveMissing((List<?>) wrappers, true, session, following);
                wrapperPage = filter((List<?>) wrappers, true, now, following, session);
                sawPosts |= wrapperPage.sawPosts;
                if (wrapperPage.sawPosts) exhausted &= wrapperPage.entirePageOlder;
            }
            if (media instanceof List) {
                resolveMissing((List<?>) media, false, session, following);
                mediaPage = filter((List<?>) media, false, now, following, session);
                sawPosts |= mediaPage.sawPosts;
                if (mediaPage.sawPosts) exhausted &= mediaPage.entirePageOlder;
            }
            // Commit both representations together so an optional second representation
            // cannot make a partially filtered response escape on an exception.
            if (wrapperPage != null) StockAccess.set(response, "A0S", wrapperPage.items);
            if (mediaPage != null) StockAccess.set(response, "A0U", mediaPage.items);
            boolean orderedStream = true;
            if (sawPosts) {
                long newest = Math.max(wrapperPage == null ? Long.MIN_VALUE : wrapperPage.newest, mediaPage == null ? Long.MIN_VALUE : mediaPage.newest);
                long oldest = Math.min(wrapperPage == null ? Long.MAX_VALUE : wrapperPage.oldest, mediaPage == null ? Long.MAX_VALUE : mediaPage.oldest);
                boolean ordered = (wrapperPage == null || wrapperPage.ordered) && (mediaPage == null || mediaPage.ordered);
                orderedStream = CHRONOLOGY.computeIfAbsent(owner, unused -> new ChronologyState()).observe(CalmaConfig.sessionId(), request != null && request.head, newest, oldest, ordered);
            }
            if (following && sawPosts && exhausted && orderedStream) {
                StockAccess.set(response, "A0a", false);
                StockAccess.set(response, "A0W", false);
                StockAccess.set(response, "A0N", null);
            }
        } catch (ReflectiveOperationException | RuntimeException unexpectedStockShape) {
            // A recognized feed may never expose accounts merely because an optional
            // native value failed to decode. Keep its pagination available for recovery.
            try {
                StockAccess.set(response, "A0S", new ArrayList<>());
                StockAccess.set(response, "A0U", new ArrayList<>());
            } catch (ReflectiveOperationException unsupportedResponse) { /* Not a feed. */ }
        }
    }

    private static final class DatedItem {
        final Object item;
        final long time;
        DatedItem(Object item, long time) { this.item = item; this.time = time; }
    }
    private static final class Page {
        final List<Object> items;
        final boolean entirePageOlder, sawPosts, ordered;
        final long newest, oldest;
        Page(List<Object> items, boolean entirePageOlder, boolean sawPosts, boolean ordered, long newest, long oldest) {
            this.items = items; this.entirePageOlder = entirePageOlder; this.sawPosts = sawPosts;
            this.ordered = ordered; this.newest = newest; this.oldest = oldest;
        }
    }
    private static Page filter(List<?> original, boolean wrapped, long now, boolean following, Object session)
            throws ReflectiveOperationException {
        List<DatedItem> posts = new ArrayList<>(original.size());
        List<Object> controls = new ArrayList<>();
        List<Integer> controlSlots = new ArrayList<>();
        Set<String> pageIds = new HashSet<>();
        boolean sawPost = false, allOlder = true, descending = true;
        long previous = Long.MAX_VALUE, newest = Long.MIN_VALUE, oldest = Long.MAX_VALUE;
        for (Object item : original) {
            if (item == null) continue;
            if (wrapped && NativeAds.feedWrapper(item)) continue;
            Object media = wrapped ? StockAccess.call(item, "A0A") : item;
            if (media == null) {
                if (!suggestion(item)) { controls.add(item); controlSlots.add(posts.size()); }
                continue;
            }
            // An old sponsored creative says nothing about organic chronology.
            // Exclude it before observing timestamps or the end of the feed.
            if (NativeAds.media(media)) continue;
            sawPost = true;
            Object dictionary = StockAccess.get(media, "A04");
            Long time = (Long) StockAccess.call(dictionary, "A6X");
            if (time == null || time <= 0) { allOlder = false; descending = false; continue; }
            if (time > previous) descending = false;
            previous = time;
            newest = Math.max(newest, time); oldest = Math.min(oldest, time);
            if (time >= now - RecentFeedPolicy.WINDOW_SECONDS) allOlder = false;
            if (!RecentFeedPolicy.withinWindow(time, now)) continue;
            String kind = (String) StockAccess.call(dictionary, "A7W");
            if (CalmaConfig.reels() && "clips".equals(kind)) continue;
            Object author = StockAccess.call(dictionary, "A33");
            boolean permitted = NativeRelations.permitted(session, author, CalmaConfig.mode(), following);
            if (!permitted) {
                Object collaborators = StockAccess.call(dictionary, "A8F");
                if (collaborators instanceof List) for (Object collaborator : (List<?>) collaborators) {
                    if (NativeRelations.permitted(session, collaborator, CalmaConfig.mode(), following)) { permitted = true; break; }
                }
            }
            if (!permitted) continue;
            String id = (String) StockAccess.call(dictionary, "getId");
            if (id != null && id.length() > 0 && pageIds.add(id)) posts.add(new DatedItem(item, time));
        }
        posts.sort((a, b) -> Long.compare(b.time, a.time));
        List<Object> items = new ArrayList<>(posts.size() + controls.size());
        int control = 0;
        for (int position = 0; position <= posts.size(); position++) {
            while (control < controls.size() && controlSlots.get(control) <= position) items.add(controls.get(control++));
            if (position < posts.size()) items.add(posts.get(position).item);
        }
        // A ranked or out-of-order page never proves the chronological boundary.
        return new Page(items, sawPost && allOlder && descending, sawPost, descending, newest, oldest);
    }

    private static void resolveMissing(List<?> items, boolean wrapped, Object session, boolean following) {
        if (CalmaConfig.mode() != 2) return;
        List<String> ids = new ArrayList<>();
        for (Object item : items) {
            try {
                if (item == null || (wrapped && NativeAds.feedWrapper(item))) continue;
                Object media = wrapped ? StockAccess.call(item, "A0A") : item;
                if (media == null || NativeAds.media(media)) continue;
                Object dictionary = StockAccess.get(media, "A04");
                Object author = StockAccess.call(dictionary, "A33");
                if (author != null && !NativeRelations.known(session, author, CalmaConfig.mode())) {
                    ids.add((String) StockAccess.call(author, "getId"));
                }
                Object collaborators = StockAccess.call(dictionary, "A8F");
                if (collaborators instanceof List) for (Object collaborator : (List<?>) collaborators) {
                    if (!NativeRelations.known(session, collaborator, CalmaConfig.mode())) ids.add((String) StockAccess.call(collaborator, "getId"));
                }
            } catch (ReflectiveOperationException | RuntimeException missingOptionalAuthor) { /* Process other posts. */ }
        }
        NativeRelationLookup.awaitOffMainThread(session, ids);
    }

    private static boolean suggestion(Object wrapper) throws ReflectiveOperationException {
        if (CalmaConfig.reels() && StockAccess.get(wrapper, "A03") != null) return true;
        if (CalmaConfig.mode() != 0) {
            for (String field : SUGGESTION_FIELDS) if (StockAccess.get(wrapper, field) != null) return true;
        }
        return false;
    }
}
