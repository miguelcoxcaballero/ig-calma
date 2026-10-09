package es.calma.smoke;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;

/** Exercises shipped DEX model allocation and footer drawing without an Instagram account. */
public final class NativeModelSmoke extends Instrumentation {
    public static final class Response {
        public List<Object> A0S = new ArrayList<>();
        public List<Object> A0U;
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        final Throwable[] error = new Throwable[1];
        runOnMainSync(() -> {
            try {
                Context context = getTargetContext();
                ClassLoader loader = context.getClassLoader();
                Class<?> end = Class.forName("es.calma.instagram.nativeapp.NativeFeedEnd", true, loader);
                Object nativeModel = end.getMethod("newModel", Object.class, Object.class).invoke(null, null, null);
                check(nativeModel != null && nativeModel.getClass().getName().equals("X.0AMQ"), "Mapped native model allocation");
                Class<?> selector = Class.forName("es.calma.instagram.nativeapp.NativeFeedSelector", true, loader);
                List<?> options = (List<?>) selector.getMethod("options", Object.class, List.class).invoke(null, nativeModel, null);
                check(options != null && options.size() == 4, "Four actual stock selector models");
                String[] names = {"BLENDED_FOR_YOU", "FAVORITES", "RECENTS", "FOLLOWING"};
                for (int i=0;i<4;i++) check(names[i].equals(((Enum<?>)options.get(i).getClass().getField("A01").get(options.get(i))).name()), "Exact selector order");
                Object friends = options.get(2).getClass().getField("A01").get(options.get(2));
                Class<?> budget=Class.forName("es.calma.instagram.nativeapp.NativeFeedBudget",true,loader);
                long balance=(Long)budget.getMethod("balance").invoke(null);
                List<?> menu = (List<?>) selector.getMethod("menuRows", Context.class, Object.class, List.class, Object.class, Object.class)
                    .invoke(null, context, nativeModel, options, friends, nativeModel);
                check(menu.size()==4,"Four original IGDS popup rows");
                String[] labels={"For you","Favourites","Friends","Following"};
                for(int i=0;i<4;i++) {
                    Object item=menu.get(i); Class<?> rowType=item.getClass();
                    check(rowType.getName().equals("X.0VTM"),"Stock popup model type");
                    check(labels[i].equals(rowType.getField("A03").get(item)),"Native option label");
                    check((Boolean)rowType.getField("A08").get(item)==(i==2),"Friends selected");
                    check((Boolean)rowType.getField("A05").get(item)==(i==0 && balance<=0),"For you disabled with zero credits");
                }
                check(budget.getMethod("label",long.class).invoke(null,balance).equals(menu.get(0).getClass().getField("A04").get(menu.get(0))),"Remaining time in native subtitle");
                Method append = end.getDeclaredMethod("append", Object.class, Object.class); append.setAccessible(true);
                Response response = new Response(); append.invoke(null, response, nativeModel);
                check(response.A0S.size() == 1, "One real native end row");
                Object row = response.A0S.get(0);
                Object content = row.getClass().getMethod("A07").invoke(row);
                check(content.getClass().getName().equals("X.06qT"), "Original feed content type");
                check("calma:timeline:complete".equals(content.getClass().getMethod("DZg").invoke(content)), "Completion identity");
                Method bind = end.getMethod("bind", View.class, Object.class);
                for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                    Configuration configuration = new Configuration(context.getResources().getConfiguration());
                    configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
                    Context themed = context.createConfigurationContext(configuration);
                    LinearLayout view = new LinearLayout(themed);
                    view.setLayoutParams(new ViewGroup.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT));
                    TextView oldChild = new TextView(themed); view.addView(oldChild);
                    check(Boolean.TRUE.equals(bind.invoke(null, view, content)), "Own row selected");
                    check(oldChild.getVisibility() == View.GONE, "No old row contents behind footer");
                    check("That's it".contentEquals(view.getContentDescription()), "Accessible footer");
                    check(view.getLayoutParams().height > 0, "Measured inline row");
                    int height=view.getLayoutParams().height;
                    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    view.layout(0,0,1080,height);
                    Bitmap bitmap=Bitmap.createBitmap(1080,height,Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(bitmap));
                    boolean drawn=false;
                    for(int y=0;y<height && !drawn;y+=2) for(int x=0;x<1080;x+=2) if((bitmap.getPixel(x,y)>>>24)!=0){drawn=true;break;}
                    bitmap.recycle(); check(drawn,"Smiley/text draw in light and dark modes");
                    Object model = content.getClass().getField("A01").get(content);
                    model.getClass().getField("A0C").set(model,"ordinary-native-end-row");
                    check(Boolean.FALSE.equals(bind.invoke(null, view, content)),"Recycled native row restored");
                    check(view.getBackground()==null && oldChild.getVisibility()==View.VISIBLE,"Own drawing removed on native reuse");
                    model.getClass().getField("A0C").set(model,"calma:timeline:complete");
                }
            } catch (Throwable failure) { error[0] = failure; }
        });
        if (error[0] == null) {
            result.putString("stream", "CALMA_NATIVE_MODELS_PASSED: allocation, native row, light/dark drawing, accessibility, recycling, four stock selector models, remaining time, zero-credit lock\n");
            finish(-1,result);
        } else {
            result.putString("stream", "CALMA_NATIVE_MODELS_FAILED: " + android.util.Log.getStackTraceString(error[0]));
            finish(0,result);
        }
    }
}
