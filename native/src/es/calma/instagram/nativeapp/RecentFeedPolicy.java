package es.calma.instagram.nativeapp;

/** Pure time-bound policy shared by native feed filtering and regression tests. */
final class RecentFeedPolicy {
    static final long WINDOW_SECONDS = 48L * 60L * 60L;
    private RecentFeedPolicy() {}
    static boolean withinWindow(Long takenAt, long nowSeconds) {
        return takenAt != null && takenAt > 0 && takenAt >= nowSeconds - WINDOW_SECONDS && takenAt <= nowSeconds + 300L;
    }
}
