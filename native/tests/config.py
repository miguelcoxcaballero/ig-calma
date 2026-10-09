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
        public boolean getBoolean(String key,boolean fallback){Object v=values.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
        public Editor edit(){return new Editor();}
        public final class Editor {
            public Editor putInt(String key,int value){values.put(key,value);return this;}
            public Editor putBoolean(String key,boolean value){values.put(key,value);return this;}
            public Editor remove(String key){values.remove(key);return this;}public void apply(){}
        }
    }''',
    PACKAGE+'/ConfigTest.java': '''package es.calma.instagram.nativeapp;
        public final class ConfigTest {
            static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
            public static void main(String[] args){
                check(CalmaConfig.mode()==2,"mutual follows required even before initialization");
                CalmaConfig.init(null);check(CalmaConfig.mode()==2,"missing context never relaxes feed");
                android.content.Context app=new android.content.Context();
                boolean upgrading=args[0].equals("upgrade");
                if(upgrading){app.prefs.values.put("mode",1);app.prefs.values.put("native_48h_initialized",true);app.prefs.values.put("limit",10);app.prefs.values.put("reels",false);}
                CalmaConfig.init(app);
                check(CalmaConfig.mode()==2 && Integer.valueOf(2).equals(app.prefs.values.get("mode")),"new and existing installations use friends");
                check(!app.prefs.values.containsKey("limit"),"old post-count limit removed");
                check(CalmaConfig.reels()==!upgrading,"migration preserves user's Reels choice");
                check(CalmaConfig.feedWindowSeconds()==172800,"48-hour feed unchanged");
                long before=CalmaConfig.sessionId();CalmaConfig.setReels(upgrading);
                check(CalmaConfig.reels()==upgrading && CalmaConfig.sessionId()>before,"Reels control still updates session");
                app.prefs.values.put("mode",1);CalmaConfig.init(app);
                check(CalmaConfig.mode()==2,"legacy preferences cannot reenable one-way follows");
                System.out.println("PASS: "+args[0]+" mutual-feed policy and preference migration");
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
