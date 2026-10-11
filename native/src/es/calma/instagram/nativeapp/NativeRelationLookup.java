package es.calma.instagram.nativeapp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Compatibility entry points for locally learned native relationships.
 * Never request friendship data: Instagram owns authentication, limits and security challenges.
 */
final class NativeRelationLookup {
    private NativeRelationLookup() {}

    static CompletableFuture<Void> refresh(Object session, List<String> input) {
        if (input == null || input.isEmpty()) return CompletableFuture.completedFuture(null);
        String owner = NativeRelations.owner(session);
        if (owner.length() != 0) {
            boolean resolved = true;
            for (String id : new LinkedHashSet<>(input)) {
                if (id != null && id.matches("[0-9]+") && !NativeRelations.resolved(session, id)) {
                    resolved = false;
                    break;
                }
            }
            if (resolved) return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> unavailable = new CompletableFuture<>();
        unavailable.completeExceptionally(new IllegalStateException("Relationship is not available in Instagram's local state"));
        return unavailable;
    }

    /** Local rendering decides whether native relationship state is sufficient; never wait or fetch. */
    static void awaitOffMainThread(Object session, List<String> ids) { }

    static boolean mainThread() {
        try {
            Class<?> looper = Class.forName("android.os.Looper");
            return looper.getMethod("myLooper").invoke(null) == looper.getMethod("getMainLooper").invoke(null);
        } catch (ReflectiveOperationException noAndroidRuntime) { return "main".equals(Thread.currentThread().getName()); }
    }
}
