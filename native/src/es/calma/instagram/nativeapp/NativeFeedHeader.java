package es.calma.instagram.nativeapp;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;
import java.util.WeakHashMap;

/** Only registered Home titles are changed; profile/search title switchers remain stock. */
public final class NativeFeedHeader {
    private static final WeakHashMap<Object, Boolean> home = new WeakHashMap<>();
    private static final String LOGO = "inhouse:home-wordmark";
    private static boolean updating;
    private NativeFeedHeader() {}

    public static int layout(int original) {
        return CalmaConfig.enabled() ? 2131627659 : original;
    }
    public static void classicCreated(Object title, Object helper) {
        if (!CalmaConfig.enabled()) return;
        try {
            StockAccess.set(helper, "A06", title);
            bind(title);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("CalmaFeed", "Home title binding failed", error);
        }
    }
    public static void bind(Object title) {
        if (!CalmaConfig.enabled() || !(title instanceof ViewAnimator)) return;
        home.put(title, Boolean.TRUE);
        update(title);
    }
    /** Runs after stock logo/title, delayed text, theme and alignment updates. No polling. */
    public static void update(Object title) {
        if (updating || !CalmaConfig.enabled() || !home.containsKey(title)) return;
        updating = true;
        try {
            ViewAnimator switcher = (ViewAnimator) title;
            String mode = CalmaConfig.feed();
            String label = "FAVORITES".equals(mode) ? "Favourites" :
                "BLENDED_FOR_YOU".equals(mode) ? "For you" : "Following";
            // Inflate the stock lazy secondary label and keep its original chevron and typeface.
            StockAccess.method(title.getClass(), "A06", boolean.class, String.class).invoke(title, true, label);
            LinearLayout row = (LinearLayout) StockAccess.get(title, "A0D");
            TextView text = (TextView) StockAccess.get(title, "A0E");
            ImageView source = (ImageView) StockAccess.get(title, "A05");
            if (row == null || text == null) return;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            float density = row.getResources().getDisplayMetrics().density;
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams) text.getLayoutParams();
            textParams.width = 0; textParams.weight = 1;
            text.setLayoutParams(textParams);
            if (source != null && source.getDrawable() != null) {
                ImageView logo = row.findViewWithTag(LOGO);
                if (logo == null) {
                    logo = new ImageView(row.getContext()); logo.setTag(LOGO);
                    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int)(72*density), (int)(24*density));
                    params.leftMargin = (int)(8*density);
                    row.addView(logo, params);
                }
                Drawable drawable = source.getDrawable();
                logo.setImageDrawable(drawable.getConstantState() == null ? drawable :
                    drawable.getConstantState().newDrawable(row.getResources()).mutate());
                logo.setColorFilter(source.getColorFilter());
            }
            ViewGroup.LayoutParams params = row.getLayoutParams();
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (params instanceof FrameLayout.LayoutParams)
                ((FrameLayout.LayoutParams) params).gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            row.setLayoutParams(params);
            row.setMinimumHeight((int)(48*density));
            switcher.setMeasureAllChildren(false);
            switcher.setContentDescription(label + ", cambiar feed");
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("CalmaFeed", "Home title update failed", error);
        } finally { updating = false; }
    }
}
