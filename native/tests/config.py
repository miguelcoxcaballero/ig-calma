#!/usr/bin/env python3
"""Verify the real feed policy and upgrade preferences, without an Android device."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'es/calma/instagram/nativeapp'
fixtures = {
    'android/content/Context.java': '''package android.content; public class Context {
        public static final int MODE_PRIVATE=0; public final SharedPreferences prefs=new SharedPreferences();
        public Context getApplicationContext(){return this;} public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
    }''',
    'android/content/SharedPreferences.java': '''package android.content; public final class SharedPreferences {
        public final java.util.Map<String,Object> values=new java.util.HashMap<>();
        public String getString(String key,String fallback){Object v=values.get(key);return v instanceof String?(String)v:fallback;} public boolean getBoolean(String key,boolean fallback){Object v=values.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
        public java.util.Map<String,?> getAll(){return new java.util.HashMap<>(values);} public Editor edit(){return new Editor();}
        public final class Editor {
            public Editor putString(String key,String value){values.put(key,value);return this;}
            public Editor putInt(String key,int value){values.put(key,value);return this;}
            public Editor putBoolean(String key,boolean value){values.put(key,value);return this;}
            public Editor remove(String key){values.remove(key);return this;}public void apply(){}
        }
    }''',
    PACKAGE+'/ConfigTest.java': '''package es.calma.instagram.nativeapp;
        public final class ConfigTest {
            static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
            public static void main(String[] args){
                check(CalmaConfig.mode()==1 && "FOLLOWING".equals(CalmaConfig.feed()),"Following is available before context initialization");
                CalmaConfig.init(null);check(CalmaConfig.mode()==1,"missing context does not enable a removed mode");
                android.content.Context app=new android.content.Context();
                boolean upgrading=args[0].equals("upgrade");
                if(upgrading){
                    app.prefs.values.put("mode",2);app.prefs.values.put("native_friends_feed_initialized",true);
                    app.prefs.values.put("native_48h_initialized",true);app.prefs.values.put("limit",10);app.prefs.values.put("reels",false);
                    app.prefs.values.put("feed:100","RECENTS");app.prefs.values.put("feed:200","RECENTS");
                    app.prefs.values.put("feed:300","FAVORITES");app.prefs.values.put("feed:400","FOLLOWING");
                    app.prefs.values.put("credits","preserved");
                }
                CalmaConfig.init(app);
                check(CalmaConfig.mode()==1 && Integer.valueOf(1).equals(app.prefs.values.get("mode")),"new and existing installations use Following");
                if(upgrading){
                    check("FOLLOWING".equals(app.prefs.values.get("feed:100")) && "FOLLOWING".equals(app.prefs.values.get("feed:200")),"all saved Friends accounts migrate");
                    check("FAVORITES".equals(app.prefs.values.get("feed:300")) && "FOLLOWING".equals(app.prefs.values.get("feed:400")),"other selections remain intact");
                    check("preserved".equals(app.prefs.values.get("credits")),"migration preserves credits");
                }
                check(!app.prefs.values.containsKey("limit"),"old post-count limit removed");
                check(CalmaConfig.reels()==!upgrading,"migration preserves user's Reels choice");
                check(CalmaConfig.feedWindowSeconds()==172800,"48-hour feed unchanged");
                long before=CalmaConfig.sessionId();CalmaConfig.setReels(upgrading);
                check(CalmaConfig.reels()==upgrading && CalmaConfig.sessionId()>before,"Reels control still updates session");
                CalmaConfig.select("FAVORITES");check(CalmaConfig.mode()==0,"Favorites retains stock filtering");
                long generation=CalmaConfig.sessionId(), cache=CalmaConfig.contentId();
                CalmaConfig.select("FOLLOWING");
                check(CalmaConfig.mode()==1 && CalmaConfig.sessionId()>generation,"Following cancels stale requests");
                check(CalmaConfig.contentId()==cache,"Switching modes preserves existing Following cache");
                CalmaConfig.select("BLENDED_FOR_YOU");check(CalmaConfig.mode()==0,"For you retains algorithm");
                CalmaConfig.scope(2);check(CalmaConfig.mode()==1,"Legacy response scopes cannot reactivate mutual filtering");
                CalmaConfig.scope(null);check(CalmaConfig.mode()==0,"Request scope does not leak");
                CalmaConfig.select("RECENTS");check(CalmaConfig.mode()==1 && "FOLLOWING".equals(CalmaConfig.feed()),"Legacy native selection routes to Following");
                CalmaConfig.init(app);check(CalmaConfig.mode()==1,"Migration is idempotent");
                System.out.println("PASS: "+args[0]+" Following default, removed-mode routing and preference migration");
            }
        }'''
}
with tempfile.TemporaryDirectory(prefix='calma-config-') as tmp:
    work=Path(tmp)
    for name,source in fixtures.items():
        target=work/'src'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source)
    sources=list((work/'src').rglob('*.java'))+[ROOT/'native/src'/PACKAGE/'CalmaConfig.java']
    subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-17','-proc:none','-d',str(work/'classes'),*map(str,sources)],check=True)
    for case in ['fresh','upgrade']:
        subprocess.run(['java','-cp',str(work/'classes'),'es.calma.instagram.nativeapp.ConfigTest',case],check=True)
