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
        final String id; final long timestamp; final Object user;
        Dictionary(String id,long timestamp,Object user){this.id=id;this.timestamp=timestamp;this.user=user;}
        public String getId(){return id;} public Long A6X(){return timestamp;}
        public String A7W(){return "feed";} public Object A33(){return user;}
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
    static void verify(Session session,String id,boolean mutual){Status status=new Status(true,mutual);NativeRelations.beginStatus(session,id,status);NativeRelations.endStatus(status);}
    static <T> T worker(Callable<T> action)throws Exception{ExecutorService e=Executors.newSingleThreadExecutor();try{return e.submit(action).get(8,TimeUnit.SECONDS);}finally{e.shutdownNow();}}
    static void fails(Callable<?> action,String message)throws Exception{try{action.call();throw new AssertionError(message);}catch(IllegalStateException expected){checks++;}}
    static void warm(NativeTimeline.Context context)throws Exception {
        java.lang.reflect.Field field=NativeTimeline.class.getDeclaredField("STREAMS");field.setAccessible(true);
        Object stream=((Map<?,?>)field.get(null)).get(NativeRelations.owner(context.session)+':'+context.mode);
        java.lang.reflect.Field future=stream.getClass().getDeclaredField("complete");future.setAccessible(true);
        ((CompletableFuture<?>)future.get(stream)).get(4,TimeUnit.SECONDS);
    }
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
        Response delivered=new Response();snapshot.apply(delivered);
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
        NativeTimeline.Context fast=context("9020",new TimelineRequest());
        CountDownLatch fetching=new CountDownLatch(1),allowNext=new CountDownLatch(1);
        Response head=page("next",stories,one);
        long coldStart=System.nanoTime();
        check(NativeTimeline.begin(head,fast,now,(a,b,d)->{fetching.countDown();allowNext.await();return page(null,one,two);})==null,"cold head does not await complete timeline");
        check(System.nanoTime()-coldStart<TimeUnit.MILLISECONDS.toNanos(200),"first page returns while network preloading is blocked");
        check(fetching.await(2,TimeUnit.SECONDS),"remaining pages are prefetched without scrolling");
        NativeTimeline.delivered(head,fast,true);
        check(head.A0S.size()==2 && head.A0S.get(0)==stories,"stories stay visible in immediate head");
        Response beforeReady=page("native-next",two);
        check(!NativeTimeline.remaining(beforeReady,fast) && beforeReady.A0a,"unfinished preload never blocks or ends native pagination");
        head.A0S.clear();allowNext.countDown();warm(fast);
        NativeTimeline.Snapshot complete=NativeTimeline.saved(fast.session);
        check(complete.wrappers.size()==3,"adapter mutations cannot truncate background seed");
        Response tail=page("ignored",one,two);
        check(NativeTimeline.remaining(tail,fast),"ready preload is consumed by next native response");
        check(tail.A0S.size()==1 && ((Wrapper)tail.A0S.get(0)).A0A()==two,"cached tail excludes posts and stories already delivered");
        check(!tail.A0a && tail.A0N==null,"complete cached tail ends pagination");
        Response duplicate=page("duplicate",two);
        NativeTimeline.delivered(duplicate,fast,false);
        check(duplicate.A0S.isEmpty(),"overlapping native pages do not repeat posts");
        check(NativeTimeline.begin(page(null),fast,now,(a,b,d)->{throw new AssertionError("cached feed fetched");})==complete,"warm reopen immediately returns complete snapshot");
        Wrapper currentStories=new Wrapper(null);Response reopened=page(null,currentStories);
        complete.applyHead(reopened,fast.session);
        check(reopened.A0S.get(0)==currentStories && !reopened.A0S.contains(stories),"cached posts preserve current stories rather than stale head controls");
        NativeTimeline.Context continued=context("9021",new TimelineRequest());
        CountDownLatch later=new CountDownLatch(1);
        Response early=page("next",one);
        NativeTimeline.begin(early,continued,now,(a,b,d)->{later.await();return page(null,one,two,post("three",now-30));});
        NativeTimeline.delivered(early,continued,true);
        Response middle=page("next2",one,two);
        NativeTimeline.delivered(middle,continued,false);
        check(middle.A0S.size()==1,"normal pagination remains usable during preloading without duplicate rows");
        later.countDown();warm(continued);
        Response finalTail=page(null);
        NativeTimeline.remaining(finalTail,continued);
        check(finalTail.A0S.size()==1 && ((Media)((Wrapper)finalTail.A0S.get(0)).A0A()).A04.getId().equals("three"),"completion excludes all pages delivered during preloading");
        TimelineRequest explicit=new TimelineRequest();explicit.A09="pull_to_refresh";
        NativeTimeline.Context reloading=context("9021",explicit);
        check(NativeTimeline.begin(page(null,two),reloading,now+1,(a,b,d)->null)==null,"explicit refresh starts a new progressive generation");
        warm(reloading);check(NativeTimeline.saved(reloading.session).wrappers.size()==1,"refresh replaces previous full snapshot");
        NativeTimeline.Context failed=context("9022",new TimelineRequest());
        NativeTimeline.begin(page("offline",one),failed,now,(a,b,d)->{throw new IllegalStateException("offline");});
        try {warm(failed);} catch (ExecutionException expected) {}
        Response retry=page("real-cursor",two);
        check(!NativeTimeline.remaining(retry,failed) && retry.A0a,"failed optional preload leaves original pagination enabled");
        CalmaConfig.testMode=2;
        IdentifiedUser confirmed=new IdentifiedUser("70001");confirmed.A00=new UserDictionary(true,true);
        check(NativeFeed.missing(Collections.emptyList(),Arrays.asList(new Item("confirmed",now,confirmed)),new Session("9030"),now).isEmpty(),"positive native mutual friendship avoids redundant network verification");
        check(NativeFeed.missing(Collections.emptyList(),Arrays.asList(new Item("uncertain",now,new IdentifiedUser("70002"))),new Session("9030"),now).contains("70002"),"unverified negative friendship is still checked before hiding posts");
        NativeTimeline.Context switched=context("9031",new TimelineRequest());
        NativeTimeline.begin(page("old-mode",one),switched,now,(a,b,d)->{CalmaConfig.epoch++;return page(null,two);});
        try {warm(switched);throw new AssertionError("obsolete preload succeeded");} catch(ExecutionException expected){checks++;}
        check(NativeTimeline.saved(switched.session)==null && !NativeTimeline.remaining(page(null),switched),"obsolete generation neither replaces cache nor leaks into current feed");
        System.out.println("Native timeline: "+checks+" assertions passed");
    }
}
