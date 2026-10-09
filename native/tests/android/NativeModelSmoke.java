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
    public static final class Request {
        public String A0H="smoke-head", A0G;
        public Object A09="cold_start_fetch";
        public Map<String,String> A0L=Collections.singletonMap("pagination_source","following");
    }
    private Object allocate(ClassLoader loader,String name) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),Class.forName(name,true,loader));
    }
    private void set(Object target,String name,Object value)throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private Object media(ClassLoader loader,String id,long time,boolean mutual)throws Exception {
        Object user=allocate(loader,"com.instagram.user.model.User");
        Object userDict=allocate(loader,"com.instagram.user.model.LiveTreeUserDict");
        Object userData=allocate(loader,"X.01xA");
        set(userData,"A8X","994411");set(userData,"A2E",mutual);set(userDict,"A00",userData);set(user,"A00",userDict);
        Object media=allocate(loader,"com.instagram.feed.media.Media");
        Object dict=allocate(loader,"com.instagram.feed.media.LiveTreeMediaDict");
        Object data=allocate(loader,"X.03hQ");
        set(data,"AAG",id);set(data,"A6Y",time);set(data,"A7Z","feed");set(data,"A31",user);
        set(dict,"A00",data);set(media,"A04",dict);
        return media;
    }
    private Object wrapper(ClassLoader loader,Object media)throws Exception {
        Object row=allocate(loader,"X.05qw");set(row,"A0p",media);set(row,"A0s",media);return row;
    }
    private Object page(ClassLoader loader,String request,String cursor,Object... rows)throws Exception {
        Object response=allocate(loader,"X.07do");
        set(response,"A0S",new ArrayList<>(Arrays.asList(rows)));set(response,"A0P",request);
        set(response,"A0O","following");set(response,"A0N",cursor);set(response,"A0a",cursor!=null);set(response,"A0W",cursor!=null);
        return response;
    }
    private void feedModels(ClassLoader loader)throws Exception {
        Class<?> config=Class.forName("es.calma.instagram.nativeapp.CalmaConfig",true,loader);
        Method select=config.getDeclaredMethod("select",String.class);select.setAccessible(true);select.invoke(null,"RECENTS");
        Object session=allocate(loader,"com.instagram.common.session.UserSession");set(session,"userId","994400");
        Object parser=allocate(loader,"X.03gD");set(parser,"A01",session);
        Class<?> feed=Class.forName("es.calma.instagram.nativeapp.NativeFeed",true,loader);
        Method context=feed.getMethod("context",Object.class,Object.class,Object.class,Object.class,Object.class,Object.class);
        Method response=feed.getMethod("response",Object.class,Object.class);
        Object firstMedia=media(loader,"99440001",System.currentTimeMillis()/1000-10,true);
        Object nextMedia=media(loader,"99440002",System.currentTimeMillis()/1000-20,true);
        check(Boolean.FALSE.equals(firstMedia.getClass().getMethod("EKS").invoke(firstMedia)),"Native sponsored predicate on real cached Media");
        Object firstRow=wrapper(loader,firstMedia),nextRow=wrapper(loader,nextMedia);
        Request request=new Request();Object first=page(loader,request.A0H,"smoke-next",firstRow);
        context.invoke(null,null,null,null,session,request,null);long start=android.os.SystemClock.elapsedRealtime();
        response.invoke(null,first,parser);
        check(android.os.SystemClock.elapsedRealtime()-start<1000,"Actual-model cold parse does not block UI");
        check(((List<?>)first.getClass().getField("A0S").get(first)).contains(firstRow),"Cold Friends head keeps real native Media and wrapper");
        check((Boolean)first.getClass().getField("A0a").get(first),"Cold native cursor remains available");
        request=new Request();request.A0H="smoke-next";request.A0G="smoke-next";
        Object tail=page(loader,request.A0H,null,firstRow,nextRow);
        context.invoke(null,null,null,null,session,request,null);response.invoke(null,tail,parser);
        List<?> tailRows=(List<?>)tail.getClass().getField("A0S").get(tail);
        check(!tailRows.contains(firstRow) && tailRows.contains(nextRow),"Real wrapper duplicates removed across pages");
        check(!(Boolean)tail.getClass().getField("A0a").get(tail),"Real EOF terminates native feed");
        request=new Request();Object cached=page(loader,request.A0H,"unused",firstRow);
        context.invoke(null,null,null,null,session,request,null);response.invoke(null,cached,parser);
        List<?> cachedRows=(List<?>)cached.getClass().getField("A0S").get(cached);
        check(cachedRows.contains(firstRow) && cachedRows.contains(nextRow),"Complete cache replays real Media and wrapper models");
        select.invoke(null,"RECENTS");
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
                feedModels(loader);
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
            result.putString("stream", "CALMA_NATIVE_MODELS_PASSED: allocation, native row, light/dark drawing, accessibility, recycling, four stock selector models, remaining time, zero-credit lock, real Media/dictionary/user/wrapper/response/parser models, cold head, native continuation, duplicate removal, completed cache\n");
            finish(-1,result);
        } else {
            result.putString("stream", "CALMA_NATIVE_MODELS_FAILED: " + android.util.Log.getStackTraceString(error[0]));
            finish(0,result);
        }
    }
}
