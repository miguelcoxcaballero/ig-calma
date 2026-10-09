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
        Class<?> type=target.getClass();
        while(type!=null){try{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;}catch(NoSuchFieldException missing){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
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
        Object row=allocate(loader,"X.05qw");set(row,"A0p",media);set(row,"A0s",media);set(row,"A15",media.getClass().getMethod("getId").invoke(media));set(row,"A0q",media.getClass().getMethod("C3g").invoke(media));return row;
    }
    private Object page(ClassLoader loader,String request,String cursor,Object... rows)throws Exception {
        Object response=allocate(loader,"X.07do");
        set(response,"A0S",new ArrayList<>(Arrays.asList(rows)));set(response,"A0P",request);
        set(response,"A0O","following");set(response,"A0N",cursor);set(response,"A0a",cursor!=null);set(response,"A0W",cursor!=null);
        return response;
    }
    private Object request(ClassLoader loader,String id,String cursor)throws Exception {
        Object request=allocate(loader,"X.02pp");set(request,"A0H",id);set(request,"A0G",cursor);
        set(request,"A09",Class.forName("X.02pk",true,loader).getField(cursor==null?"A0J":"A0R").get(null));
        set(request,"A0L",Collections.singletonMap("pagination_source","homecoming_all"));return request;
    }
    private Object deliver(ClassLoader loader,Object controller,Object request,Object page)throws Exception {
        Object envelope=allocate(loader,"X.06lZ");set(envelope,"A00",request);
        Object result=allocate(loader,"X.04ss");set(result,"A03",page);set(result,"A02",Collections.emptyList());
        Class.forName("es.calma.instagram.nativeapp.NativeFeed",true,loader).getMethod("delivered",Object.class,Object.class,Object.class).invoke(null,controller,envelope,result);
        check(!((List<?>)result.getClass().getMethod("A01").invoke(result)).isEmpty(),"Stock delivery list is updated, not only its parser model");
        return result;
    }
    private void feedModels(ClassLoader loader)throws Exception {
        Class<?> config=Class.forName("es.calma.instagram.nativeapp.CalmaConfig",true,loader);
        Method select=config.getDeclaredMethod("select",String.class);select.setAccessible(true);select.invoke(null,"RECENTS");
        Object session=allocate(loader,"com.instagram.common.session.UserSession");set(session,"userId","994400");
        Object controller=allocate(loader,"X.05qX");set(controller,"A0X",session);
        Object parser=allocate(loader,"X.03gD");set(parser,"A01",session);
        Class<?> feed=Class.forName("es.calma.instagram.nativeapp.NativeFeed",true,loader);
        Method context=feed.getMethod("context",Object.class,Object.class,Object.class,Object.class,Object.class,Object.class);
        Method response=feed.getMethod("response",Object.class,Object.class);
        Object firstMedia=media(loader,"99440001",System.currentTimeMillis()/1000-10,true);
        Object nextMedia=media(loader,"99440002",System.currentTimeMillis()/1000-20,true);
        check(Boolean.FALSE.equals(firstMedia.getClass().getMethod("EKS").invoke(firstMedia)),"Native sponsored predicate on real cached Media");
        Object firstRow=wrapper(loader,firstMedia),nextRow=wrapper(loader,nextMedia);
        Object request=request(loader,"smoke-head",null),first=page(loader,"server-generated-id","smoke-next",firstRow);
        Object builder=allocate(loader,"X.01pg");Object parameters=Class.forName("X.02pv",true,loader).getConstructor().newInstance();set(builder,"A0a",parameters);
        context.invoke(null,null,builder,null,session,request,null);
        builder.getClass().getMethod("AOA",String.class,String.class).invoke(builder,"pagination_source","homecoming_all");
        feed.getMethod("wire",Object.class).invoke(null,builder);
        Map<?,?> actual=(Map<?,?>)parameters.getClass().getField("A00").get(parameters);
        check("following".equals(actual.get("pagination_source").getClass().getField("A00").get(actual.get("pagination_source"))),"Final original HTTP parameter map overwrites experimental source");
        check("FOLLOWING".equals(actual.get("feed_type").getClass().getField("A00").get(actual.get("feed_type"))),"Original HTTP map sends FOLLOWING for Friends");
        long start=android.os.SystemClock.elapsedRealtime();response.invoke(null,first,parser);deliver(loader,controller,request,first);
        check(android.os.SystemClock.elapsedRealtime()-start<1000,"Actual-model cold delivery does not block UI");
        check(((List<?>)first.getClass().getField("A0S").get(first)).contains(firstRow),"Cold Friends delivers real native Media despite a different response ID");
        check((Boolean)first.getClass().getField("A0a").get(first),"Cold native cursor remains available");
        request=request(loader,"smoke-next","smoke-next");Object tail=page(loader,null,null,firstRow,nextRow);
        context.invoke(null,null,null,null,session,request,null);response.invoke(null,tail,parser);deliver(loader,controller,request,tail);
        List<?> tailRows=(List<?>)tail.getClass().getField("A0S").get(tail);
        check(!tailRows.contains(firstRow) && tailRows.contains(nextRow),"Real wrapper duplicates removed across pages without echoed request ID");
        check(!(Boolean)tail.getClass().getField("A0a").get(tail),"Real EOF terminates native feed");
        request=request(loader,"smoke-cached",null);Object cached=page(loader,null,"unused",firstRow);
        context.invoke(null,null,null,null,session,request,null);response.invoke(null,cached,parser);deliver(loader,controller,request,cached);
        List<?> cachedRows=(List<?>)cached.getClass().getField("A0S").get(cached);
        check(cachedRows.contains(firstRow) && cachedRows.contains(nextRow),"Complete cache replays real Media and wrapper models");
        select.invoke(null,"FOLLOWING");
        Object stale=media(loader,"99440003",System.currentTimeMillis()/1000-5,false);
        Object user=stale.getClass().getField("A04").get(stale);user=user.getClass().getMethod("A33").invoke(user);
        Object dict=user.getClass().getField("A00").get(user),data=dict.getClass().getField("A00").get(dict);
        for(Object status:Class.forName("X.02tG",true,loader).getEnumConstants())if("FollowStatusNotFollowing".equals(status.toString()))set(data,"A05",status);
        request=request(loader,"stale-status",null);Object staleRow=wrapper(loader,stale),fresh=page(loader,null,"cursor",staleRow);
        context.invoke(null,null,null,null,session,request,null);response.invoke(null,fresh,parser);deliver(loader,controller,request,fresh);
        check(((List<?>)fresh.getClass().getField("A0S").get(fresh)).contains(staleRow),"Following survives actual stale NotFollowing enum in original User model");
        select.invoke(null,"RECENTS");
    }
    private Object getStatic(Class<?> type,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private void setStatic(Class<?> type,String name,Object value)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private void reelModels(ClassLoader loader,Context context)throws Exception {
        Class<?> policy=Class.forName("es.calma.instagram.nativeapp.CalmaReels",true,loader);
        Class<?> configType=Class.forName("es.calma.instagram.nativeapp.CalmaConfig",true,loader);
        Method select=configType.getDeclaredMethod("select",String.class);select.setAccessible(true);select.invoke(null,"RECENTS");
        Class<?> budget=Class.forName("es.calma.instagram.nativeapp.NativeFeedBudget",true,loader);
        Object oldCredits=getStatic(budget,"credits"),oldChain=getStatic(budget,"reelChain"),oldLaunch=getStatic(budget,"launchTime");
        Object session=allocate(loader,"com.instagram.common.session.UserSession");set(session,"userId","994490");
        Object config=allocate(loader,"com.instagram.clips.intf.ClipsViewerConfig");set(config,"A1k","99449001");set(config,"A27",true);set(config,"A28",true);set(config,"A2o",true);
        Class<?> listType=config.getClass().getField("A0G").getType();Object originalIds=listType.getMethod("of",Object.class).invoke(null,"99449001");set(config,"A0G",originalIds);
        Method allow=policy.getMethod("allowLaunch",Object.class,Object.class),pin=policy.getMethod("lockPager",Object.class),move=policy.getMethod("maySelect",Object.class,int.class);
        check(Boolean.TRUE.equals(policy.getMethod("allowPrefetch",Object.class,Object.class).invoke(null,config,session)) && !(Boolean)config.getClass().getField("A2u").get(config),"Original viewer config is not changed by prefetch");
        check(Boolean.TRUE.equals(allow.invoke(null,config,session)),"Original feed Reel config opens without a DM or friendship lookup");
        check((Boolean)config.getClass().getField("A2u").get(config) && ((List<?>)config.getClass().getField("A0G").get(config)).size()==1,"Original config disables tail and pins one source");
        Object controller=allocate(loader,"X.019Z");set(controller,"A0P",config);
        Object pager=Class.forName("androidx.viewpager2.widget.ViewPager2",true,loader).getConstructor(Context.class).newInstance(context);set(controller,"A0A",pager);
        pager.getClass().getMethod("setUserInputEnabled",boolean.class).invoke(pager,true);pin.invoke(null,controller);
        check(Boolean.FALSE.equals(pager.getClass().getField("A0A").get(pager)),"Actual ViewPager2 user gestures are disabled for an opened feed Reel");
        check(Boolean.FALSE.equals(move.invoke(null,controller,1)),"Feed Reel cannot advance programmatically");
        controller.getClass().getMethod("A03",controller.getClass(),int.class,boolean.class).invoke(null,controller,1,false);
        check((Integer)pager.getClass().getMethod("getCurrentItem").invoke(pager)==0,"Patched native setCurrentItem rejects the next item before entering the stock body");
        try {
            long now=System.currentTimeMillis();Class<?> creditsType=Class.forName("es.calma.instagram.nativeapp.HourlyCredits",true,loader);
            Constructor<?> ctor=creditsType.getDeclaredConstructor(long.class,int.class);ctor.setAccessible(true);setStatic(budget,"credits",ctor.newInstance(now-3600000L,1));
            select.invoke(null,"BLENDED_FOR_YOU");setStatic(budget,"reelChain",true);setStatic(budget,"launchTime",android.os.SystemClock.elapsedRealtime());
            check(Boolean.TRUE.equals(budget.getMethod("reelsAllowed").invoke(null)),"Real earned-time policy opens the paid Reel chain");
            check(Boolean.TRUE.equals(allow.invoke(null,config,session)) && !(Boolean)config.getClass().getField("A2u").get(config) && (Boolean)config.getClass().getField("A27").get(config),"Earned time restores actual original config flags");
            check(Boolean.TRUE.equals(move.invoke(null,controller,1)),"Paid For you permits the next item with blocking enabled");
            Object direct=allocate(loader,"com.instagram.clips.intf.ClipsViewerDirectData");set(config,"A0N",direct);
            check(Boolean.TRUE.equals(allow.invoke(null,config,session)) && Boolean.FALSE.equals(move.invoke(null,controller,1)),"Original DM config remains single-clip independently of For you balance");
            set(config,"A0N",null);setStatic(budget,"credits",ctor.newInstance(now,0));
            check(Boolean.FALSE.equals(move.invoke(null,controller,1)),"Real exhausted credits lock the viewer again");
        } finally {
            setStatic(budget,"credits",oldCredits);setStatic(budget,"reelChain",oldChain);setStatic(budget,"launchTime",oldLaunch);select.invoke(null,"RECENTS");
        }
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
                reelModels(loader,context);
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
            result.putString("stream", "CALMA_NATIVE_MODELS_PASSED: allocation, native row, light/dark drawing, accessibility, recycling, four stock selector models, remaining time, zero-credit lock, real Media/dictionary/user/wrapper/response/parser models; actual HTTP parameter map and delivery envelope with absent/different response IDs, cold head, native continuation, duplicate removal, completed cache; actual ClipsViewerConfig/ViewPager2 single-clip lock and earned-time restore\n");
            finish(-1,result);
        } else {
            result.putString("stream", "CALMA_NATIVE_MODELS_FAILED: " + android.util.Log.getStackTraceString(error[0]));
            finish(0,result);
        }
    }
}
