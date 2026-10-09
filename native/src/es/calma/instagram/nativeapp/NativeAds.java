package es.calma.instagram.nativeapp;

/** Uses the pinned stock model's sponsored-content predicate, not an author's identity. */
public final class NativeAds {
    private NativeAds() {}

    static boolean media(Object media) throws ReflectiveOperationException {
        // Stock Media.EKS is the same injected-ad check used to construct a
        // sponsored FeedItem. Organic branded-content tags alone are not ads.
        return media != null && Boolean.TRUE.equals(StockAccess.call(media, "EKS"));
    }

    static boolean feedWrapper(Object wrapper) throws ReflectiveOperationException {
        // Ad4ad is a promotion unit without Media. A0y is a prebuilt sponsored
        // feed item, which can also exist before its Media has been resolved.
        return StockAccess.get(wrapper, "A0x") != null || StockAccess.get(wrapper, "A0y") != null;
    }

    /** Called with the native Story reel before its ad-insertion adapter is mutated. */
    public static boolean story(Object reel) {
        try {
            // X.03sn.EKS compares its native reel type with ADS_REEL.
            return media(reel);
        } catch (ReflectiveOperationException | RuntimeException unknownReel) {
            // The patched stock version is checked at build time. An unexpected
            // optional object must not prevent the ordinary Story player opening.
            return false;
        }
    }
}
