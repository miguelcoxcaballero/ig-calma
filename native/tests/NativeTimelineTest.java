package es.calma.instagram.nativeapp;

import java.util.*;
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
    static Response deliver(NativeTimeline.Context context, boolean head, long now, Response response)throws Exception {
        NativeTimelineProgress.response(response,context,head,CalmaConfig.sessionId(),now);
        return response;
    }
    static boolean contains(Response response,Object media) {
        return response.A0S.stream().anyMatch(row->((Wrapper)row).A0A()==media);
    }
    public static void main(String[] args)throws Exception{
        CalmaConfig.testMode=1;long now=System.currentTimeMillis()/1000;
        TimelineRequest request=new TimelineRequest();
        NativeTimeline.Context c=context("9001",request);
        Wrapper stories=new Wrapper(null);Media one=post("one",now-20),two=post("two",now-10),old=post("old",now-190000);
        Response first=deliver(c,true,now,page("a",stories,one));
        check(first.A0S.size()==2 && first.A0S.get(0)==stories,"head stories and post appear immediately from the native page");
        check(first.A0a && "a".equals(first.A0N) && "a".equals(NativeTimelineProgress.next(c.session)),"native source cursor remains unchanged before more pages arrive");
        check(NativeTimelineProgress.saved(c.session)==null,"partial native pages are never advertised as a complete snapshot");
        Response last=deliver(c,false,now,page(null,one,two));
        check(last.A0S.size()==1 && contains(last,two),"later native page delivers only the new post");
        check(!last.A0a && !last.A0W && last.A0N==null,"real EOF closes native continuation");
        check(NativeTimelineProgress.saved(c.session)!=null,"observed native EOF publishes the complete snapshot");
        Response cached=deliver(c,true,now,page("unused",one));
        check(cached.A0S.size()==3 && cached.A0S.get(0)==stories,"completed cache retains stories once and deduplicates posts globally");
        check(((Wrapper)cached.A0S.get(1)).A0A()==two,"late newer post is globally sorted in the saved timeline");
        check(!cached.A0a && cached.A0N==null,"complete cached timeline does not ask for another native page");
        cached.A0S.clear();
        check(deliver(c,true,now,page(null)).A0S.size()==3,"native adapter cannot mutate saved rows");

        List<Object> sourceRows=new ArrayList<>(Arrays.asList(stories,new Wrapper(one)));
        List<Object> sourceMedia=new ArrayList<>(Arrays.asList(one));
        NativeTimeline.Snapshot snapshot=new NativeTimeline.Snapshot(sourceRows,sourceMedia,now);
        sourceRows.clear();sourceMedia.clear();
        check(snapshot.wrappers.size()==2 && snapshot.media.size()==1,"snapshot defensively copies both native representations");
        try {snapshot.wrappers.clear();throw new AssertionError("snapshot must be immutable");}
        catch(UnsupportedOperationException expected){checks++;}
        Response delivered=new Response();snapshot.apply(delivered,c.session);
        delivered.A0S.clear();delivered.A0U.clear();
        snapshot.apply(delivered,c.session);
        check(delivered.A0S.size()==2 && delivered.A0U.size()==1,"each snapshot application gives the native adapter independent lists");

        request.A09="pull_to_refresh";
        Response refreshed=deliver(c,true,now,page(null,two));
        check(refreshed.A0S.size()==1 && contains(refreshed,two),"explicit refresh replaces the collected timeline");
        request.A09="cold_start_fetch";
        check(deliver(c,true,now,page(null,one)).A0S.size()==1,"completed refreshed timeline remains the warm cache");
        NativeTimeline.Context other=context("9002",new TimelineRequest());
        Response empty=deliver(other,true,now,page("empty"));
        check(empty.A0S.isEmpty() && empty.A0a && "empty".equals(NativeTimelineProgress.next(other.session)),"empty native page is not EOF and keeps its source cursor");
        deliver(other,false,now,page(null,one));
        Response otherCache=deliver(other,true,now,page(null));
        check(contains(otherCache,one) && !contains(otherCache,two),"collected timeline never crosses accounts");

        NativeTimeline.Context cyc=context("9003",new TimelineRequest());
        deliver(cyc,true,now,page("same",one));
        Response repeated=deliver(cyc,false,now,page("same",two));
        check(NativeTimelineProgress.next(cyc.session)==null && repeated.A0a && "same".equals(repeated.A0N),"repeated source cursor does not invent a next request or a completed timeline");
        check(NativeTimelineProgress.saved(cyc.session)==null,"a repeated cursor cannot publish an incomplete timeline as complete");
        NativeTimeline.Context bounded=context("9004",new TimelineRequest());
        deliver(bounded,true,now,page("next",one));
        Response cutoff=deliver(bounded,false,now,page("unneeded",old));
        check(cutoff.A0S.isEmpty() && !cutoff.A0a && cutoff.A0N==null,"ordered old native page closes the 48-hour interval");
        check(deliver(bounded,true,now,page(null)).A0S.size()==1,"48-hour snapshot retains only the observed recent post");
        NativeTimeline.Context reversed=context("9005",new TimelineRequest());
        Response disorder=deliver(reversed,true,now,page("p1",old,post("less-old",now-180000)));
        check(disorder.A0a && "p1".equals(disorder.A0N),"out-of-order old native page does not falsely truncate timeline");
        Response later=deliver(reversed,false,now,page(null,two));
        check(later.A0S.size()==1 && contains(later,two),"a later native page can still reveal a recent post");

        CalmaConfig.testMode=2;
        NativeTimeline.Context late=context("9006",new TimelineRequest());String author="71234";
        Item pending=new Item("delayed",now-1,new IdentifiedUser(author));
        Response unresolved=deliver(late,true,now,page("native-next",pending));
        check(unresolved.A0S.isEmpty() && unresolved.A0a,"unknown friend is held locally without completing the native timeline");
        verify((Session)late.session,author,true);
        check(NativeTimelineProgress.local(late.session).size()==1,"naturally parsed relationship proof unlocks an already received post");
        check(NativeRelations.permitted(late.session,new IdentifiedUser(author),2,true),"fresh native mutual proof overrides stale native false");
        check(!NativeRelations.permitted(other.session,new IdentifiedUser(author),2,true),"verified friendship never crosses accounts");
        CalmaConfig.epoch++;
        check(NativeTimelineProgress.local(late.session).isEmpty() && NativeTimelineProgress.next(late.session)==null,"changed feed settings invalidate the prior generation");
        CalmaConfig.testMode=1;
        check(NativeTimelineProgress.saved(c.session)==null,"settings changes also invalidate a completed saved snapshot");

        NativeTimeline.Context following=context("9010",new TimelineRequest());
        Media oneWay=new Media("one-way",now-5,"feed",new User(true,false));
        check(contains(deliver(following,true,now,page(null,oneWay)),oneWay),"Following includes non-mutual followed accounts");
        CalmaConfig.testMode=2;
        NativeTimeline.Context friends=context("9010",new TimelineRequest());
        check(deliver(friends,true,now,page(null,oneWay)).A0S.isEmpty(),"Friends does not reuse the Following generation");
        CalmaConfig.testMode=1;
        check(contains(deliver(following,true,now,page(null)),oneWay),"returning to Following reuses its own complete timeline");
        System.out.println("Native timeline: "+checks+" assertions passed");
    }
}
