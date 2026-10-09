package es.calma.instagram.nativeapp;

/** Only a continuous, observed chronological stream can prove the 48-hour boundary. */
final class ChronologyState {
    private long epoch = Long.MIN_VALUE, previousOldest = Long.MAX_VALUE;
    private boolean chronological = true;
    synchronized boolean observe(long currentEpoch, boolean head, long newest, long oldest, boolean ordered) {
        if (head || epoch != currentEpoch) { epoch = currentEpoch; previousOldest = Long.MAX_VALUE; chronological = true; }
        if (!ordered || newest > previousOldest) chronological = false;
        previousOldest = Math.min(previousOldest, oldest);
        return chronological;
    }
}
