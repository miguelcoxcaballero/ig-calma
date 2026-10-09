package es.calma.instagram.nativeapp;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** The original end-of-feed row, with a Calma-only inline drawing (never an overlay). */
public final class NativeFeedEnd {
    private static final String ID = "calma:timeline:complete";
    private NativeFeedEnd() {}
    /** Replaced by a mapped DEX allocation: Redex removed the model constructor. */
    public static Object newModel(Object unused, Object unused2) { return null; }
    static void append(Object response, Object session) throws ReflectiveOperationException {
        appendStatus(response, session, true);
    }
    static void appendStatus(Object response, Object session, boolean complete) throws ReflectiveOperationException {
        ClassLoader loader = session.getClass().getClassLoader();
        Class<?> model = Class.forName("X.0AMQ", false, loader);
        Object data = newModel(null, null);
        if (data == null) throw new ReflectiveOperationException("Native end model factory missing");
        StockAccess.set(data, "A0C", complete ? ID : "calma:timeline:retry");
        StockAccess.set(data, "A0G", complete ? "That's it" : "No se pudo cargar");
        StockAccess.set(data, "A0F", complete ? "" : "Desliza hacia abajo para reintentar");
        StockAccess.set(data, "A08", false);
        StockAccess.set(data, "A07", false);
        // HOMECOMING gives a normal, measurable native row even when there are no posts.
        Class<?> style = Class.forName("X.0AML", false, loader);
        Object home = style.getMethod("valueOf", String.class).invoke(null, "HOMECOMING");
        StockAccess.set(data, "A02", home);
        Class<?> type = Class.forName("X.04a3", false, loader);
        Object endType = type.getField("A0G").get(null);
        Class<?> contentType = Class.forName("X.06qT", false, loader);
        Object content = contentType.getConstructor(model, type).newInstance(data, endType);
        Class<?> wrapper = Class.forName("X.05qw", false, loader);
        Object row = wrapper.getConstructor(Class.forName("X.0UkD", false, loader), type, String.class)
                .newInstance(content, endType, complete ? ID : "calma:timeline:retry");
        Object original = StockAccess.get(response, "A0S");
        List<Object> rows = original instanceof List ? new ArrayList<>((List<?>) original) : new ArrayList<>();
        // Some responses only use the direct-media representation: normalize through the native wrapper factory.
        if (!(original instanceof List)) {
            Object media = StockAccess.get(response, "A0U");
            if (media instanceof List) for (Object item : (List<?>) media)
                rows.add(StockAccess.method(wrapper, "A01", item.getClass()).invoke(null, item));
        }
        rows.add(row);
        StockAccess.set(response, "A0S", rows);
    }
    public static boolean bind(View view, Object content) {
        if (view == null || content == null) return false;
        boolean own;
        try { own = ID.equals(StockAccess.call(content, "DZg")); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        if (!own) {
            if (view.getBackground() instanceof EndDrawable) {
                view.setBackground(null);
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params != null) { params.height = ViewGroup.LayoutParams.WRAP_CONTENT; view.setLayoutParams(params); }
                if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                    ((ViewGroup) view).getChildAt(i).setVisibility(View.VISIBLE);
                view.setMinimumHeight(0);
                view.setContentDescription(null);
            }
            return false;
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            ((ViewGroup) view).getChildAt(i).setVisibility(View.GONE);
        float density = view.getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) { params.height = Math.round(280 * density); view.setLayoutParams(params); }
        view.setMinimumHeight(Math.round(280 * density));
        view.setBackground(new EndDrawable(view));
        view.setContentDescription("That's it");
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        view.setOnClickListener(null);
        return true;
    }
    private static final class EndDrawable extends Drawable {
        private final View view;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        EndDrawable(View view) { this.view = view; }
        @Override public void draw(Canvas canvas) {
            float density = view.getResources().getDisplayMetrics().density;
            float x = getBounds().exactCenterX(), y = getBounds().top + 104 * density, radius = 38 * density;
            boolean dark = (view.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            paint.setColor(dark ? Color.rgb(245,245,245) : Color.rgb(20,20,20));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f * density); paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x - 12 * density, y - 8 * density, 2.3f * density, paint);
            canvas.drawCircle(x + 12 * density, y - 8 * density, 2.3f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawArc(new RectF(x - 17 * density, y - 10 * density, x + 17 * density, y + 22 * density), 20, 140, false, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(30 * view.getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText("That's it", x, y + 92 * density, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
