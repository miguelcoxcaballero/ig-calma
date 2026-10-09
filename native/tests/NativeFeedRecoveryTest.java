package es.calma.instagram.nativeapp;

import java.util.*;
import java.util.concurrent.*;
import es.calma.instagram.nativeapp.NativeFeedTest.*;

/** Cold response integration: no pre-populated complete cache and no second transport. */
public final class NativeFeedRecoveryTest {
    public static final class Request {
        public String A0H="head", A0G;
        public Object A09="cold_start_fetch";
        public Map<String,String> A0L=Collections.singletonMap("pagination_source","following");
    }
    public static final class Controller { public Object A0X; Controller(Object session){A0X=session;} }
    public static final class Envelope { public Object A00; Envelope(Object request){A00=request;} }
    public static final class Result { public Object A03; public List<?> A02; Result(Object response){A03=response;} public void A02(List<?> rows){A02=rows;} }
    public static final class Builder { public Map<String,String> values=new HashMap<>(); public void AOA(String key,String value){values.put(key,value);} }
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static Response deliver(Session session,Request request,Response response) {
        response.A0P=request.A0H;Parser parser=new Parser();parser.A01=session;
        NativeFeed.context(null,null,null,session,request,null);NativeFeed.response(response,parser);NativeFeed.delivered(new Controller(session),new Envelope(request),new Result(response));return response;
    }
    public static void main(String[] args) throws Exception {
        long now=System.currentTimeMillis()/1000;
        Media newest=new Media("new",now-10,"feed",new User(true,true));
        Media older=new Media("older",now-20,"feed",new User(true,true));
        Media oneWay=new Media("one-way",now-30,"feed",new User(true,false));
        Media ad=new Media("ad",now-5,"feed",new User(true,true));ad.sponsored=true;
        CalmaConfig.testMode=1;
        Session wireAccount=new Session("9400"); Request wireRequest=new Request();
        wireRequest.A0L=Collections.singletonMap("pagination_source","homecoming_all");
        Builder builder=new Builder(); NativeFeed.context(null,builder,null,wireAccount,wireRequest,null);
        builder.AOA("pagination_source","feed_recs"); builder.AOA("feed_type","RECENTS");
        CalmaConfig.testMode=0; NativeFeed.wire(builder);
        check("following".equals(builder.values.get("pagination_source")),"final wire uses captured request mode despite later experiment writes or selection changes");
        check("FOLLOWING".equals(builder.values.get("feed_type")),"Friends and Following use the actual native Following protocol");
        Builder other=new Builder();other.AOA("pagination_source","discover");NativeFeed.wire(other);
        check("discover".equals(other.values.get("pagination_source")),"unrelated requests remain untouched");
        CalmaConfig.testMode=1;
        Media staleUser=new Media("stale-native-user",now-10,"feed",new User(false,false));
        Response noEcho=NativeTimelineTest.page("wire-tail",staleUser);noEcho.A0P="server-generated-id";noEcho.A0O="homecoming_all";
        Parser wireParser=new Parser();wireParser.A01=wireAccount;
        NativeFeed.response(noEcho,wireParser);
        Result wireResult=new Result(noEcho);wireResult.A02=Collections.emptyList();
        NativeFeed.delivered(new Controller(wireAccount),new Envelope(new Request()),wireResult);
        check(wireResult.A02.size()==1,"real delivery request restores post rejected by stale model even when server id/source differ");
        check("wire-tail".equals(NativeTimelineProgress.next(wireAccount)),"unmatched response still advances native pagination");
        for(int mode:new int[]{2,1}) {
            CalmaConfig.testMode=mode;Session account=new Session("94"+mode);
            Request request=new Request();Wrapper stories=new Wrapper(null);
            long start=System.nanoTime();Response first=deliver(account,request,NativeTimelineTest.page("tail",stories,newest,ad));
            check(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(300),"cold head delivers without waiting for all pages or friendship HTTP");
            check(first.A0S.size()==2 && first.A0S.get(0)==stories,"stories and first post appear on cold main-thread parse");
            check(first.A0a && "tail".equals(first.A0N),"stock continuation is preserved");
            check("tail".equals(NativeTimelineProgress.next(account)),"preload uses original controller cursor");
            request=new Request();request.A0H="tail";request.A0G="tail";
            Response last=deliver(account,request,NativeTimelineTest.page(null,newest,older,oneWay));
            check(last.A0S.size()==(mode==2?1:2),"later pages emit only new permitted rows");
            check(!last.A0a && last.A0N==null,"real EOF stops native pagination");
            Response cached=deliver(account,new Request(),NativeTimelineTest.page("unused",newest));
            check(cached.A0S.size()==(mode==2?3:4),"completed account cache contains all posts and stories exactly once");
            check(!cached.A0a && cached.A0N==null,"cached feed never starts another pagination chain");
            ((List<?>)cached.A0S).clear();
            Response again=deliver(account,new Request(),NativeTimelineTest.page("unused",newest));
            check(again.A0S.size()==(mode==2?3:4),"native adapter cannot mutate saved snapshot");
        }
        CalmaConfig.testMode=2;Session account=new Session("9500");
        NativeTimelineTest.IdentifiedUser unresolved=new NativeTimelineTest.IdentifiedUser("888");
        NativeTimelineTest.Item pending=new NativeTimelineTest.Item("pending",now-15,unresolved);
        java.lang.reflect.Field activeField=NativeRelationLookup.class.getDeclaredField("ACTIVE");activeField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,CompletableFuture<Void>> active=(Map<String,CompletableFuture<Void>>)activeField.get(null);
        Session overlapAccount=new Session("9510");
        CompletableFuture<Void> authorA=new CompletableFuture<>(),authorB=new CompletableFuture<>();
        active.put("9510:800",authorA);active.put("9510:801",authorB);
        CompletableFuture<Void> overlap=NativeRelationLookup.refresh(overlapAccount,Arrays.asList("800","801","800"));
        CompletableFuture<Void> repeated=NativeRelationLookup.refresh(overlapAccount,Collections.singletonList("800"));
        check(!overlap.isDone() && !repeated.isDone(),"overlapping pages reuse in-flight author lookups instead of starting duplicate native requests");
        authorA.complete(null);
        check(repeated.isDone() && !repeated.isCompletedExceptionally() && !overlap.isDone(),"one page can finish while the other author is pending");
        authorB.complete(null);check(overlap.isDone() && !overlap.isCompletedExceptionally(),"shared author completion releases overlapping pages");
        active.remove("9510:800");active.remove("9510:801");
        CompletableFuture<Void> blocked=new CompletableFuture<>();active.put("9500:888",blocked);
        Response first=deliver(account,new Request(),NativeTimelineTest.page("next",newest,pending));
        check(first.A0S.size()==1,"unknown author does not erase a known friend");
        check(!blocked.isDone() && first.A0a,"blocked metadata does not block parser or truncate feed");
        NativeTimelineTest.verify(account,"888",true);blocked.complete(null);active.remove("9500:888");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(!NativeTimelineProgress.replay(account) && System.nanoTime()<deadline)Thread.sleep(5);
        List<?> immediate=NativeTimelineProgress.local(account);
        check(NativeTimelineProgress.replay(account) && immediate.size()==2,"resolved friend is ready for LOCAL delivery before any next page or EOF");
        NativeTimelineProgress.rendered(account);
        Request tail=new Request();tail.A0H="next";tail.A0G="next";
        Response recovered=deliver(account,tail,NativeTimelineTest.page(null,older));
        check(recovered.A0S.size()==1,"local delivery does not duplicate the resolved friend on the next page");
        check(((Wrapper)immediate.get(1)).A0A()==pending,"local friends remain chronologically ordered");
        Response finalCache=deliver(account,new Request(),NativeTimelineTest.page("unused",newest));
        check(finalCache.A0S.size()==3,"resolved friends retained in complete timeline");
        Session brokenAccount=new Session("9501");
        Response malformed=deliver(brokenAccount,new Request(),NativeTimelineTest.page("valid-cursor",newest,new Object()));
        check(malformed.A0S.size()==1 && malformed.A0a,"one malformed row preserves valid post and continuation");
        CalmaConfig.testMode=0;
        Media unknown=new Media("algorithm",now-300000,"clips",new User(false,false));
        for(String source:new String[]{"feed_recs","favorites"}) {
            Response nativePage=new Response();nativePage.A0O=source;nativePage.A0P=null;
            nativePage.A0S=Arrays.asList(new Wrapper(unknown),new Wrapper(ad),new Wrapper(newest));
            Parser parser=new Parser();parser.A01=account;NativeFeed.response(nativePage,parser);
            check(nativePage.A0S.size()==2 && ((Wrapper)nativePage.A0S.get(0)).A0A()==unknown,"stock ranking and Reels retained: "+source);
            check(nativePage.A0a && nativePage.A0N!=null,"native continuation remains available: "+source);
        }
        System.out.println("Feed recovery: "+checks+" cold response integration assertions passed");
    }
}
