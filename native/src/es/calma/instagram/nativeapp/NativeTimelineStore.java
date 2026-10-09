package es.calma.instagram.nativeapp;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;

/** Private per-account metadata cache using the pinned native Media codec. No credentials or bitmaps. */
final class NativeTimelineStore {
    private static final int MAGIC = 0x43414c37;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private NativeTimelineStore() {}
    private static File file(NativeTimeline.Context context) throws Exception {
        File root = (File) StockAccess.call(context.androidContext, "getFilesDir");
        String owner = NativeRelations.owner(context.session);
        if (!owner.matches("[0-9]+")) throw new IOException("Invalid account");
        File directory = new File(root, "calma-timeline");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No cache directory");
        return new File(directory, owner + "-" + context.mode + ".snapshot");
    }
    static void write(NativeTimeline.Context context, NativeTimeline.Snapshot snapshot) {
        File temporary = null;
        try {
            File destination = file(context); temporary = new File(destination.getPath() + ".tmp");
            LinkedHashMap<String,Object> posts = new LinkedHashMap<>();
            if (snapshot.wrappers != null) for (Object row : snapshot.wrappers) {
                Object media = StockAccess.call(row, "A0A"); if (media != null) add(posts, media);
            }
            if (snapshot.media != null) for (Object media : snapshot.media) add(posts, media);
            ClassLoader loader = context.session.getClass().getClassLoader();
            Class<?> codec = Class.forName("com.instagram.feed.media.MediaExtKt", false, loader);
            Class<?> mediaType = Class.forName("com.instagram.feed.media.Media", false, loader);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
                out.writeInt(MAGIC); out.writeLong(snapshot.anchor); out.writeBoolean(CalmaConfig.reels()); out.writeInt(posts.size());
                long written = 0;
                for (Object media : posts.values()) {
                    byte[] data = (byte[]) StockAccess.method(codec, "A1h", mediaType).invoke(null, media);
                    if (data == null || data.length > 4*1024*1024 || (written += data.length) > MAX_BYTES) throw new IOException("Metadata cache budget exceeded");
                    out.writeInt(data.length); out.write(data); out.write(MessageDigest.getInstance("SHA-256").digest(data));
                }
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception optionalCacheFailure) {
            if (temporary != null) temporary.delete();
        }
    }
    private static void add(Map<String,Object> posts, Object media) throws ReflectiveOperationException {
        String id = (String) StockAccess.call(StockAccess.get(media,"A04"),"getId");
        if (id != null) posts.putIfAbsent(id, media);
    }
    static NativeTimeline.Snapshot read(NativeTimeline.Context context, Object head, long now) {
        try {
            File source = file(context);
            if (source.length() > MAX_BYTES + 1024*1024) return null;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(source)))) {
                if (in.readInt() != MAGIC) return null;
                long anchor = in.readLong();
                if (anchor > now || now - anchor > 15*60 || in.readBoolean() != CalmaConfig.reels()) return null;
                int count = in.readInt(); if (count < 0 || count > 100000) return null;
                ClassLoader loader = context.session.getClass().getClassLoader();
                Class<?> decoder = Class.forName("X.04ve", false, loader);
                Class<?> wrapper = Class.forName("X.05qw", false, loader);
                Class<?> mediaType = Class.forName("com.instagram.feed.media.Media", false, loader);
                List<Object> rows = new ArrayList<>(), media = new ArrayList<>();
                Object controls = StockAccess.get(head, "A0S");
                if (controls instanceof List) {
                    NativeFeed.Page filtered = NativeFeed.filter((List<?>) controls, true, anchor, true, context.session);
                    for (Object row : filtered.items) if (StockAccess.call(row, "A0A") == null) rows.add(row);
                }
                for (int i=0;i<count;i++) {
                    int length=in.readInt(); if (length <= 0 || length > 4*1024*1024) return null;
                    byte[] data=new byte[length], checksum=new byte[32];in.readFully(data);in.readFully(checksum);
                    if (!MessageDigest.isEqual(checksum, MessageDigest.getInstance("SHA-256").digest(data))) return null;
                    Object parsed=StockAccess.method(decoder,"A00",context.session.getClass(),byte[].class).invoke(null,context.session,data);
                    Object item=StockAccess.get(parsed,"A00"); if (item == null || NativeAds.media(item)) return null;
                    media.add(item);rows.add(StockAccess.method(wrapper,"A01",mediaType).invoke(null,item));
                }
                if (in.read() != -1) return null;
                return new NativeTimeline.Snapshot(rows, media, anchor);
            }
        } catch (Exception absentOrInvalidCache) { return null; }
    }
}
