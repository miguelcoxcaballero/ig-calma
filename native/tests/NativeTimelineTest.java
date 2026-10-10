package es.calma.instagram.nativeapp;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import es.calma.instagram.nativeapp.NativeFeedTest.*;

public class NativeTimelineTest {
    public static final class TimelineRequest { public Object A09 = "cold_start_fetch"; }
    public static final class IdentifiedUser {
        public UserDictionary A00 = new UserDictionary(true, false);
        private final String id;
        IdentifiedUser(String id) { this.id=id; }
        public String getId(){return id;}
    }
    public static final class Dictionary {
        final String id; final long timestamp; final Object user; public String kind="feed";
        Dictionary(String id,long timestamp,Object user){this.id=id;this.timestamp=timestamp;this.user=user;}
        public String getId(){return id;} public Long A6X(){return timestamp;}
        public String A7W(){return kind;} public Object A33(){return user;}
        public List<Object> A8F(){return Collections.emptyList();}
    }
    public static final class Item {
        public Dictionary A04;
        Item(String id,long time,Object user){A04=new Dictionary(id,time,user);}
        public boolean EKS(){return false;}
    }
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static Response page(String cursor,Object...items){Response r=new Response();r.A0S=new ArrayList<>();for(Object item:items)r.A0S.add(item instanceof Wrapper?item:new Wrapper(item));r.A0N=cursor;r.A0a=cursor!=null;return r;}
    static Media post(String id,long time){return new Media(id,time,"feed",new User(true,true));}
    static NativeTimeline.Context context(String owner,TimelineRequest request){return new NativeTimeline.Context(null,new Session(owner),request,null);}
    static void verify(Session session,String id,boolean mutual){Status status=new Status(true,mutual);NativeRelations.beginStatus(session,id,status); NativeRelations.statusField(status,"following"); NativeRelations.endStatus(status);}
    static <T> T worker(Callable<T> action)throws Exception{ExecutorService e=Executors.newSingleThreadExecutor();try{return e.submit(action).get(8,TimeUnit.SECONDS);}finally{e.shutdownNow();}}
    static void fails(Callable<?> action,String message)throws Exception{try{action.call();throw new AssertionError(message);}catch(IllegalStateException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        CalmaConfig.testMode=2;long now=System.currentTimeMillis()/1000;
        TimelineRequest request=new TimelineRequest();
        NativeTimeline.Context c=context("9001",request);
        Wrapper stories=new Wrapper(null);Media one=post("one",now-20),two=post("two",now-10),old=post("old",now-190000);
        AtomicInteger fetched=new AtomicInteger();
        NativeTimeline.Snapshot snapshot=worker(()->NativeTimeline.load(page("a",stories,one),c,now,(ctx,cursor,deadline)->{fetched.incrementAndGet();return page(null,one,two);}));
        check(fetched.get()==1,"preloads remaining pages before completing");
        check(snapshot.wrappers.size()==3 && snapshot.wrappers.get(0)==stories,"stories retained once; duplicate post removed globally");
        check(((Wrapper)snapshot.wrappers.get(1)).A0A()==two,"late newer post is globally sorted");
        Response delivered=new Response();snapshot.apply(delivered,c.session);
        check(!delivered.A0a && !delivered.A0W && delivered.A0N==null,"complete snapshot never requests more pages on scroll");
        delivered.A0S.clear();check(snapshot.wrappers.size()==3,"native adapter cannot mutate saved list");
        NativeTimeline.Snapshot stable=worker(()->NativeTimeline.load(page(null),c,now+20,(a,b,d)->{throw new AssertionError("Unexpected fetch");}));
        check(stable==snapshot,"warm opening retains same snapshot and time window");
        request.A09="pull_to_refresh";
        NativeTimeline.Snapshot refreshed=worker(()->NativeTimeline.load(page(null,two),c,now+20,(a,b,d)->null));
        check(refreshed!=snapshot && refreshed.wrappers.size()==1,"explicit refresh replaces snapshot atomically");
        NativeTimeline.Context other=context("9002",new TimelineRequest());
        NativeTimeline.Snapshot empty=worker(()->NativeTimeline.load(page("empty"),other,now,(a,b,d)->page(null,two)));
        check(empty.wrappers.size()==1,"empty page is not EOF and does not hide later friends");
        NativeTimeline.Context cyc=context("9003",new TimelineRequest());
        worker(()->{fails(()->NativeTimeline.load(page("same"),cyc,now,(a,b,d)->page("same")),"repeated cursor must fail");return null;});
        check(NativeTimeline.saved(cyc.session)==null,"failed cycle never commits an empty completed timeline");
        NativeTimeline.Context bounded=context("9004",new TimelineRequest());
        NativeTimeline.Snapshot cutoff=worker(()->NativeTimeline.load(page("next",one),bounded,now,(a,b,d)->page("unneeded",old)));
        check(cutoff.wrappers.size()==1,"complete old page closes 48-hour interval");
        NativeTimeline.Context reversed=context("9005",new TimelineRequest());AtomicInteger requests=new AtomicInteger();
        NativeTimeline.Snapshot disorder=worker(()->NativeTimeline.load(page("p1",old,post("less-old",now-180000)),reversed,now,(a,b,d)->{requests.incrementAndGet();return page(null,two);}));
        check(requests.get()==1 && disorder.wrappers.size()==1,"out-of-order old page does not falsely truncate timeline");
        NativeTimeline.Context late=context("9006",new TimelineRequest());String author="71234";
        java.lang.reflect.Field activeField=NativeRelationLookup.class.getDeclaredField("ACTIVE");activeField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,CompletableFuture<Void>> active=(Map<String,CompletableFuture<Void>>)activeField.get(null);
        CompletableFuture<Void> pending=new CompletableFuture<>();active.put("9006:"+author,pending);
        ScheduledExecutorService delayed=Executors.newSingleThreadScheduledExecutor();
        delayed.schedule(()->{verify((Session)late.session,author,true);pending.complete(null);},180,TimeUnit.MILLISECONDS);
        NativeTimeline.Snapshot delayedSnapshot=worker(()->NativeTimeline.load(page(null,new Item("delayed",now-1,new IdentifiedUser(author))),late,now,(a,b,d)->null));
        delayed.shutdownNow();active.remove("9006:"+author);
        check(delayedSnapshot.wrappers.size()==1,"pending friendship resolves before filtering instead of losing post");
        check(NativeRelations.permitted(late.session,new IdentifiedUser(author),2,true),"fresh verified mutual overrides stale native false");
        check(!NativeRelations.permitted(other.session,new IdentifiedUser(author),2,true),"verified friendship never crosses accounts");
        request.A09="pull_to_refresh";
        worker(()->{fails(()->NativeTimeline.load(page("broken"),c,now+30,(a,b,d)->{throw new IllegalStateException("offline");}),"network failure must not complete");return null;});
        check(NativeTimeline.saved(c.session)==refreshed,"failed refresh keeps previous complete snapshot");
        NativeTimeline.Context busy=context("9007",new TimelineRequest());CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService loading=Executors.newSingleThreadExecutor();Future<?> work=loading.submit(()->{try{NativeTimeline.load(page("wait"),busy,now,(a,b,d)->{entered.countDown();release.await();return page(null);});}catch(Exception e){throw new RuntimeException(e);}});
        entered.await(2,TimeUnit.SECONDS);long start=System.nanoTime();
        fails(()->NativeTimeline.load(page(null),busy,now,(a,b,d)->null),"main thread must not wait on synchronization lock");
        check(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(100),"main thread is never held by another loading request");release.countDown();work.get();loading.shutdownNow();
        CalmaConfig.epoch++;
        check(NativeTimeline.saved(c.session)==null,"changed feed settings invalidate prior snapshot");
        CalmaConfig.testMode=1;
        NativeTimeline.Context following=context("9010",new TimelineRequest());
        NativeFeedTest.Media oneWay=new NativeFeedTest.Media("one-way",now-5,"feed",new NativeFeedTest.User(true,false));
        NativeTimeline.Snapshot all=worker(()->NativeTimeline.load(page(null,oneWay),following,now,(a,b,d)->null));
        check(all.wrappers.stream().anyMatch(row -> ((NativeFeedTest.Wrapper) row).A0A() == oneWay),"Following includes non-mutual followed accounts");
        CalmaConfig.testMode=2;
        check(NativeTimeline.saved(following.session)==null,"Friends never reuses the Following snapshot");
        NativeTimeline.Context friends=context("9010",new TimelineRequest());
        NativeTimeline.Snapshot mutualOnly=worker(()->NativeTimeline.load(page(null,oneWay),friends,now,(a,b,d)->null));
        check(mutualOnly.wrappers.isEmpty(),"Friends excludes non-mutual followed accounts");
        CalmaConfig.testMode=1;check(NativeTimeline.saved(following.session)==all,"Returning to Following reuses its complete timeline");
        System.out.println("Native timeline: "+checks+" assertions passed");
    }
}
