package es.calma.instagram.nativeapp;

import java.util.ArrayList;
import java.util.Iterator;

/** Clock-hour earnings, spent oldest first. This class has no Android dependency. */
final class HourlyCredits {
    static final long HOUR = 3600000L, DAY = 24 * HOUR;
    private long through;
    private int rate;
    private final ArrayList<long[]> buckets = new ArrayList<>();

    HourlyCredits(long now, int minutes) { through = now / HOUR; rate = clamp(minutes); }
    static int clamp(int minutes) { return Math.max(0, Math.min(60, minutes)); }

    synchronized long balance(long now) {
        advance(now);
        long result = 0;
        for (long[] bucket : buckets) result += bucket[1];
        return result;
    }
    private void advance(long now) {
        long hour = now / HOUR;
        // A backwards clock never earns the same hour twice.
        if (hour > through) {
            for (long h = Math.max(through + 1, hour - 23); h <= hour; h++) {
                if (rate > 0) buckets.add(new long[]{h * HOUR + DAY, rate * 60000L});
            }
            through = hour;
        }
        buckets.removeIf(bucket -> bucket[0] <= now || bucket[1] <= 0);
    }
    synchronized void rate(long now, int minutes) { advance(now); rate = clamp(minutes); }
    synchronized int rate() { return rate; }

    /** Settle active time before granting the next hour; new earnings cannot pay old usage. */
    synchronized void spend(long wallStart, long wallEnd, long activeMillis) {
        long span = Math.max(0, wallEnd - wallStart);
        long cursor = wallStart, remaining = Math.max(0, activeMillis);
        while (cursor < wallEnd) {
            advance(cursor);
            long next = Math.min(wallEnd, (cursor / HOUR + 1) * HOUR);
            for (long[] b : buckets) if (b[0] > cursor) next = Math.min(next, b[0]);
            long part = next == wallEnd ? remaining : Math.min(remaining,
                    activeMillis * (next - cursor) / Math.max(1, span));
            consume(part, cursor); remaining -= part; cursor = next;
        }
        if (span == 0) consume(remaining, wallEnd);
        advance(wallEnd);
    }
    private void consume(long amount, long at) {
        for (Iterator<long[]> it = buckets.iterator(); it.hasNext() && amount > 0;) {
            long[] b = it.next(); long used = Math.min(b[1], amount);
            if (b[0] - DAY > at) continue;
            b[1] -= used; amount -= used; if (b[1] == 0) it.remove();
        }
    }
    synchronized String save() {
        StringBuilder s = new StringBuilder().append(through).append(',').append(rate);
        for (long[] b : buckets) s.append(';').append(b[0]).append(',').append(b[1]);
        return s.toString();
    }
    static HourlyCredits restore(String value, long now, int rate) {
        HourlyCredits result = new HourlyCredits(now, rate);
        if (value == null || value.length() > 4096) return result;
        try {
            String[] parts = value.split(";"); String[] head = parts[0].split(",");
            result.through = Long.parseLong(head[0]); result.rate = clamp(Integer.parseInt(head[1]));
            if (result.through < 0 || parts.length > 25) throw new IllegalArgumentException();
            long previous = 0;
            for (int i = 1; i < parts.length; i++) {
                String[] p = parts[i].split(","); long expires = Long.parseLong(p[0]), amount = Long.parseLong(p[1]);
                if (expires <= previous || amount <= 0 || amount > 3600000L) throw new IllegalArgumentException();
                result.buckets.add(new long[]{expires, amount}); previous = expires;
            }
            result.advance(now); return result;
        } catch (RuntimeException corrupt) { return new HourlyCredits(now, rate); }
    }
}
