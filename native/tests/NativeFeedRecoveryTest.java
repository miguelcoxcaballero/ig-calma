package es.calma.instagram.nativeapp;

import java.util.*;
import java.util.concurrent.*;
import es.calma.instagram.nativeapp.NativeFeedTest.*;

/** Exercise the actual response hook after a failed cache and across four selections. */
public final class NativeFeedRecoveryTest {
    public static final class Request {
        public String A0H="head", A0G;
        public Object A09="cold_start_fetch";
        public Map<String,String> A0L=Collections.singletonMap("pagination_source","following");
    }
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args) throws Exception {
        long now=System.currentTimeMillis()/1000;
        Session account=new Session("9400");Parser parser=new Parser();parser.A01=account;
        Media newest=new Media("new",now-10,"feed",new User(true,true));
        Media older=new Media("older",now-20,"feed",new User(true,true));
        Media oneWay=new Media("one-way",now-30,"feed",new User(true,false));
        Media ad=new Media("ad",now-5,"feed",new User(true,true));ad.sponsored=true;
        for(int mode:new int[]{2,1}) {
            CalmaConfig.testMode=mode;
            Request request=new Request();
            NativeTimeline.Context context=new NativeTimeline.Context(null,account,request,null);
            ExecutorService worker=Executors.newSingleThreadExecutor();
            try {
                NativeTimeline.Snapshot saved=worker.submit(()->NativeTimeline.load(NativeTimelineTest.page("tail",newest),context,now,
                        (a,b,d)->NativeTimelineTest.page(null,older,oneWay,ad))).get(4,TimeUnit.SECONDS);
                Response head=NativeTimelineTest.page("network-still-has-pages",newest);head.A0P="head";
                NativeFeed.context(null,null,null,account,request,null);
                NativeFeed.response(head,parser);
                check(head.A0S.size()==(mode==2?2:3),"actual response hook delivers entire cached mode "+mode);
                check(!head.A0a && head.A0N==null,"cached feed does not start a second pagination chain");
                check(((Wrapper)head.A0S.get(0)).A0A()==newest,"newest post remains first");
                check(NativeTimeline.saved(account)==saved,"response retains completed account cache");
            } finally {worker.shutdownNow();}
        }
        CalmaConfig.testMode=0;
        Media unknown=new Media("algorithm",now-300000,"clips",new User(false,false));
        for(String source:new String[]{"feed_recs","favorites"}) {
            Response nativePage=new Response();nativePage.A0O=source;nativePage.A0P=null;
            nativePage.A0S=Arrays.asList(new Wrapper(unknown),new Wrapper(ad),new Wrapper(newest));
            NativeFeed.response(nativePage,parser);
            check(nativePage.A0S.size()==2 && ((Wrapper)nativePage.A0S.get(0)).A0A()==unknown,"stock feed survives without timeline synchronization: "+source);
            check(nativePage.A0a && nativePage.A0N!=null,"native continuation remains available: "+source);
        }
        System.out.println("Feed recovery: "+checks+" response integration assertions passed");
    }
}
