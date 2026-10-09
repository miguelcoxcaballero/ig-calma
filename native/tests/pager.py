#!/usr/bin/env python3
"""Drive the real preload scheduler through busy, hidden, resumed and complete states."""
from pathlib import Path
import argparse, struct, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
PACKAGE='es/calma/instagram/nativeapp'
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--pager-source',type=Path,default=ROOT/'native/src'/PACKAGE/'NativeTimelinePager.java');args=parser.parse_args()
files={
'android/os/Looper.java':'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'''package android.os; import java.util.*; public class Handler {
 public static Queue<Runnable> queue=new ArrayDeque<>();public static java.util.List<Long> delays=new ArrayList<>();
 public Handler(Looper l){} public boolean postDelayed(Runnable r,long d){queue.add(r);delays.add(d);return true;}
 public static void drain(){int n=0;while(!queue.isEmpty()){if(++n>30)throw new AssertionError("Unbounded retries");queue.remove().run();}}
}''',
'android/graphics/Rect.java':'package android.graphics; public class Rect {public int width=100; public int width(){return width;}}',
'android/view/View.java':'''package android.view; import android.graphics.Rect; public class View {
 public static final int VISIBLE=0;public boolean shown=true;public int portion=100;
 public int getWindowVisibility(){return 0;}public boolean isShown(){return shown;}
 public boolean getGlobalVisibleRect(Rect r){r.width=portion;return shown;} public int getWidth(){return 100;}
}''',
'X/Trigger.java':'package X;public interface Trigger {}',
'X/NamedTrigger.java':'package X;public class NamedTrigger implements Trigger {public NamedTrigger(String s){}}',
'X/Reason.java':'package X;public class Reason {public static final Reason A0R=new Reason();}',
'X/Envelope.java':'package X;public class Envelope {}',
PACKAGE+'/CalmaConfig.java':'package es.calma.instagram.nativeapp; class CalmaConfig {static long epoch=1;static int mode=1;static long sessionId(){return epoch;}static int mode(){return mode;}}',
PACKAGE+'/NativeRelations.java':'package es.calma.instagram.nativeapp; class NativeRelations {static String owner(Object session){return session.toString();}}',
PACKAGE+'/NativeFeedBudget.java':'package es.calma.instagram.nativeapp; class NativeFeedBudget {static boolean timelineVisible(){return false;}}',
PACKAGE+'/NativeTimelineProgress.java':'''package es.calma.instagram.nativeapp; import java.util.*; class NativeTimelineProgress {
 static String cursor="first";static boolean replay;static int rendered;
 static String next(Object s){return cursor;}static boolean replay(Object s){return replay;}
 static List<?> local(Object s){return Arrays.asList("friend");}static void rendered(Object s){replay=false;rendered++;}
}''',
PACKAGE+'/NativePagerTest.java':'''package es.calma.instagram.nativeapp;
import android.os.Handler;import android.view.View;import java.util.*;
public class NativePagerTest {
 public static class Fragment {public View view=new View();public boolean hidden,resumed=true;public boolean isResumed(){return resumed;}public boolean isHidden(){return hidden;}public View getView(){return view;}}
 public static class Host {public Fragment A01=new Fragment();}
 public static class Controller {
  public Object A0X="100";public Host A0Y=new Host();public int attempts,loads,local;public boolean alwaysBusy;
  public boolean A0J(X.Trigger t,X.Reason r,String cursor,Map<?,?> p){
   attempts++;if(alwaysBusy || attempts==1)return false;
   loads++;if(loads>2)throw new AssertionError("Duplicate native cursor");
   if(!"following".equals(p.get("pagination_source")))throw new AssertionError("Wrong feed source");
   Handler.queue.add(()->{NativeTimelineProgress.cursor=loads==1?"second":null;NativeTimelinePager.completed(this);});return true;
  }
  public void A0E(X.Envelope head,List<?> rows,boolean replace,boolean force){if(rows.size()!=1 || !replace || !force)throw new AssertionError("Local replay shape");local++;}
 }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  Controller c=new Controller();NativeTimelinePager.delivery(c,new X.Envelope(),true);Handler.drain();
  check(c.loads==2 && c.attempts==3,"Preload retries busy native controller and loads all next pages without any scroll or budget tick");
  check(Handler.delays.contains(250L),"Busy retry is delayed");
  c.A0Y.A01.hidden=true;NativeTimelineProgress.replay=true;NativeTimelinePager.wake(c.A0X);Handler.drain();
  check(c.local==1 && NativeTimelineProgress.rendered==1,"Resolved Friends render locally even while Home is hidden");
  NativeTimelineProgress.cursor="third";NativeTimelinePager.wake(c.A0X);Handler.drain();check(c.attempts==3,"Hidden Home does not fetch pages");
  c.A0Y.A01.hidden=false;c.A0Y.A01.view.portion=30;NativeTimelinePager.wake(c.A0X);Handler.drain();check(c.attempts==3,"DM swipe does not preload offscreen Home");
  c.A0Y.A01.view.portion=100;c.alwaysBusy=true;NativeTimelinePager.delivery(c,new X.Envelope(),true);Handler.drain();
  check(c.attempts==10,"Busy retries are bounded until the next native event");
  CalmaConfig.epoch++;NativeTimelinePager.wake(c.A0X);CalmaConfig.epoch++;Handler.drain();check(c.attempts==10,"Stale selection cannot start a request");
  System.out.println("PASS: real pager scheduling, busy retry, complete preload, hidden Friends replay, DM visibility and selection cancellation");
 }
}'''
}
# Native DEX identifiers start with digits. Rename fixture constant-pool UTF8 names
# after Java compilation to exercise the production reflection signatures exactly.
renames={b'X/Trigger':b'X/0AHw',b'X/NamedTrigger':b'X/08cS',b'X/Reason':b'X/02pk',b'X/Envelope':b'X/08KU'}
def rename(data):
 out=bytearray(data[:10]);count=int.from_bytes(data[8:10],'big');pos=10;i=1
 sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
 while i<count:
  tag=data[pos];out.append(tag);pos+=1
  if tag==1:
   length=int.from_bytes(data[pos:pos+2],'big');pos+=2;value=data[pos:pos+length];pos+=length
   for old,new in renames.items():value=value.replace(old,new)
   out+=struct.pack('>H',len(value))+value
  else:
   size=sizes[tag];out+=data[pos:pos+size];pos+=size
   if tag in (5,6):i+=1
  i+=1
 return bytes(out)+data[pos:]
with tempfile.TemporaryDirectory(prefix='calma-native-pager-') as temp:
 root=Path(temp);paths=[]
 for name,source in files.items():
  p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);paths.append(str(p))
 subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-17','-proc:none','-d',str(root),*paths,str(args.pager_source),str(ROOT/'native/src'/PACKAGE/'StockAccess.java')],check=True)
 for p in list(root.rglob('*.class')):
  data=rename(p.read_bytes());destination=root/(renames.get(str(p.relative_to(root).with_suffix('')).encode(),str(p.relative_to(root).with_suffix('')).encode()).decode()+'.class')
  if destination!=p:p.unlink()
  destination.write_bytes(data)
 subprocess.run(['java','-cp',str(root),'es.calma.instagram.nativeapp.NativePagerTest'],check=True)
