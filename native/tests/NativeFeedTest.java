package es.calma.instagram.nativeapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class NativeFeedTest {
    public static final class Session { final String owner; Session(){this("100");} Session(String owner){this.owner=owner;} public String getUserId() { return owner; } }
    public static final class Status { public boolean A0H; public Boolean A02; Status(boolean following,Boolean followedBy){A0H=following;A02=followedBy;} }
    public static final class Parser { public Object A01 = new Session(); }
    public static final class Friendship {
        private final Boolean following, followedBy;
        Friendship(Boolean following, Boolean followedBy) { this.following = following; this.followedBy = followedBy; }
        public Boolean C66() { return following; }
        public Boolean C5v() { return followedBy; }
    }
    public static final class UserDictionary {
        private final Friendship friendship;
        UserDictionary(Boolean following, Boolean followedBy) { friendship = new Friendship(following, followedBy); }
        public Friendship C8H() { return friendship; }
        public Boolean A1J() { return friendship.followedBy; }
        public Object C5s() { return null; }
    }
    public static final class User {
        public UserDictionary A00;
        User(Boolean following, Boolean followedBy) { A00 = new UserDictionary(following, followedBy); }
    }
    public static final class MediaDictionary {
        final String id, kind;
        final Long timestamp;
        final User user;
        List<User> collaborators = Collections.emptyList();
        MediaDictionary(String id, Long timestamp, String kind, User user) { this.id = id; this.timestamp = timestamp; this.kind = kind; this.user = user; }
        public String getId() { return id; }
        public Long A6X() { return timestamp; }
        public String A7W() { return kind; }
        public User A33() { return user; }
        public List<User> A8F() { return collaborators; }
    }
    public static final class Media {
        public MediaDictionary A04;
        public boolean sponsored;
        Media(String id, Long timestamp, String kind, User user) { A04 = new MediaDictionary(id, timestamp, kind, user); }
        public boolean EKS() { return sponsored; }
    }
    public static final class Wrapper {
        public Object A0s, A03, A0N, A0O, A0P, A0Q, A0R, A0S, A0T, A0U, A0K, A0W, A04, A0J;
        public Object A0L, A0V, A0e, A0d, A0H, A0M, A0j, A0b, A0h, A0Z, A0a, A0c, A0g, A0i, A0Y, A09, A0x, A0y;
        Wrapper(Object media) { A0s = media; }
        public Object A0A() { return A0s; }
    }
    public static final class Story {
        final boolean sponsored;
        Story(boolean sponsored) { this.sponsored = sponsored; }
        public boolean EKS() { return sponsored; }
    }
    public static final class Request {
        public String A0H = "request", A0G;
        public Map<String,String> A0L = Collections.emptyMap();
    }
    public static final class Response {
        public Object A08;
        public Boolean A0E;
        public Integer A0J = 4;
        public List<Object> A0S;
        public List<Object> A0U;
        public String A0N = "next-page", A0O = "following", A0P = "request";
        public boolean A0a = true, A0W = true;
    }
    private static int checks;
    private static void check(boolean test, String reason) { if (!test) throw new AssertionError(reason); checks++; }
    private static Media post(String id, long timestamp) { return new Media(id, timestamp, "feed", new User(true, true)); }
    private static Response response(Object... media) {
        Response response = new Response(); response.A0U = new ArrayList<>(Arrays.asList(media)); return response;
    }
    private static void apply(Response response) { CalmaConfig.epoch++; NativeFeed.response(response, new Parser()); }

    public static void main(String[] args) {
        long now = System.currentTimeMillis() / 1000;
        check(RecentFeedPolicy.withinWindow(now - 172800, now), "inclusive 48-hour boundary");
        check(!RecentFeedPolicy.withinWindow(now - 172801, now), "older than boundary excluded");
        check(!RecentFeedPolicy.withinWindow(null, now), "unknown dates not fabricated");
        check("following".equals(NativeFeed.parameters(null).get("pagination_source")), "null optional map still selects native Following");
        Map<String, String> original = Collections.singletonMap("cursor", "42");
        Map<?, ?> request = NativeFeed.parameters(original);
        check("following".equals(request.get("pagination_source")) && "42".equals(request.get("cursor")), "native following request keeps pagination");
        check(!original.containsKey("pagination_source"), "immutable original request preserved");

        Media pendingAuthor = new Media("pending-following", now - 20, "feed", new User(null, null));
        Response missingSource = response(pendingAuthor); missingSource.A0O = null;
        NativeFeed.request(new Session(), new Request()); NativeFeed.response(missingSource, new Parser());
        check(missingSource.A0U.contains(pendingAuthor), "known native following request covers an absent response source without extra lookups");
        check(!NativeRelations.permitted(null, 1, true), "missing author is not assumed to be a followed account");
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 125; i++) many.add(post("post" + i, now - 600 - i));
        Response unrestrictedCount = new Response(); unrestrictedCount.A0U = many; apply(unrestrictedCount);
        check(unrestrictedCount.A0U.size() == 125 && unrestrictedCount.A0a, "125 recent posts remain; no 20/100 post cap");

        Media older = post("older", now - 172900), newest = post("new", now - 10), recent = post("recent", now - 60);
        Response mixed = response(recent, older, newest, newest); apply(mixed);
        check(mixed.A0U.equals(Arrays.asList(newest, recent)), "newest-first ordering, age filtering, deduplication");
        check(mixed.A0a && "next-page".equals(mixed.A0N), "partly filtered page retains native next cursor");
        Media sponsored = post("followed-ad", now - 5); sponsored.sponsored = true;
        Media oldSponsored = post("old-ad", now - 900000); oldSponsored.sponsored = true;
        for (int mode : new int[]{0, 1, 2}) {
            CalmaConfig.testMode = mode;
            Response ads = response(sponsored, newest); apply(ads);
            check(ads.A0U.equals(Arrays.asList(newest)), "sponsored mutual-follow media excluded in mode " + mode);
            check(Boolean.TRUE.equals(ads.A0E) && Integer.valueOf(0).equals(ads.A0J), "native client ad insertion disabled in mode " + mode);
        }
        CalmaConfig.testMode = 1;
        Response onlyAds = response(oldSponsored); apply(onlyAds);
        check(onlyAds.A0U.isEmpty() && onlyAds.A0a && onlyAds.A0W && "next-page".equals(onlyAds.A0N), "old ad-only page must not terminate organic pagination");
        Response interleaved = response(newest, oldSponsored, recent); apply(interleaved);
        check(interleaved.A0U.equals(Arrays.asList(newest, recent)) && interleaved.A0a, "ad timestamps do not affect organic chronology");
        Wrapper ad4ad = new Wrapper(null), prebuiltAd = new Wrapper(null);
        ad4ad.A0x = new Object(); prebuiltAd.A0y = new Object();
        Response wrappedAds = new Response();
        Wrapper cleanPost = new Wrapper(newest);
        wrappedAds.A0S = Arrays.asList(ad4ad, new Wrapper(sponsored), prebuiltAd, cleanPost); apply(wrappedAds);
        check(wrappedAds.A0S.equals(Arrays.asList(cleanPost)) && wrappedAds.A0a, "promotion cards and prebuilt sponsored wrappers are removed");
        check(NativeAds.story(new Story(true)), "native ADS_REEL insertion is rejected");
        check(!NativeAds.story(new Story(false)), "ordinary Story and non-sponsored native modules remain unchanged");
        check(!NativeAds.story(null) && !NativeAds.story(new Object()), "unexpected optional Story object does not crash the player");
        Response end = response(post("old1", now - 172900), post("old2", now - 173000)); apply(end);
        check(end.A0U.isEmpty() && !end.A0a && !end.A0W && end.A0N == null, "ordered old following page ends 48-hour feed");
        Response ranked = response(older); ranked.A0O = "feed_recs"; apply(ranked);
        check(ranked.A0a && ranked.A0N != null, "ranked old page does not falsely prove end");
        Response outOfOrder = response(post("a", now - 190000), post("b", now - 180000)); apply(outOfOrder);
        check(outOfOrder.A0a, "out-of-order old page does not prove chronological end");
        Response twoLists = response(newest);
        twoLists.A0S = Arrays.asList(new Wrapper(older)); apply(twoLists);
        check(twoLists.A0a && twoLists.A0U.contains(newest), "old wrapper representation cannot truncate recent alternate list");
        Response future = response(post("future", now + 86400)); apply(future);
        check(future.A0U.isEmpty() && future.A0a, "invalid future timestamp is excluded without declaring an old page");
        Response missingDate = response(new Media("unknown", null, "feed", new User(true, true))); apply(missingDate);
        check(missingDate.A0a && missingDate.A0U.isEmpty(), "unknown date does not stop pagination");

        ChronologyState state = new ChronologyState();
        check(state.observe(1, true, 1000, 900, true), "first chronological page");
        check(state.observe(1, false, 899, 800, true), "strictly older next page");
        check(!state.observe(1, false, 950, 700, true), "late newer post disables chronological cutoff");
        check(!state.observe(1, false, 699, 600, true), "later old pages cannot restore a broken stream");
        check(state.observe(1, true, 2000, 1900, true), "fresh head starts a new chronology");
        CalmaConfig.testMode = 2;
        Media mutual = post("friend", now - 50), nonMutual = new Media("non-mutual", now - 20, "feed", new User(true, false));
        Media unknown = new Media("unknown-mutual", now - 10, "feed", new User(true, null));
        Media collaboration = new Media("collaboration", now - 15, "feed", new User(false, false));
        collaboration.A04.collaborators = Arrays.asList(new User(true, true));
        Response friends = response(mutual, nonMutual, unknown, collaboration); apply(friends);
        check(friends.A0U.equals(Arrays.asList(collaboration, mutual)), "mutual follows and coauthors verified automatically");
        check(friends.A0a, "unknown friendship does not turn a pending page into the end");
        CalmaConfig.testMode = 1;
        Response clips = response(new Media("clip", now - 3, "clips", new User(true, true)), mutual); apply(clips);
        check(clips.A0U.equals(Arrays.asList(mutual)), "feed reels excluded without touching media elsewhere");
        Response storyShell = new Response();
        Wrapper control = new Wrapper(null), suggested = new Wrapper(null);
        suggested.A0U = new Object();
        storyShell.A0S = Arrays.asList(control, suggested, new Wrapper(mutual)); apply(storyShell);
        check(storyShell.A0S.size() == 2 && storyShell.A0S.get(0) == control, "native neutral controls survive while suggestions disappear");
        Wrapper threads = new Wrapper(null), shopping = new Wrapper(null), breakNudge = new Wrapper(null), followRequests = new Wrapper(null);
        threads.A0e = new Object(); shopping.A0H = new Object(); breakNudge.A0Y = new Object(); followRequests.A09 = new Object();
        Response nativeCards = new Response(); nativeCards.A0S = Arrays.asList(threads, breakNudge, shopping, followRequests); apply(nativeCards);
        check(nativeCards.A0S.equals(Arrays.asList(breakNudge, followRequests)), "cross-app recommendations removed while native break and follow-request controls remain");
        Session session = new Session("400");
        Status mutualStatus = new Status(true, true);
        NativeRelations.beginStatus(session, "200", mutualStatus); NativeRelations.endStatus(mutualStatus);
        check(NativeRelations.isMutual(session, "200"), "verified native batch works when User is absent from native cache");
        check(!NativeRelations.isMutual(new Session("401"), "200"), "friendship facts stay within the requesting account");
        Status incompleteStatus = new Status(true, null);
        NativeRelations.beginStatus(session, "201", incompleteStatus); NativeRelations.endStatus(incompleteStatus);
        check(!NativeRelations.resolved(session, "201"), "missing followed_by stays unknown rather than false");
        Status unfollowed = new Status(false, true);
        NativeRelations.beginStatus(session, "200", unfollowed); NativeRelations.endStatus(unfollowed);
        check(!NativeRelations.isMutual(session, "200"), "new verified unfollow overrides older mutual result");
        CalmaConfig.testMode = 0;
        Media algorithmReel = new Media("algorithm-reel", now - 10, "clips", new User(false, false));
        Media algorithmOld = new Media("algorithm-old", now - 300000, "feed", new User(false, false));
        Response algorithm = response(algorithmOld, sponsored, algorithmReel, newest);
        algorithm.A0O = "feed_recs";
        apply(algorithm);
        check(algorithm.A0U.equals(Arrays.asList(algorithmOld, algorithmReel, newest)), "For you keeps algorithm order, old posts, unknown accounts and Reels while removing ads");
        check(algorithm.A0a && algorithm.A0N != null, "stock algorithm pagination remains available");
        Response favorites = response(algorithmOld, algorithmReel); favorites.A0O = "favorites"; apply(favorites);
        check(favorites.A0U.equals(Arrays.asList(algorithmOld, algorithmReel)), "Favorites does not inherit the friends window or Reels filter");
        System.out.println("Native feed: " + checks + " assertions passed");
    }
}
