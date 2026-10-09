#!/usr/bin/env python3
"""Run the real native updater host against deterministic Android lifecycle fixtures.

This verifies hosting, not Android WebView or PackageInstaller implementation.
Read's browser tests and Photos' literal-source tests remain required separately.
"""
from pathlib import Path
import argparse
import hashlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'es/calma/instagram/nativeapp'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk',type=Path,help='Also verify the updater assets actually packaged in this native APK')
args=parser.parse_args()
PINS = {
    'inhouse-read/android-update.js': 'e617dd9aa1d582116ed7ec7b9851c4eeb42fb6040bb0c8d0a3904ae302540da1',
    'inhouse-read/android-update.css': 'acde6bf279bca975c6f8d1b3bd550e825df971c54af5b2a9676b09ed08870a27',
    'inhouse-read/idle-startup.js': '87610de7d16196b39b9203a861d501cef239551fc8803fd5c194327f7fbd5343',
    'inhouse-read/updater-native.methods.txt': 'bf5a23ca0fe2a2298e421f76b1be0e85b1e1b331255c80f8878e0713d42589c7',
    'inhouse-photos/UpdateInstaller.kt': 'e2bf78c49f9f75d3082637ed47c0e2ad374d8efa7b3ab1a9ff40510ced63ce68',
}
for name, digest in PINS.items():
    assert hashlib.sha256((ROOT/'vendor'/name).read_bytes()).hexdigest() == digest, name

fixtures = {
    'android/R.java': 'package android; public final class R { public static final class id { public static final int content=1; } }',
    'android/content/Context.java': '''package android.content; public class Context {
      public Context getApplicationContext(){return this;}
      public void registerComponentCallbacks(ComponentCallbacks2 callback){}
    }''',
    'android/content/MutableContextWrapper.java': '''package android.content;
      public class MutableContextWrapper extends Context { public Context base;
        public MutableContextWrapper(Context value){base=value;} public void setBaseContext(Context value){base=value;}
      }''',
    'android/content/Intent.java': '''package android.content;
      public final class Intent { public final Class<?> target; public String manifest;
        public Intent(Context context,Class<?> value){target=value;}
        public Intent putExtra(String name,String value){if(name.equals("manifest"))manifest=value;return this;}
      }''',
    'android/content/ComponentCallbacks2.java': '''package android.content;
      public interface ComponentCallbacks2 { int TRIM_MEMORY_BACKGROUND=40;
        void onConfigurationChanged(android.content.res.Configuration value); void onLowMemory(); void onTrimMemory(int level);
      }''',
    'android/content/res/Configuration.java': 'package android.content.res; public final class Configuration {}',
    'android/os/Bundle.java': 'package android.os; public final class Bundle {}',
    'android/os/Looper.java': 'package android.os; public final class Looper { public static Looper getMainLooper(){return new Looper();} }',
    'android/os/Handler.java': '''package android.os; public final class Handler {
      public static final java.util.List<Runnable> pending=new java.util.ArrayList<>();
      public Handler(Looper looper){} public boolean post(Runnable task){pending.add(task);return true;}
      public static void drain(){while(!pending.isEmpty())pending.remove(0).run();}
    }''',
    'android/util/Log.java': 'package android.util; public final class Log { public static int w(String tag,String value,Throwable error){return 0;} }',
    'android/view/ViewParent.java': 'package android.view; public interface ViewParent {}',
    'android/view/ViewTreeObserver.java': '''package android.view; public final class ViewTreeObserver {
      public interface OnWindowFocusChangeListener { void onWindowFocusChanged(boolean focus); }
      public final java.util.List<OnWindowFocusChangeListener> listeners=new java.util.ArrayList<>();
      public boolean isAlive(){return true;}
      public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener value){listeners.add(value);}
      public void removeOnWindowFocusChangeListener(OnWindowFocusChangeListener value){listeners.remove(value);}
      public void focus(boolean value){for(OnWindowFocusChangeListener listener:new java.util.ArrayList<>(listeners))listener.onWindowFocusChanged(value);}
    }''',
    'android/view/View.java': '''package android.view; public class View {
      public static final int INVISIBLE=4,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
      public ViewParent parent; public int visibility; public boolean focusable=true;
      public final ViewTreeObserver observer=new ViewTreeObserver();
      public void setVisibility(int value){visibility=value;} public void setFocusable(boolean value){focusable=value;}
      public void setImportantForAccessibility(int value){} public ViewParent getParent(){return parent;}
      public ViewTreeObserver getViewTreeObserver(){return observer;}
    }''',
    'android/view/ViewGroup.java': '''package android.view; public class ViewGroup extends View implements ViewParent {
      public static final class LayoutParams { public final int width,height; public LayoutParams(int w,int h){width=w;height=h;} }
      public final java.util.List<View> children=new java.util.ArrayList<>();
      public void addView(View child,LayoutParams params){if(child.parent!=null)throw new IllegalStateException("already attached");children.add(child);child.parent=this;}
      public void removeView(View child){children.remove(child);child.parent=null;}
    }''',
    'android/view/Window.java': '''package android.view; public final class Window {
      public final ViewGroup decor=new ViewGroup(); public View getDecorView(){return decor;}
    }''',
    'android/app/Activity.java': '''package android.app; public class Activity extends android.content.Context {
      public final android.view.Window window=new android.view.Window(); public boolean focused,finishing,destroyed;
      public android.content.Context application=new Application();
      public final java.util.List<android.content.Intent> launched=new java.util.ArrayList<>();
      public boolean isFinishing(){return finishing;} public boolean isDestroyed(){return destroyed;}
      public android.view.View findViewById(int id){return window.decor;}
      public android.view.Window getWindow(){return window;} public boolean hasWindowFocus(){return focused;}
      public void startActivity(android.content.Intent intent){launched.add(intent);}
      public android.content.Context getApplicationContext(){return application;}
      public void setFocused(boolean value){focused=value;window.decor.observer.focus(value);}
    }''',
    'android/app/Application.java': '''package android.app; public class Application extends android.content.Context {
      public interface ActivityLifecycleCallbacks {
        void onActivityResumed(Activity value); void onActivityPaused(Activity value); void onActivityDestroyed(Activity value);
        void onActivityCreated(Activity value,android.os.Bundle state);void onActivityStarted(Activity value);void onActivityStopped(Activity value);
        void onActivitySaveInstanceState(Activity value,android.os.Bundle state);
      }
      public final java.util.List<ActivityLifecycleCallbacks> callbacks=new java.util.ArrayList<>();
      public final java.util.List<android.content.ComponentCallbacks2> memory=new java.util.ArrayList<>();
      public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks value){callbacks.add(value);}
      public void registerComponentCallbacks(android.content.ComponentCallbacks2 value){memory.add(value);}
    }''',
    'android/webkit/JavascriptInterface.java': '''package android.webkit;
      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface JavascriptInterface {}''',
    'android/webkit/RenderProcessGoneDetail.java': 'package android.webkit; public final class RenderProcessGoneDetail {}',
    'android/webkit/WebSettings.java': '''package android.webkit; public final class WebSettings {
      public static final int MIXED_CONTENT_NEVER_ALLOW=1; public boolean javaScript,storage,files=true,content=true; public String agent="fixture";
      public void setJavaScriptEnabled(boolean value){javaScript=value;}public void setDomStorageEnabled(boolean value){storage=value;}
      public void setAllowFileAccess(boolean value){files=value;}public void setAllowContentAccess(boolean value){content=value;}
      public void setMixedContentMode(int value){}public void setUserAgentString(String value){agent=value;}public String getUserAgentString(){return agent;}
    }''',
    'android/webkit/WebViewClient.java': '''package android.webkit; public class WebViewClient {
      public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){return false;}
    }''',
    'android/webkit/WebView.java': '''package android.webkit; public final class WebView extends android.view.View {
      public static boolean unavailable; public static final java.util.List<WebView> instances=new java.util.ArrayList<>();
      public final android.content.Context context; public final WebSettings settings=new WebSettings();
      public final java.util.Map<String,Object> bridges=new java.util.HashMap<>();
      public final java.util.List<String> loads=new java.util.ArrayList<>(),scripts=new java.util.ArrayList<>();
      public WebViewClient client;public boolean paused,closed,stopped;
      public WebView(android.content.Context value){if(unavailable)throw new IllegalStateException("missing WebView");context=value;instances.add(this);}
      public WebSettings getSettings(){return settings;}public void onResume(){paused=false;}public void onPause(){paused=true;}
      public void loadUrl(String value){loads.add(value);}public void evaluateJavascript(String value,Object callback){scripts.add(value);}
      public void addJavascriptInterface(Object value,String key){bridges.put(key,value);}public void removeJavascriptInterface(String key){bridges.remove(key);}
      public void setWebViewClient(WebViewClient value){client=value;}public void stopLoading(){stopped=true;}public void destroy(){closed=true;}
    }''',
    'android/content/ContentValues.java': 'package android.content; public final class ContentValues {}',
    'android/database/Cursor.java': 'package android.database; public interface Cursor {}',
    'android/net/Uri.java': 'package android.net; public final class Uri {}',
    'android/content/ContentProvider.java': '''package android.content; public abstract class ContentProvider {
      public Context context;public Context getContext(){return context;}
      public abstract boolean onCreate();public abstract android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] arguments,String order);
      public abstract String getType(android.net.Uri uri); public abstract android.net.Uri insert(android.net.Uri uri,ContentValues values);
      public abstract int delete(android.net.Uri uri,String selection,String[] arguments);public abstract int update(android.net.Uri uri,ContentValues values,String selection,String[] arguments);
    }''',
    'es/calma/instagram/UpdateActivity.java': 'package es.calma.instagram; public final class UpdateActivity extends android.app.Activity {}',
    'es/calma/instagram/UpdateAssetClient.java': '''package es.calma.instagram; public class UpdateAssetClient extends android.webkit.WebViewClient {
      public final android.content.Context context;public UpdateAssetClient(android.content.Context value){context=value;}
    }''',
    'com/instagram/mainactivity/InstagramMainActivity.java': 'package com.instagram.mainactivity; public final class InstagramMainActivity extends android.app.Activity {}',
    'com/instagram/process/asyncinit/IgSplashScreenActivity.java': 'package com.instagram.process.asyncinit; public final class IgSplashScreenActivity extends android.app.Activity {}',
    PACKAGE+'/CalmaConfig.java': 'package es.calma.instagram.nativeapp; public final class CalmaConfig {public static int calls;public static void init(android.content.Context context){calls++;}}',
    PACKAGE+'/CalmaReels.java': 'package es.calma.instagram.nativeapp; public final class CalmaReels {public static int calls;public static void decorate(android.app.Activity value){calls++;}}',
    PACKAGE+'/NativeFeedBudget.java': 'package es.calma.instagram.nativeapp; public final class NativeFeedBudget {public static void init(){} public static void resumed(android.app.Activity a){} public static void paused(android.app.Activity a){}}',
    PACKAGE+'/UpdaterHostTest.java': '''package es.calma.instagram.nativeapp;
      import android.app.*;import android.content.*;import android.os.Handler;import android.webkit.*;
      public final class UpdaterHostTest {
        static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
        static WebView last(){return WebView.instances.get(WebView.instances.size()-1);}
        static NativeUpdates.UpdateOfferBridge bridge(WebView web){return (NativeUpdates.UpdateOfferBridge)web.bridges.get("InhouseUpdateHost");}
        public static void main(String[] args){
          Application app=new Application();NativeUpdates updates=new NativeUpdates(app);Activity first=new Activity();
          updates.onResumed(first);WebView checker=last();int count=WebView.instances.size();
          check(checker.loads.size()==1&&checker.loads.get(0).endsWith("quiet=1"),"first resume checks immediately, with only local assets");
          check(checker.visibility==android.view.View.INVISIBLE&&!checker.focusable,"checker adds no visible or focusable interface");
          check(checker.settings.javaScript&&checker.settings.storage&&!checker.settings.files&&!checker.settings.content,"local checker access restrictions");
          check(checker.bridges.size()==2&&checker.bridges.get("InhouseNative") instanceof NativeUpdates.UpdateCheckBridge,"checker only exposes check bridges");
          check(((NativeUpdates.UpdateCheckBridge)checker.bridges.get("InhouseNative")).getAppVersion().equals(CalmaBuild.VERSION),"independent Calma version");
          for(java.lang.reflect.Method method:NativeUpdates.UpdateCheckBridge.class.getDeclaredMethods())
            check(!method.getName().equals("installAppUpdate"),"no installer on check bridge");
          bridge(checker).offer("validated-manifest");Handler.drain();check(first.launched.isEmpty(),"offer waits for focus");
          updates.onPaused(first);first.setFocused(true);check(first.launched.isEmpty()&&checker.paused,"paused host cannot launch popup");
          updates.onResumed(first);check(first.launched.size()==1,"resume surfaces pending offer");
          check(first.launched.get(0).target==es.calma.instagram.UpdateActivity.class&&first.launched.get(0).manifest.equals("validated-manifest"),"validated offer forwarded without second fetch");
          bridge(checker).offer("validated-manifest");Handler.drain();check(first.launched.size()==1,"one startup popup per process");
          Activity second=new Activity();updates.onPaused(first);updates.onResumed(second);
          check(last()==checker&&WebView.instances.size()==count&&checker.loads.size()==1,"native navigation retains loaded checker");
          check(first.window.decor.children.isEmpty()&&first.window.decor.observer.listeners.isEmpty(),"old Activity releases child and listener");
          check(((MutableContextWrapper)checker.context).base==second&&checker.getParent()==second.window.decor,"context and parent transfer to active host");
          updates.onDestroyed(first);check(!checker.closed,"old host destruction cannot destroy transferred checker");
          updates.onMemoryPressure();check(!checker.closed,"foreground checker survives background-only release");
          NativeUpdates.UpdateOfferBridge stale=bridge(checker);updates.onPaused(second);updates.onMemoryPressure();
          check(checker.closed&&checker.bridges.isEmpty()&&checker.getParent()==null,"background release destroys and detaches checker");
          check(((MutableContextWrapper)checker.context).base==app,"released wrapper retains only application context");
          updates.onResumed(second);WebView fresh=last();stale.offer("stale-offer");Handler.drain();
          check(fresh!=checker&&second.launched.isEmpty(),"obsolete renderer callback cannot surface an offer");
          fresh.client.onRenderProcessGone(fresh,new RenderProcessGoneDetail());check(fresh.closed,"renderer failure releases checker");
          updates.onResumed(second);fresh=last();updates.onDestroyed(second);check(fresh.closed,"host destruction releases checker");
          WebView.unavailable=true;updates.onResumed(second);WebView.unavailable=false;
          updates.onResumed(second);check(last().loads.size()==1,"missing WebView is nonfatal and recovers on resume");
          updates.onDestroyed(second);

          // A queued callback must also be ignored when no earlier popup set the deduplication flag.
          NativeUpdates race=new NativeUpdates(app);Activity racing=new Activity();racing.focused=true;
          race.onResumed(racing);WebView old=last();bridge(old).offer("queued-before-destroy");race.onDestroyed(racing);
          race.onResumed(racing);Handler.drain();check(racing.launched.isEmpty(),"queued offer checks generation after destruction");
          racing.focused=false;bridge(last()).offer("new-offer");Handler.drain();racing.setFocused(true);
          check(racing.launched.size()==1,"window focus alone surfaces pending offer");race.onDestroyed(racing);

          NativeInitProvider provider=new NativeInitProvider();check(!provider.onCreate(),"provider missing context is safe");
          provider.context=app;check(provider.onCreate()&&provider.onCreate(),"provider starts successfully");
          check(app.callbacks.size()==1&&app.memory.size()==1,"initialization registers callbacks exactly once");
          Application.ActivityLifecycleCallbacks lifecycle=app.callbacks.get(0);int before=WebView.instances.size();
          int configBefore=CalmaConfig.calls,reelsBefore=CalmaReels.calls;
          Activity splash=new com.instagram.process.asyncinit.IgSplashScreenActivity();
          lifecycle.onActivityResumed(splash);
          check(WebView.instances.size()==before&&CalmaConfig.calls==configBefore&&CalmaReels.calls==reelsBefore,"async bootstrap does not start Calma work before Instagram initializes its WebView provider");
          lifecycle.onActivityResumed(new es.calma.instagram.UpdateActivity());lifecycle.onActivityResumed(new Activity());
          check(WebView.instances.size()==before,"popup and unrelated Activities do not become checker hosts");
          Activity instagram=new com.instagram.mainactivity.InstagramMainActivity();lifecycle.onActivityResumed(instagram);
          check(WebView.instances.size()==before+1&&CalmaReels.calls==1,"Instagram lifecycle starts checker and native Reel decoration");
          WebView mainChecker=last();
          check(mainChecker.loads.size()==1&&mainChecker.loads.get(0).endsWith("quiet=1"),"first real Instagram screen checks for updates immediately after bootstrap");
          lifecycle.onActivityPaused(splash);lifecycle.onActivityDestroyed(splash);
          check(!mainChecker.paused&&!mainChecker.closed&&mainChecker.getParent()==instagram.window.decor,"late bootstrap callbacks cannot pause or destroy the real host checker");
          lifecycle.onActivityPaused(instagram);app.memory.get(0).onTrimMemory(40);check(last().closed,"lifecycle forwards background memory pressure");
          lifecycle.onActivityDestroyed(instagram);
          System.out.println("PASS: native startup, focus/pause, Activity transfer, stale callback, cleanup, renderer recovery and provider lifecycle fixtures");
        }
      }''',
}

with tempfile.TemporaryDirectory(prefix='calma-native-updater-') as temporary:
    work=Path(temporary)
    # Run the existing mechanical adapters in an isolated copy, never rewrite app sources during tests.
    for name in PINS:
        target=work/'vendor'/name;target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ROOT/'vendor'/name,target)
    (work/'scripts').mkdir()
    (work/'app/assets/updates').mkdir(parents=True)
    (work/'app/src/es/calma/instagram').mkdir(parents=True)
    for name in ['vendor-updater.py','vendor-photos-installer.py']:
        shutil.copyfile(ROOT/'scripts'/name,work/'scripts'/name)
        subprocess.run([sys.executable,str(work/'scripts'/name)],check=True)
    for name in ['app/assets/updates/updater.js','app/assets/updates/android-update.css',
                 'app/src/es/calma/instagram/PhotosUpdateInstaller.kt']:
        assert (work/name).read_bytes()==(ROOT/name).read_bytes(), f'Not the original mechanically adapted updater: {name}'
    print('PASS: pinned Read/Photos originals and generated JS/CSS/Kotlin match byte for byte',flush=True)

    for name,source in fixtures.items():
        target=work/'src'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source)
    sources=list((work/'src').rglob('*.java'))
    sources += [ROOT/'native/src'/PACKAGE/name for name in ['CalmaBuild.java','NativeUpdates.java','NativeLifecycle.java','NativeInitProvider.java']]
    subprocess.run(['java','-jar',str(ROOT/'tools/ecj.jar'),'-17','-proc:none','-d',str(work/'classes'),*map(str,sources)],check=True)
    subprocess.run(['java','-cp',str(work/'classes'),'es.calma.instagram.nativeapp.UpdaterHostTest'],check=True)

# When a native extension has been built, verify the sources actually fed to its compilers.
generated=ROOT/'native/build/extension'
if (generated/'PhotosUpdateInstaller.kt').exists():
    assert (generated/'PhotosUpdateInstaller.kt').read_bytes()==(ROOT/'app/src/es/calma/instagram/PhotosUpdateInstaller.kt').read_bytes()
    assert (generated/'UpdateAssetClient.java').read_bytes()==(ROOT/'app/src/es/calma/instagram/UpdateAssetClient.java').read_bytes()
    activity=(ROOT/'app/src/es/calma/instagram/UpdateActivity.java').read_text()
    activity,count=re.subn(r'@JavascriptInterface public String getAppVersion\(\)\{try\{return getPackageManager\(\)\.getPackageInfo\(getPackageName\(\),0\)\.versionName;\}catch\(Exception e\)\{return "[^"]+";\}\}',
        '@JavascriptInterface public String getAppVersion(){return es.calma.instagram.nativeapp.CalmaBuild.VERSION;}',activity)
    assert count==1
    activity,count=re.subn(r'" InhouseReadApp/[^"]+"','" InhouseReadApp/"+es.calma.instagram.nativeapp.CalmaBuild.VERSION',activity)
    assert count==1 and activity==(generated/'UpdateActivity.java').read_text(), 'Native popup permits version-host adaptation only'
    print('PASS: compiled extension updater sources preserve Photos, Read callbacks and local asset client; only host version changed')
if args.apk:
    stable=b'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/main/android-update.json'
    native=b'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/native-instagram/native/android-update.json'
    with zipfile.ZipFile(args.apk) as apk:
        for path in (ROOT/'app/assets/updates').rglob('*'):
            if not path.is_file():
                continue
            expected=path.read_bytes()
            if path.name=='updater.js':
                assert expected.count(stable)==1
                expected=expected.replace(stable,native)
            packaged='assets/updates/'+path.relative_to(ROOT/'app/assets/updates').as_posix()
            assert apk.read(packaged)==expected, f'Native APK updater differs beyond channel binding: {packaged}'
    print('PASS: actual native APK contains the original updater assets with only the native channel URL changed')
print('LIMIT: JVM fixtures do not measure Android WebView timing, render a device popup or execute PackageInstaller.')
