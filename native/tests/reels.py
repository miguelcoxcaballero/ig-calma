#!/usr/bin/env python3
"""Single-Reel playback, per-viewer paging and unrestricted earned For you time."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'es/calma/instagram/nativeapp'
fixture = r'''
package es.calma.instagram.nativeapp;
public class ReelsTest {
    public static final class Direct { public String A02 = "friend-sender"; }
    public static final class NativeList extends java.util.AbstractList<Object> {
        final java.util.List<Object> values;
        NativeList(Object... values) { this.values=java.util.Arrays.asList(values); }
        public static NativeList of(Object value) { return new NativeList(value); }
        public Object get(int index){return values.get(index);}
        public int size(){return values.size();}
    }
    public static final class Config {
        public Object A0N = new Direct();
        public String A1k = "shared-clip";
        public String A1N = "unrelated-clip,shared-clip,another-clip";
        public NativeList A0G = NativeList.of("unrelated-clip");
        public int A07 = 2;
        public boolean A2u, A20, A2t, A24, A25;
        public boolean A27=true, A28=true, A2o=true, A2N=true, A2O=true, A3b=true, A2n=true, A2r=true, A3H=true;
    }
    public static final class Pager { public boolean input=true; public void setUserInputEnabled(boolean enabled) { input=enabled; } }
    public static final class Controller { public Pager A0A = new Pager(); public Object A0P; Controller(Object config){A0P=config;} }
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    public static void main(String[] args) {
        Object session = new Object();
        Config config = new Config();
        check(!CalmaReels.allowLaunch(config,null), "requires a real account session");
        config.A0N=null;
        check(CalmaReels.allowPrefetch(config,session) && !config.A2u && config.A07==2,"prefetch never changes the viewer before selection/time is known");
        check(CalmaReels.allowLaunch(config,session), "opens the selected feed Reel without hiding it");
        check(config.A2u && !config.A27 && !config.A28 && !config.A2o, "disables tail, backward, dual and refresh pagination");
        check(!config.A2N && !config.A2O && config.A20 && !config.A3b, "disables Blend recommendations and tabs");
        check(config.A2t && config.A24 && config.A25 && !config.A2n && !config.A2r && !config.A3H, "does not repopulate from native grid or flash cache");
        check(config.A07==0 && config.A1N==null && config.A0G.size()==1 && "shared-clip".equals(config.A0G.get(0)), "pins source to the opened clip");
        Controller pager = new Controller(config);
        CalmaReels.lockPager(pager);
        check(!pager.A0A.input && CalmaReels.maySelect(pager,0) && !CalmaReels.maySelect(pager,1) && !CalmaReels.maySelect(pager,-1), "locks gesture, forward, backward and programmatic clip paging");
        Config dm=new Config();NativeRelations.mutual=false;
        check(CalmaReels.launchOrDefer("A09",new Object[]{null,null,dm,session}) && dm.A2u && NativeFeedBudget.directLaunches==1,"opens the specific DM Reel immediately without a friendship lookup");
        Config grid=new Config();grid.A0N=null;grid.A1k=null;grid.A07=1;grid.A0G=new NativeList("first","selected","last");
        check(CalmaReels.allowLaunch(grid,session) && "selected".equals(grid.A1k) && grid.A0G.size()==1,"pins the selected grid item when only source IDs and an index are supplied");
        Config empty=new Config();empty.A0N=null;empty.A1k=null;empty.A0G=new NativeList();
        check(!CalmaReels.allowLaunch(empty,session), "does not open recommendations without an explicit clip outside earned time");
        check("es.calma.instagram.nativeapp.BlockedReelsFragment".equals(CalmaReels.restoredClass("X.05Cb")), "saved-state recommendation viewers cannot bypass gate");
        check("other.fragment".equals(CalmaReels.restoredClass("other.fragment")), "restoration preserves DM and story fragments");
        NativeFeedBudget.allowed=true;
        check(!CalmaReels.tabLocked() && NativeFeedBudget.tabLaunches==1,"earned For you time allows the native Reels tab");
        check(CalmaReels.allowLaunch(config,session) && !config.A2u && config.A27 && config.A28 && config.A2o,"earned For you time restores original pagination even on a previously pinned config");
        check(config.A07==2 && config.A1N!=null && "unrelated-clip".equals(config.A0G.get(0)),"paid viewer restores original index and complete source");
        check(CalmaReels.allowLaunch(empty,session) && CalmaReels.maySelect(new Controller(empty),8),"paid algorithm can open without a seed and advance freely");
        Controller paid=new Controller(config);CalmaReels.lockPager(paid);
        check(paid.A0A.input && CalmaReels.maySelect(paid,4),"For you scrolling stays active with the blocking setting enabled");
        Controller direct=new Controller(dm);CalmaReels.lockPager(direct);
        check(!direct.A0A.input && !CalmaReels.maySelect(direct,1),"DM keeps its own single-clip policy even while For you has time");
        NativeFeedBudget.allowed=false;
        check(!CalmaReels.maySelect(paid,1),"exhausted time stops programmatic scrolling");
        CalmaConfig.enabled=false;
        check(CalmaReels.allowLaunch(dm,session) && !dm.A2u && dm.A27 && dm.A1N!=null,"turning blocking off restores the original viewer config");
        check(CalmaReels.allowLaunch(null,null) && CalmaReels.maySelect(paid,4), "setting off restores native behavior");
        paid.A0A.input=true;CalmaReels.lockPager(paid);
        check(paid.A0A.input, "setting off leaves pager unchanged");
        System.out.println("Native single-Reel playback, per-viewer scrolling and earned-time checks passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='calma-reels-tests-') as temporary:
    work = Path(temporary)
    source = work / PACKAGE
    source.mkdir(parents=True)
    (source / 'ReelsTest.java').write_text(fixture)
    (source / 'NativeFeedBudget.java').write_text('package es.calma.instagram.nativeapp; public final class NativeFeedBudget { public static boolean allowed=false; public static int tabLaunches, directLaunches; public static boolean reelsAllowed(){return allowed;} public static void reelsLaunched(){tabLaunches++;} public static void directLaunched(){directLaunches++;} }')
    (source / 'CalmaConfig.java').write_text('package es.calma.instagram.nativeapp; public final class CalmaConfig { static boolean enabled=true; public static boolean reels(){return enabled;} public static long sessionId(){return 1;} }')
    (source / 'NativeRelations.java').write_text('package es.calma.instagram.nativeapp; public final class NativeRelations { static boolean mutual; static Object session; static String id; public static String owner(Object session){return "owner";} public static void resolveMutual(Object session,String sender,Runnable completion){completion.run();} public static boolean isMutual(Object account,String sender){session=account;id=sender;return mutual;} }')
    classes = work / 'classes'
    classes.mkdir()
    android = ROOT / 'tools/android-35/android.jar'
    subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-1.8','-proc:none','-bootclasspath',str(android),'-d',str(classes),str(ROOT/'native/src'/PACKAGE/'CalmaReels.java'), *map(str, source.glob('*.java'))], check=True)
    subprocess.run(['java','-cp',str(classes)+':'+str(android),'es.calma.instagram.nativeapp.ReelsTest'],check=True)
