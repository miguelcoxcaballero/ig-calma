package es.calma.instagram.nativeapp;

import java.util.*;
import es.calma.instagram.nativeapp.NativeFeedTest.*;
import es.calma.instagram.nativeapp.NativeFeedRecoveryTest.*;

/** The same Following page must produce different, account-verified Friends rows. */
public final class NativeFriendsPolicyTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static NativeTimelineTest.IdentifiedUser user(String id) {
        NativeTimelineTest.IdentifiedUser user=new NativeTimelineTest.IdentifiedUser(id);
        user.A00=new UserDictionary(true,true); // positive model may belong to an older relationship
        return user;
    }
    public static void main(String[] args)throws Exception {
        Session session=new Session("9600"), other=new Session("9601");
        Object friend=user("96001"), oneWay=user("96002"), unknown=user("96003");
        long now=System.currentTimeMillis()/1000;
        Object a=new NativeTimelineTest.Item("mutual",now-3,friend);
        Object b=new NativeTimelineTest.Item("one-way",now-2,oneWay);
        Object c=new NativeTimelineTest.Item("unverified",now-1,unknown);
        List<Object> posts=Arrays.asList(a,b,c);
        CalmaConfig.testMode=2;
        check(NativeFeed.filter(posts,false,now,true,session).items.isEmpty(),"Friends must not trust stale positive native models before account verification");
        check(NativeFeed.missing(Collections.emptyList(),posts,session,now).size()==3,"Positive native flags still require a batch lookup");
        NativeTimelineTest.verify(session,"96001",true);NativeTimelineTest.verify(session,"96002",false);
        check(NativeFeed.filter(posts,false,now,true,session).items.equals(Arrays.asList(a)),"Only a verified mutual author is visible in Friends");
        check(NativeFeed.filter(posts,false,now,true,other).items.isEmpty(),"Native positives cannot import another account's verified friendships");
        Result bypass=new Result(new Response());bypass.A02=Arrays.asList(new Wrapper(a),new Wrapper(b),new Wrapper(c));
        NativeFeed.delivered(new Controller(session),new Object(),bypass);
        check(bypass.A02.size()==1 && ((Wrapper)bypass.A02.get(0)).A0A()==a,"Missing native envelope cannot bypass final adapter guard");
        CalmaConfig.testMode=1;
        check(NativeFeed.filter(posts,false,now,true,session).items.equals(Arrays.asList(c,b,a)),"Following still shows every followed account without waiting for friendships");
        CalmaConfig.testMode=2;
        NativeFeedRecoveryTest.Request request=new NativeFeedRecoveryTest.Request();
        NativeFeedRecoveryTest.Controller controller=new NativeFeedRecoveryTest.Controller(session);
        NativeFeedRecoveryTest.Envelope envelope=new NativeFeedRecoveryTest.Envelope(request);
        NativeFeed.context(null,null,null,session,request,null);
        Response page=NativeTimelineTest.page(null,a,b);page.A0P=request.A0H;
        Parser parser=new Parser();parser.A01=session;
        NativeFeed.response(page,parser);NativeFeed.delivered(controller,envelope,new Result(page));
        check(page.A0S.size()==1,"Completed native Friends delivery excludes one-way account");
        NativeTimelineTest.verify(session,"96001",false);
        Response cached=NativeTimelineTest.page("unused",a,b);
        NativeFeed.delivered(controller,envelope,new Result(cached));
        check(cached.A0S.isEmpty(),"Completed snapshot rechecks new followed_by=false before UI delivery");
        check(NativeTimelineProgress.local(session).isEmpty(),"Local snapshot replay also removes a no-longer-mutual author");
        System.out.println("Friends policy: stale positive, exact-account verification, Following distinction and snapshot revocation passed");
    }
}
