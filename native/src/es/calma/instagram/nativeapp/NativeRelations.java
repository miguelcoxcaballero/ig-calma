package es.calma.instagram.nativeapp;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads Instagram's account-scoped model and verified native friendship responses. */
public final class NativeRelations {
    private static final long FRESH_MS = 15 * 60_000L;
    private static final Map<String, State> VERIFIED = new ConcurrentHashMap<>();
    private static final Map<Object, String> PARSING = Collections.synchronizedMap(new IdentityHashMap<>());
    private NativeRelations() {}
    private static final class State {
        final Boolean following, followedBy;
        final long updated;
        State(Boolean following, Boolean followedBy) { this.following = following; this.followedBy = followedBy; this.updated = System.currentTimeMillis(); }
        boolean known(int mode) { return following != null && (mode == 1 || followedBy != null); }
    }
    public static String owner(Object session) {
        try { return (String) StockAccess.call(session, "getUserId"); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return ""; }
    }
    public static Object user(Object session, String id) {
        if (session == null || id == null || id.length() == 0) return null;
        try {
            Class<?> registry = Class.forName("com.instagram.user.model.UserCache", false, session.getClass().getClassLoader());
            Object scoped = StockAccess.method(registry, "A00", session.getClass()).invoke(null, session);
            return StockAccess.callString(scoped, "A06", id);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    /** Capture only the batch parser's exact account/user row, including uncached users. */
    public static void beginStatus(Object session, String id, Object status) {
        String owner = owner(session);
        if (owner.length() == 0 || id == null || !id.matches("[0-9]+") || status == null) return;
        synchronized (PARSING) {
            if (PARSING.size() > 256) PARSING.clear();
            PARSING.put(status, owner + ':' + id);
        }
    }
    public static void endStatus(Object status) {
        String key = PARSING.remove(status);
        if (key == null) return;
        try {
            Boolean following = (Boolean) StockAccess.get(status, "A0H");
            Boolean followedBy = (Boolean) StockAccess.get(status, "A02");
            if (VERIFIED.size() > 10000) VERIFIED.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().updated >= FRESH_MS);
            VERIFIED.put(key, new State(following, followedBy));
        } catch (ReflectiveOperationException | RuntimeException incomplete) { /* Never cache fabricated false values. */ }
    }
    private static State cached(Object session, String id) {
        State state = VERIFIED.get(owner(session) + ':' + id);
        return state != null && System.currentTimeMillis() - state.updated < FRESH_MS ? state : null;
    }
    private static State nativeState(Object user) {
        if (user == null) return new State(null, null);
        try {
            Object dictionary = StockAccess.get(user, "A00");
            Object friendship = StockAccess.call(dictionary, "C8H");
            Boolean following = friendship == null ? null : (Boolean) StockAccess.call(friendship, "C66");
            Boolean followedBy = friendship == null ? null : (Boolean) StockAccess.call(friendship, "C5v");
            if (followedBy == null) followedBy = (Boolean) StockAccess.call(dictionary, "A1J");
            if (following == null) {
                Object status = StockAccess.call(dictionary, "C5s");
                String value = status == null ? "" : status.toString();
                if ("FollowStatusFollowing".equals(value)) following = true;
                else if ("FollowStatusNotFollowing".equals(value) || "FollowStatusRequested".equals(value)) following = false;
            }
            return new State(following, followedBy);
        } catch (ReflectiveOperationException | RuntimeException incomplete) { return new State(null, null); }
    }
    private static State state(Object session, Object user, String id) {
        State nativeState = nativeState(user);
        State response = id == null ? null : cached(session, id);
        if (response == null) return nativeState;
        return new State(response.following == null ? nativeState.following : response.following,
                response.followedBy == null ? nativeState.followedBy : response.followedBy);
    }
    private static String id(Object user) {
        try { return (String) StockAccess.call(user, "getId"); }
        catch (ReflectiveOperationException | RuntimeException missing) { return null; }
    }

    /** Completion may run on a worker; the caller dispatches its UI continuation. */
    public static void resolveMutual(Object session, String id, Runnable completion) {
        if (state(session, user(session, id), id).known(2)) { completion.run(); return; }
        NativeRelationLookup.refresh(session, Collections.singletonList(id)).whenComplete((result, failure) -> completion.run());
    }
    static boolean known(Object user, int mode) { return nativeState(user).known(mode); }
    static boolean known(Object session, Object user, int mode) { return state(session, user, id(user)).known(mode); }
    static boolean verified(Object session, String id) { State value = cached(session, id); return value != null && value.known(2); }
    static boolean resolved(Object session, String id) { return state(session, user(session, id), id).known(2); }
    public static boolean isMutual(Object session, String id) { return permits(state(session, user(session, id), id), 2, false); }
    public static boolean isFollowing(Object session, String id) { return permits(state(session, user(session, id), id), 1, false); }
    static boolean permitted(Object user, int mode, boolean followingResponse) {
        if (mode != 0 && user == null) return false;
        return permits(nativeState(user), mode, followingResponse);
    }
    static boolean permitted(Object session, Object user, int mode, boolean followingResponse) {
        if (mode != 0 && user == null) return false;
        return permits(state(session, user, id(user)), mode, followingResponse);
    }
    private static boolean permits(State state, int mode, boolean followingResponse) {
        if (mode == 0) return true;
        boolean follows = Boolean.TRUE.equals(state.following) || (state.following == null && followingResponse);
        return follows && (mode == 1 || Boolean.TRUE.equals(state.followedBy));
    }
}
