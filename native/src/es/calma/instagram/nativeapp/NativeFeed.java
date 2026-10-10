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
    private static final Map<Object, Request> BUILDERS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Map<Object, Request> NATIVE_REQUESTS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static final Map<Object, RawPage> RAW = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Map<String, ChronologyState> CHRONOLOGY = new ConcurrentHashMap<>();
    private static final class Request {
        final boolean head, following;
        final int mode = CalmaConfig.mode();
        final long epoch = CalmaConfig.sessionId();
        final String feed = CalmaConfig.feed();
        String owner;
        NativeTimeline.Context context;
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
                Request tracked = new Request(StockAccess.get(request, "A0G") == null,
                        selected != null && "following".equals(selected.get("pagination_source")));
                tracked.owner = owner;
                REQUESTS.put(owner + ':' + id, tracked);
                synchronized (NATIVE_REQUESTS) {
                    if (NATIVE_REQUESTS.size() >= 512) {
                        NATIVE_REQUESTS.entrySet().removeIf(e -> e.getValue().epoch != CalmaConfig.sessionId());
                        if (NATIVE_REQUESTS.size() >= 512) NATIVE_REQUESTS.remove(NATIVE_REQUESTS.keySet().iterator().next());
                    }
                    NATIVE_REQUESTS.put(request, tracked);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException missingRequestContext) { /* Cached responses still filter. */ }
    }
    public static void context(Object androidContext, Object builder, Object unused, Object session, Object request, Object parameters) {
        if (NativeTimeline.internal()) return;
        request(session, request);
        try {
            Request tracked = REQUESTS.get(NativeRelations.owner(session) + ':' + StockAccess.get(request, "A0H"));
            if (tracked != null) {
                tracked.context = new NativeTimeline.Context(androidContext, session, request, parameters);
                if (builder != null) BUILDERS.put(builder, tracked);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
    /** Last write, after stock experiment/supplier parameters and before HTTP serialization. */
    public static void wire(Object builder) {
        Request request = BUILDERS.get(builder);
        if (request == null) return; // Never change Discover, DMs, profiles or other requests.
        try {
            String source = request.following ? "following" : "FAVORITES".equals(request.feed) ? "favorites" : "feed_recs";
            StockAccess.method(builder.getClass(), "AOA", String.class, String.class).invoke(builder, "pagination_source", source);
            StockAccess.method(builder.getClass(), "AOA", String.class, String.class).invoke(builder, "feed_type", request.following ? "FOLLOWING" : request.feed);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
    private static final class RawPage {
        final Object wrappers, media, cursor, source, more, auto;
        RawPage(Object response) throws ReflectiveOperationException {
            Object w = StockAccess.get(response, "A0S"), m = StockAccess.get(response, "A0U");
            wrappers = w instanceof List ? new ArrayList<>((List<?>)w) : w;
            media = m instanceof List ? new ArrayList<>((List<?>)m) : m;
            cursor = StockAccess.get(response,"A0N"); source = StockAccess.get(response,"A0O");
            more = StockAccess.get(response,"A0a"); auto = StockAccess.get(response,"A0W");
        }
        void restore(Object response) throws ReflectiveOperationException {
            StockAccess.set(response,"A0S",wrappers); StockAccess.set(response,"A0U",media);
            StockAccess.set(response,"A0N",cursor); StockAccess.set(response,"A0O",source);
            StockAccess.set(response,"A0a",more); StockAccess.set(response,"A0W",auto);
        }
    }
    /** Use the actual stock delivery request: response request_id is not a correlation contract. */
    public static void delivered(Object controller, Object envelope, Object result) {
        boolean followingSource = false;
        try {
            Object session = StockAccess.get(controller,"A0X"), nativeRequest = StockAccess.get(envelope,"A00");
            Object response = StockAccess.get(result,"A03");
            Request request = NATIVE_REQUESTS.get(nativeRequest);
            if (request == null) {
                // Stock cache/network envelopes can contain a copy of 02pp. Match the
                // client's own id AND cursor, never the optional server response id.
                Object id = StockAccess.get(nativeRequest,"A0H"), cursor = StockAccess.get(nativeRequest,"A0G");
                synchronized (NATIVE_REQUESTS) {
                    for (Map.Entry<Object,Request> entry : NATIVE_REQUESTS.entrySet()) {
                        if (NativeRelations.owner(session).equals(entry.getValue().owner)
                                && java.util.Objects.equals(id,StockAccess.get(entry.getKey(),"A0H"))
                                && java.util.Objects.equals(cursor,StockAccess.get(entry.getKey(),"A0G"))) {
                            request = entry.getValue(); break;
                        }
                    }
                }
            }
            RawPage raw = RAW.remove(response);
            if (raw != null) raw.restore(response);
            followingSource = (request != null && request.following) || "following".equals(StockAccess.get(response,"A0O"));
            process(response, session, request, request != null);
            Object rows = StockAccess.get(response,"A0S");
            if (!(rows instanceof List)) {
                Object media = StockAccess.get(response,"A0U");
                rows = media instanceof List ? StockAccess.method(result.getClass(),"A00",List.class).invoke(null,media) : new ArrayList<>();
            }
            StockAccess.method(result.getClass(),"A02",List.class).invoke(result,rows);
            if (request != null && request.epoch == CalmaConfig.sessionId()) NativeTimelinePager.delivery(controller,envelope,request.head);
        } catch (ReflectiveOperationException | RuntimeException ignored) { /* Final Friends guard below also covers unmatched/cache envelopes. */ }
        finally { guardFriends(controller, result, followingSource); }
    }
    private static void guardFriends(Object controller, Object result, boolean following) {
        if (CalmaConfig.mode() != 2 || result == null) return;
        List<?> rows = java.util.Collections.emptyList();
        try {
            Object session = StockAccess.get(controller, "A0X"), value = StockAccess.get(result, "A02");
            if (value instanceof List) rows = filter((List<?>) value, true, System.currentTimeMillis()/1000, following, session).items;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* Unverified rows stay hidden. */ }
        try { StockAccess.method(result.getClass(), "A02", List.class).invoke(result, rows); }
        catch (ReflectiveOperationException | RuntimeException unavailable) {
            try { StockAccess.set(result, "A02", rows); } catch (ReflectiveOperationException ignored) { }
        }
    }
    private static final String[] SUGGESTION_FIELDS = {
        "A0N", "A0O", "A0P", "A0Q", "A0R", "A0S", "A0T", "A0U", "A0K", "A0W", "A04", "A0J",
        "A0L", "A0V", "A0e", "A0d", "A0H", "A0M", "A0j", "A0b", "A0h", "A0Z", "A0a", "A0c", "A0g", "A0i"
    };
    private NativeFeed() {}

    /** Stock disk/local deliveries bypass network parsing; filter at their adapter boundary too. */
    public static List<?> localRows(Object controller,Object envelope,List<?> rows) {
        if (CalmaConfig.mode()==0) return rows;
        try {
            Object session=StockAccess.get(controller,"A0X"), nativeRequest=StockAccess.get(envelope,"A00");
            Request request=NATIVE_REQUESTS.get(nativeRequest);
            if (request!=null && request.epoch!=CalmaConfig.sessionId()) return java.util.Collections.emptyList();
            Object params=StockAccess.get(nativeRequest,"A0L");
            boolean following=request!=null ? request.following : params instanceof Map && "following".equals(((Map<?,?>)params).get("pagination_source"));
            return filter(rows,true,System.currentTimeMillis()/1000,following,session).items;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return java.util.Collections.emptyList(); }
    }

    private static boolean awaitingDelivery(Object session) {
        String owner = NativeRelations.owner(session);
        synchronized (NATIVE_REQUESTS) {
            for (Request request : NATIVE_REQUESTS.values())
                if (owner.equals(request.owner) && request.epoch == CalmaConfig.sessionId() && request.context != null) return true;
        }
        return false;
    }

    /** Called only for the stock main-feed request's parameter map. */
    public static Map<?, ?> parameters(Map<?, ?> original) {
        if ("BLENDED_FOR_YOU".equals(CalmaConfig.feed()) && !NativeTimeline.internal()) {
            Object source = original == null ? null : original.get("pagination_source");
            if ("following".equals(source) || "favorites".equals(source)) {
                Map<Object,Object> result = new HashMap<>(original); result.put("pagination_source", "feed_recs"); return result;
            }
        }
        if ("FAVORITES".equals(CalmaConfig.feed()) && !NativeTimeline.internal()) {
            Map<Object, Object> result = original == null ? new HashMap<>() : new HashMap<>(original);
            result.put("pagination_source", "favorites"); return result;
        }
        if (CalmaConfig.mode() == 0) return original;
        Map<Object, Object> result = original == null ? new HashMap<>() : new HashMap<>(original);
        result.put("pagination_source", "following");
        result.put("feed_type", "FOLLOWING");
        return result;
    }

    /** The parser carries the actual requesting account, including cached response parsing. */
    public static void response(Object response, Object parser) {
        if (response == null || parser == null) return;
        try {
            Object session = StockAccess.get(parser, "A01");
            RAW.put(response, new RawPage(response));
            String responseId = (String) StockAccess.get(response, "A0P");
            Request request = responseId == null ? null : REQUESTS.remove(NativeRelations.owner(session) + ':' + responseId);
            if ((request != null && request.context != null) || awaitingDelivery(session)) {
                // The main delivery callback owns filtering. Do not discard unknown friends
                // or consume a cursor while the stock response is still being constructed.
                // The server may omit or replace request_id, including for an empty Friends page.
                StockAccess.set(response,"A0E",Boolean.TRUE); StockAccess.set(response,"A0J",Integer.valueOf(0));
                adsOnly(response,"A0S",true); adsOnly(response,"A0U",false); return;
            }
            process(response, session, request, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
    private static void process(Object response, Object session, Request request, boolean delivery) {
        try {
            String owner = NativeRelations.owner(session);
            if (owner.length() == 0) return;
            if (NativeTimeline.capture(response, owner)) return;
            long now = System.currentTimeMillis() / 1000L;
            if (request != null) CalmaConfig.scope(request.mode);
            // Ads stay disabled in every feed mode, including native/default.
            // These are the stock response's client-insertion controls.
            StockAccess.set(response, "A0E", Boolean.TRUE);
            StockAccess.set(response, "A0J", Integer.valueOf(0));
            if (CalmaConfig.mode() != 0) {
                // Native response keys disable_client_insertions and suggested_users:
                // prevent a second client-side suggestion source after response filtering.
                StockAccess.set(response, "A08", null);
            }
            if (request != null && request.epoch != CalmaConfig.sessionId()) {
                StockAccess.set(response, "A0S", new ArrayList<>()); StockAccess.set(response, "A0U", new ArrayList<>());
                NativeTimeline.finish(response); return;
            }
            if (CalmaConfig.mode() == 0) {
                adsOnly(response, "A0S", true); adsOnly(response, "A0U", false); return;
            }
            Object source = StockAccess.get(response, "A0O");
            boolean following = "following".equals(source) || (request != null && request.following);
            if (delivery && following && request != null && request.context != null) {
                NativeTimelineProgress.response(response, request.context, request.head, request.epoch, now);
                return;
            }
            Object wrappers = StockAccess.get(response, "A0S");
            Object media = StockAccess.get(response, "A0U");
            List<?> rawWrappers = wrappers instanceof List ? (List<?>) wrappers : java.util.Collections.emptyList();
            List<?> rawMedia = media instanceof List ? (List<?>) media : java.util.Collections.emptyList();
            List<String> missing = missing(rawWrappers, rawMedia, session, now);
            if (!missing.isEmpty()) {
                NativeRelationLookup.refresh(session, missing);
            }
            boolean exhausted = true, sawPosts = false;
            Page wrapperPage = null, mediaPage = null;
            if (wrappers instanceof List) {

                wrapperPage = filter((List<?>) wrappers, true, now, following, session);
                sawPosts |= wrapperPage.sawPosts;
                if (wrapperPage.sawPosts) exhausted &= wrapperPage.entirePageOlder;
            }
            if (media instanceof List) {

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
            // Never let extension errors crash cache parsing on the UI thread, or return
            // a successful empty page with auto-pagination still switched on.
            try {
                NativeTimeline.Snapshot previous = NativeTimeline.saved(session);
                if (previous != null) previous.apply(response, session);
                else {
                    StockAccess.set(response, "A0S", new ArrayList<>());
                    StockAccess.set(response, "A0U", new ArrayList<>());
                }
                NativeTimeline.finish(response);
                NativeFeedEnd.appendStatus(response, session, false);
            } catch (ReflectiveOperationException | RuntimeException unsupportedShape) {
                try {
                    StockAccess.set(response, "A0S", new ArrayList<>());
                    StockAccess.set(response, "A0U", new ArrayList<>());
                    NativeTimeline.finish(response);
                } catch (ReflectiveOperationException ignored) {}
            }
        } finally { CalmaConfig.scope(null); }
    }

    /** Keep original ranking, suggestions, pagination, dates and Reels in the two stock modes. */
    static void adsOnly(Object response, String field, boolean wrapped) throws ReflectiveOperationException {
        Object value = StockAccess.get(response, field); if (!(value instanceof List)) return;
        List<Object> clean = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item == null || (wrapped && NativeAds.feedWrapper(item))) continue;
            Object media = wrapped ? StockAccess.call(item, "A0A") : item;
            if (!NativeAds.media(media)) clean.add(item);
        }
        StockAccess.set(response, field, clean);
    }
    private static final class DatedItem {
        final Object item;
        final long time;
        DatedItem(Object item, long time) { this.item = item; this.time = time; }
    }
    static final class Page {
        final List<Object> items;
        final boolean entirePageOlder, sawPosts, ordered;
        final long newest, oldest;
        Page(List<Object> items, boolean entirePageOlder, boolean sawPosts, boolean ordered, long newest, long oldest) {
            this.items = items; this.entirePageOlder = entirePageOlder; this.sawPosts = sawPosts;
            this.ordered = ordered; this.newest = newest; this.oldest = oldest;
        }
    }
    static Page filter(List<?> original, boolean wrapped, long now, boolean following, Object session)
            throws ReflectiveOperationException {
        List<DatedItem> posts = new ArrayList<>(original.size());
        List<Object> controls = new ArrayList<>();
        List<Integer> controlSlots = new ArrayList<>();
        Set<String> pageIds = new HashSet<>();
        boolean sawPost = false, allOlder = true, descending = true;
        long previous = Long.MAX_VALUE, newest = Long.MIN_VALUE, oldest = Long.MAX_VALUE;
        for (Object item : original) {
            try {
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
            Object author = StockAccess.call(dictionary, "A33");
            boolean permitted = NativeRelations.permitted(session, author, CalmaConfig.mode(), following);
            if (!permitted) {
                Object collaborators = StockAccess.call(dictionary, "A8F");
                if (collaborators instanceof List) for (Object collaborator : (List<?>) collaborators) {
                    if (NativeRelations.permitted(session, collaborator, CalmaConfig.mode(), false)) { permitted = true; break; }
                }
            }
            if (!permitted) continue;
            String id = (String) StockAccess.call(dictionary, "getId");
            if (id != null && id.length() > 0 && pageIds.add(id)) posts.add(new DatedItem(item, time));
            } catch (ReflectiveOperationException | RuntimeException malformedRow) {
                allOlder = false; descending = false;
            }
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

    static List<String> missing(List<?> wrappers, List<?> media, Object session, long now) {
        List<String> ids = new ArrayList<>();
        if (CalmaConfig.mode() != 2) return ids;
        collectMissing(wrappers, true, session, now, ids);
        collectMissing(media, false, session, now, ids);
        return new ArrayList<>(new java.util.LinkedHashSet<>(ids));
    }
    private static void collectMissing(List<?> items, boolean wrapped, Object session, long now, List<String> ids) {
        for (Object item : items) {
            try {
                if (item == null || (wrapped && NativeAds.feedWrapper(item))) continue;
                Object media = wrapped ? StockAccess.call(item, "A0A") : item;
                if (media == null || NativeAds.media(media)) continue;
                Object dictionary = StockAccess.get(media, "A04");
                if (!RecentFeedPolicy.withinWindow((Long) StockAccess.call(dictionary, "A6X"), now)) continue;
                Object author = StockAccess.call(dictionary, "A33");
                if (author != null) {
                    String id = (String) StockAccess.call(author, "getId");
                    if (id != null && id.matches("[0-9]+") && !NativeRelations.verified(session, id)) ids.add(id);
                }
                Object collaborators = StockAccess.call(dictionary, "A8F");
                if (collaborators instanceof List) for (Object collaborator : (List<?>) collaborators)
                    if (collaborator != null) {
                        String id = (String) StockAccess.call(collaborator, "getId");
                        if (id != null && id.matches("[0-9]+") && !NativeRelations.verified(session, id)) ids.add(id);
                    }
            } catch (ReflectiveOperationException | RuntimeException missingOptionalAuthor) { /* Filter rejects malformed items. */ }
        }
    }
    static boolean oldPage(List<?> wrappers, List<?> media, long now, ChronologyState chronology, boolean head)
            throws ReflectiveOperationException {
        Page w = filter(wrappers, true, now, true, null);
        Page m = filter(media, false, now, true, null);
        boolean sawPosts = w.sawPosts || m.sawPosts;
        boolean ordered = chronology.observe(1, head, Math.max(w.newest, m.newest), Math.min(w.oldest, m.oldest), w.ordered && m.ordered);
        return sawPosts && ordered && (!w.sawPosts || w.entirePageOlder) && (!m.sawPosts || m.entirePageOlder);
    }

    private static boolean suggestion(Object wrapper) throws ReflectiveOperationException {
        try { if (StockAccess.get(wrapper, "A0n") != null || StockAccess.get(wrapper, "A0m") != null) return true; }
        catch (NoSuchFieldException testModel) {}
        if (CalmaConfig.reels() && StockAccess.get(wrapper, "A03") != null) return true;
        if (CalmaConfig.mode() != 0) {
            for (String field : SUGGESTION_FIELDS) if (StockAccess.get(wrapper, field) != null) return true;
        }
        return false;
    }
}
