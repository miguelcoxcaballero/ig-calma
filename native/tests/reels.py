#!/usr/bin/env python3
"""JVM checks for the original native shared-Reel permission and pagination policy."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'es/calma/instagram/nativeapp'
fixture = r'''
package es.calma.instagram.nativeapp;
public class ReelsTest {
    public static final class Direct { public String A02 = "friend-sender"; }
    public static final class NativeList {
        final Object value;
        NativeList(Object value) { this.value = value; }
        public static NativeList of(Object value) { return new NativeList(value); }
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
    public static final class Controller { public Pager A0A = new Pager(); }
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    public static void main(String[] args) {
        Object session = new Object();
        Config config = new Config();
        check(!CalmaReels.allowLaunch(config,null), "requires a real account session");
        config.A0N=null;
        check(!CalmaReels.allowLaunch(config,session), "blocks recommendations outside DM");
        config.A0N=new Direct();
        NativeRelations.mutual=false;
        check(!CalmaReels.allowLaunch(config,session), "blocks unknown and one-way relationships");
        NativeRelations.mutual=true;
        check(CalmaReels.allowLaunch(config,session), "allows verified mutual sender's clip");
        check(NativeRelations.session==session && "friend-sender".equals(NativeRelations.id), "checks sender, not media owner");
        check(config.A2u && !config.A27 && !config.A28 && !config.A2o, "disables tail, backward, dual and refresh pagination");
        check(!config.A2N && !config.A2O && config.A20 && !config.A3b, "disables Blend recommendations and tabs");
        check(config.A2t && config.A24 && config.A25 && !config.A2n && !config.A2r && !config.A3H, "does not repopulate from native grid or flash cache");
        check(config.A07==0 && config.A1N==null && "shared-clip".equals(config.A0G.value), "pins source to the shared clip");
        Controller pager = new Controller();
        CalmaReels.lockPager(pager);
        check(!pager.A0A.input && CalmaReels.maySelect(0) && !CalmaReels.maySelect(1) && !CalmaReels.maySelect(-1), "locks native clip paging");
        config.A1k=null;
        check(!CalmaReels.allowLaunch(config,session), "does not open an unpinned recommendations source");
        check("es.calma.instagram.nativeapp.BlockedReelsFragment".equals(CalmaReels.restoredClass("X.05Cb")), "saved-state fragment restoration cannot bypass gate");
        check("es.calma.instagram.nativeapp.BlockedReelsFragment".equals(CalmaReels.restoredClass("X.01Co")), "restored clips tabs cannot bypass gate");
        check("es.calma.instagram.nativeapp.BlockedReelsFragment".equals(CalmaReels.restoredClass("X.0AF3")), "restored homecoming clips cannot bypass gate");
        check("other.fragment".equals(CalmaReels.restoredClass("other.fragment")), "restoration preserves DM and story fragments");
        CalmaConfig.enabled=false;
        check("X.05Cb".equals(CalmaReels.restoredClass("X.05Cb")), "setting off restores normal fragment creation");
        check(CalmaReels.allowLaunch(null,null) && CalmaReels.maySelect(4), "setting off restores native behavior");
        pager.A0A.input=true;
        CalmaReels.lockPager(pager);
        check(pager.A0A.input, "setting off leaves pager unchanged");
        System.out.println("Native Reel permission, source pinning and pagination checks passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='calma-reels-tests-') as temporary:
    work = Path(temporary)
    source = work / PACKAGE
    source.mkdir(parents=True)
    (source / 'ReelsTest.java').write_text(fixture)
    (source / 'CalmaConfig.java').write_text('package es.calma.instagram.nativeapp; public final class CalmaConfig { static boolean enabled=true; public static boolean reels(){return enabled;} public static long sessionId(){return 1;} }')
    (source / 'NativeRelations.java').write_text('package es.calma.instagram.nativeapp; public final class NativeRelations { static boolean mutual; static Object session; static String id; public static String owner(Object session){return "owner";} public static void resolveMutual(Object session,String sender,Runnable completion){completion.run();} public static boolean isMutual(Object account,String sender){session=account;id=sender;return mutual;} }')
    classes = work / 'classes'
    classes.mkdir()
    android = ROOT / 'tools/android-35/android.jar'
    subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-1.8','-proc:none','-bootclasspath',str(android),'-d',str(classes),str(ROOT/'native/src'/PACKAGE/'CalmaReels.java'), *map(str, source.glob('*.java'))], check=True)
    subprocess.run(['java','-cp',str(classes)+':'+str(android),'es.calma.instagram.nativeapp.ReelsTest'],check=True)
