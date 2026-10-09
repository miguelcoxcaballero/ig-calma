package es.calma.instagram;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;

public class MainActivity extends Activity {
    private WebView web, homeWeb, directWeb;
    private final java.util.Map<WebView,String> reelPermissions = new java.util.IdentityHashMap<>();
    private final java.util.Map<WebView,Integer> generations = new java.util.IdentityHashMap<>();
    private String retainedOwner = "";
    private boolean paused = true, updateScheduled = false, prewarmScheduled = false;
    private WebView updateChecker;
    private boolean updateOffered=false;
    private FrameLayout root;
    private SharedPreferences prefs;
    private String filters, relations, settingsPage, appearance, reelGate, navigation, discover;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable identityWatcher = new Runnable() {
        @Override public void run() {
            reconcileOwner();
            if (web != null && trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) {
                web.evaluateJavascript("if(window.CALMA_CONFIG)window.CALMA_CONFIG.owner="+JSONObject.quote(owner())+";if(window.__calmaRelationsController)window.__calmaRelationsController.refreshIdentity(" + JSONObject.quote(owner()) + ");", null);
            }
            schedulePrewarm();
            handler.postDelayed(this, 5000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        setTheme(getResources().getIdentifier("AppTheme","style",getPackageName()));
        super.onCreate(saved);
        prefs = getSharedPreferences("calma", MODE_PRIVATE);
        try { filters = read("filter.js"); relations = read("relations.js"); settingsPage = read("settings.js"); appearance = read("appearance.js"); reelGate = read("reel-gate.js"); navigation = read("navigation.js"); discover = read("discover.js"); }
        catch (IOException e) { throw new IllegalStateException(e); }
        root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); applySystemTheme(false);
        retainedOwner = owner();
        selectTab(getIntent().getBooleanExtra("open_dm", false));
    }
    private WebView createTab(boolean direct) {
        WebView tab = new WebView(this);
        tab.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tab.setVerticalScrollBarEnabled(false); tab.setHorizontalScrollBarEnabled(false);
        tab.setBackgroundColor(systemDark()?Color.BLACK:Color.WHITE);
        tab.setVisibility(View.INVISIBLE); tab.setAlpha(0f);
        root.addView(tab, new FrameLayout.LayoutParams(-1, -1));
        if(direct)directWeb=tab;else homeWeb=tab;
        WebSettings ws=tab.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false); ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(true); ws.setSupportMultipleWindows(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        if(Build.VERSION.SDK_INT>=29)ws.setForceDark(WebSettings.FORCE_DARK_OFF);
        if(Build.VERSION.SDK_INT>=33)ws.setAlgorithmicDarkeningAllowed(false);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(tab,false);
        tab.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request) {
                Uri uri=request.getUrl();
                if(request.isForMainFrame() && handleTabAction(v,uri))return true;
                if(request.isForMainFrame() && v==web && handleSettingsAction(uri))return true;
                if(!trusted(uri)) { if(v==web)toast("Abre este enlace en tu navegador."); return true; }
                if(request.isForMainFrame() && v==web && !owner().isEmpty()) {
                    boolean toDirect="/direct/inbox/".equals(uri.getPath()) || "/direct/inbox".equals(uri.getPath());
                    boolean toHome="/".equals(uri.getPath()) && uri.getQuery()==null;
                    if((toDirect && v!=directWeb) || (toHome && v==directWeb)) {selectTab(toDirect);return true;}
                }
                if(request.isForMainFrame() && prefs.getBoolean("reels",true) && reelPath(uri.getPath())) {
                    String target=normalizeReel(uri.getPath()); String current=v.getUrl();
                    if(current!=null && target.equals(reelPermissions.get(v)) && reelPath(Uri.parse(current).getPath()))return false;
                    if(v!=web)return true;
                    final int epoch=generation(v);
                    v.evaluateJavascript("!!(window.__calmaReelGate && window.__calmaReelGate.consume("+JSONObject.quote(target)+"))",value -> {
                        if(!isLive(v) || v!=web || epoch!=generation(v))return;
                        if("true".equals(value)) { reelPermissions.put(v,target); v.loadUrl(uri.toString()); }
                        else toast("Puedes abrir los Reels que te envíen tus amigos por DM.");
                    });
                    return true;
                }
                return false;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap favicon) {
                if(!isLive(v))return;
                if(!reelPath(Uri.parse(url).getPath()))reelPermissions.remove(v);
                generations.put(v,generation(v)+1); v.setAlpha(0f);
            }
            @Override public void onPageCommitVisible(WebView v,String url) { if(isLive(v))inject(v); }
            @Override public void onPageFinished(WebView v,String url) {
                if(!isLive(v))return;
                final int epoch=generation(v);
                v.evaluateJavascript("!!window.__calma",value -> {
                    if(!isLive(v) || epoch!=generation(v))return;
                    if(!"true".equals(value))inject(v);else v.setAlpha(1f);
                });
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) {
                if(r.isForMainFrame() && isLive(v)){v.setAlpha(1f);if(v==web)toast("No se pudo cargar. Comprueba tu conexión.");}
            }
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail) {
                boolean active=v==web, wasDirect=v==directWeb;
                disposeTab(v);
                if(active)handler.post(() -> {if(web==null && !isFinishing() && !isDestroyed())selectTab(wasDirect);});
                return true;
            }
        });
        tab.setWebChromeClient(new WebChromeClient());
        tab.loadUrl(direct?"https://www.instagram.com/direct/inbox/":"https://www.instagram.com/");
        return tab;
    }
    private int generation(WebView tab){Integer value=generations.get(tab);return value==null?0:value;}
    private boolean isLive(WebView tab) {return tab!=null && (tab==homeWeb || tab==directWeb) && !isDestroyed();}
    private void visibility(WebView tab,boolean active) {
        if(!isLive(tab))return;
        tab.evaluateJavascript("window.CALMA_ACTIVE="+active+";document.dispatchEvent(new CustomEvent('calma-visibility',{bubbles:true,detail:{active:"+active+"}}));",null);
    }
    private void selectTab(boolean direct) {
        reconcileOwner();
        WebView target=direct?directWeb:homeWeb;
        if(target==null)target=createTab(direct);
        if(target==web)return;
        if(web!=null) {
            visibility(web,false); web.onPause(); web.clearFocus(); web.setVisibility(View.INVISIBLE);
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0);
        }
        web=target; web.setVisibility(View.VISIBLE);web.bringToFront();web.onResume();
        visibility(web,!paused);injectAppearance();
        web.requestFocus();
        // Restore the conversation, but never revive an old Reel/profile on a tab button.
        String path=web.getUrl()==null?null:Uri.parse(web.getUrl()).getPath();
        if(path!=null && !owner().isEmpty() && (direct?!path.startsWith("/direct/"):!"/".equals(path))) {
            web.setAlpha(0f);web.loadUrl(direct?"https://www.instagram.com/direct/inbox/":"https://www.instagram.com/");
        }
    }
    private boolean handleTabAction(WebView source,Uri uri) {
        if(!trusted(uri) || uri.getQueryParameter("calma_tab")==null)return false;
        if(source!=web || source.getUrl()==null || !trusted(Uri.parse(source.getUrl())) || owner().isEmpty())return true;
        String target=uri.getQueryParameter("calma_tab");
        if("direct".equals(target) || "home".equals(target))selectTab("direct".equals(target));
        return true;
    }
    private void disposeTab(WebView tab) {
        if(tab==null)return;
        if(tab==homeWeb)homeWeb=null;if(tab==directWeb)directWeb=null;if(tab==web)web=null;
        reelPermissions.remove(tab);generations.remove(tab);root.removeView(tab);tab.stopLoading();tab.destroy();
    }
    private void reconcileOwner() {
        String current=owner();
        if(current.equals(retainedOwner))return;
        retainedOwner=current;
        WebView inactive=web==homeWeb?directWeb:homeWeb;
        disposeTab(inactive);
    }
    private void schedulePrewarm() {
        if(paused || directWeb!=null || owner().isEmpty() || prewarmScheduled)return;
        prewarmScheduled=true;
        handler.postDelayed(() -> {prewarmScheduled=false;prewarmInbox();},2500);
    }
    private void prewarmInbox() {
        if(paused || isFinishing() || isDestroyed() || directWeb!=null || owner().isEmpty())return;
        reconcileOwner();
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
        if(info.lowMemory || ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).isLowRamDevice())return;
        createTab(true);
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(level!=TRIM_MEMORY_UI_HIDDEN && level>=TRIM_MEMORY_RUNNING_LOW)disposeTab(web==homeWeb?directWeb:homeWeb);
    }
    private boolean systemDark() { return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES; }
    private boolean reduceMotion() { return Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f; }
    private void applySystemTheme(boolean updatePage) {
        boolean dark=systemDark(); int bg=dark?Color.BLACK:Color.WHITE;
        getTheme().applyStyle(getResources().getIdentifier("AppTheme","style",getPackageName()),true);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        int flags=getWindow().getDecorView().getSystemUiVisibility();
        int light=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(dark?flags & ~light:flags | light);
        if(root!=null)root.setBackgroundColor(bg); if(homeWeb!=null)homeWeb.setBackgroundColor(bg);if(directWeb!=null)directWeb.setBackgroundColor(bg);
        if(updatePage)injectAppearance();
    }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); applySystemTheme(true); }
    private void injectAppearance() {
        if(web==null || web.getUrl()==null || !trusted(Uri.parse(web.getUrl())))return;
        web.evaluateJavascript("window.CALMA_APPEARANCE={dark:"+systemDark()+",reduceMotion:"+reduceMotion()+"};\n"+appearance,null);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if(intent.getBooleanExtra("open_dm",false)) {
        selectTab(true);
        if(web.getUrl()!=null && !"/direct/inbox/".equals(Uri.parse(web.getUrl()).getPath()))web.loadUrl("https://www.instagram.com/direct/inbox/");
    } }
    private boolean trusted(Uri uri) { String host=uri.getHost(); return "https".equalsIgnoreCase(uri.getScheme()) && host!=null && (host.equals("instagram.com") || host.endsWith(".instagram.com")); }
    private boolean reelPath(String path) { return path!=null && path.matches("^/reels?(/.*)?$"); }
    private String normalizeReel(String path) { return path==null?"":path.endsWith("/")?path:path+"/"; }
    private String owner() {
        String cookies=CookieManager.getInstance().getCookie("https://www.instagram.com/");
        String found=""; boolean session=false;
        if(cookies!=null)for(String cookie:cookies.split(";")) {
            String item=cookie.trim();
            if(item.startsWith("sessionid=") && item.length()>"sessionid=".length())session=true;
            if(item.startsWith("ds_user_id=")) {
                String id=item.substring("ds_user_id=".length()); if(id.matches("[0-9]+"))found=id;
            }
        }
        return session?found:"";
    }
    private boolean settingsPath(String path) {
        return path!=null && (path.equals("/accounts/edit/") || path.equals("/accounts/edit") || (path.equals("/accounts/settings/") || path.equals("/accounts/settings")) || path.equals("/settings/") || path.equals("/settings"));
    }
    private boolean handleSettingsAction(Uri uri) {
        if(!trusted(uri) || !settingsPath(uri.getPath()) || uri.getQueryParameter("calma_action")==null)return false;
        String current=web.getUrl();
        if(current==null || !trusted(Uri.parse(current)) || !settingsPath(Uri.parse(current).getPath()))return true;
        String action=uri.getQueryParameter("calma_action");
        if("update".equals(action)){startActivity(new Intent(this,UpdateActivity.class));return true;}
        if("new_session".equals(action)) { selectTab(false); web.loadUrl("https://www.instagram.com/"); return true; }
        if(!"save".equals(action) && !"permissions".equals(action))return true;
        try {
            int mode=Integer.parseInt(uri.getQueryParameter("mode"));
            int limit=Integer.parseInt(uri.getQueryParameter("limit"));
            String reels=uri.getQueryParameter("reels"), dm=uri.getQueryParameter("dm");
            if(mode<0 || mode>2 || limit<1 || limit>100 || !("0".equals(reels)||"1".equals(reels)) || !("0".equals(dm)||"1".equals(dm)))return true;
            prefs.edit().putInt("mode",mode).putInt("limit",limit).putBoolean("reels","1".equals(reels))
                .putBoolean("dm_mirror","1".equals(dm)).remove("following").remove("friends").apply();
            if("0".equals(dm))((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll();
            inject();
            WebView inactive=web==homeWeb?directWeb:homeWeb;
            if(inactive!=null)inject(inactive);
            web.evaluateJavascript("if(window.__calmaSettings)window.__calmaSettings.saved();",null);
            if("permissions".equals(action)) {
                if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},33);
                else notificationAccess();
            }
        }catch(Exception e){toast("No se pudo guardar la configuración.");}
        return true;
    }
    private boolean listenerEnabled() {
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if(enabled!=null)for(String entry:enabled.split(":")) {
            ComponentName component=ComponentName.unflattenFromString(entry);
            if(component!=null && getPackageName().equals(component.getPackageName()) && DmNotificationService.class.getName().equals(component.getClassName()))return true;
        }
        return false;
    }
    private void notificationAccess() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch(ActivityNotFoundException e) { toast("Este dispositivo no ofrece acceso a notificaciones."); }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==33) {
            if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)notificationAccess();
            else toast("Sin permiso, esta app no puede mostrar sus avisos de DM.");
        }
    }
    private void inject() {inject(web);}
    private void inject(WebView tab) {
        if(!isLive(tab) || tab.getUrl()==null || !trusted(Uri.parse(tab.getUrl())))return;
        final int epoch=generation(tab);
        try {
            JSONObject config=new JSONObject(); config.put("mode",prefs.getInt("mode",1)); config.put("limit",prefs.getInt("limit",20)); config.put("reels",prefs.getBoolean("reels",true)); config.put("owner",owner()); config.put("dmMirror",prefs.getBoolean("dm_mirror",false)); config.put("allowedReelPath",reelPermissions.containsKey(tab)?reelPermissions.get(tab):"");
            config.put("notificationsEnabled",((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()); config.put("listenerEnabled",listenerEnabled());
            tab.evaluateJavascript("window.CALMA_ACTIVE="+(tab==web && !paused)+";window.CALMA_TAB="+JSONObject.quote(tab==directWeb?"direct":"home")+";window.CALMA_APPEARANCE={dark:"+systemDark()+",reduceMotion:"+reduceMotion()+"};\n"+appearance+"\nwindow.CALMA_CONFIG="+config+";\n"+relations+"\n"+reelGate+"\n"+filters+"\n"+discover+"\n"+settingsPage+"\n"+navigation,value -> {
                if(!isLive(tab) || epoch!=generation(tab))return;
                tab.setAlpha(1f);
                if(tab!=web || paused){visibility(tab,false);tab.onPause();}
                if(!updateScheduled){updateScheduled=true;handler.postDelayed(() -> startUpdateChecks(),12000);}
                if(tab==web)schedulePrewarm();
            });
        }catch(JSONException e){toast("No se pudo aplicar la configuración");}
    }
    private void startUpdateChecks(){
        if(isFinishing() || isDestroyed() || updateChecker!=null)return;
        updateChecker=new WebView(this);
        updateChecker.getSettings().setJavaScriptEnabled(true);updateChecker.getSettings().setDomStorageEnabled(true);
        updateChecker.getSettings().setAllowFileAccess(false);updateChecker.getSettings().setAllowContentAccess(false);
        updateChecker.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        updateChecker.getSettings().setUserAgentString(updateChecker.getSettings().getUserAgentString()+" InhouseReadApp/0.3.5");
        updateChecker.addJavascriptInterface(new UpdateCheckBridge(),"InhouseNative");
        updateChecker.addJavascriptInterface(new UpdateOfferBridge(),"InhouseUpdateHost");
        updateChecker.setWebViewClient(new UpdateAssetClient(this){
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                if(view==updateChecker){updateChecker=null;updateScheduled=false;}
                view.removeJavascriptInterface("InhouseNative");view.removeJavascriptInterface("InhouseUpdateHost");view.destroy();
                return true;
            }
        });
        updateChecker.loadUrl("https://appassets.androidplatform.net/updates/index.html?inhouse_app=1&quiet=1");
    }
    public class UpdateCheckBridge {@JavascriptInterface public String getAppVersion(){return "0.3.5";}}
    public class UpdateOfferBridge {@JavascriptInterface public void offer(){runOnUiThread(() -> {if(!updateOffered && !isFinishing() && hasWindowFocus()){updateOffered=true;startActivity(new Intent(MainActivity.this,UpdateActivity.class));}});}}
    private String read(String name)throws IOException {
        try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");
        }
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() {
        if(web!=null && web.canGoBack())web.goBack();
        else if(web==directWeb)selectTab(false);
        else super.onBackPressed();
    }
    @Override protected void onPause() {
        paused=true;handler.removeCallbacks(identityWatcher);
        if(web!=null){visibility(web,false);web.onPause();}
        if(updateChecker!=null)updateChecker.onPause();
        super.onPause();CookieManager.getInstance().flush();
    }
    @Override protected void onResume() {
        super.onResume();paused=false;reconcileOwner();
        if(updateChecker!=null)updateChecker.onResume();
        if(web!=null) {
            web.onResume();visibility(web,true);applySystemTheme(true);
            if(web.getUrl()!=null && trusted(Uri.parse(web.getUrl())) && settingsPath(Uri.parse(web.getUrl()).getPath())) {
                boolean notices=((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();
                web.evaluateJavascript("if(window.CALMA_CONFIG){window.CALMA_CONFIG.notificationsEnabled="+notices+";window.CALMA_CONFIG.listenerEnabled="+listenerEnabled()+";}if(window.__calmaSettings)window.__calmaSettings.updateStatus();",null);
            }
            handler.removeCallbacks(identityWatcher);handler.post(identityWatcher);
        }
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null);if(updateChecker!=null){updateChecker.removeJavascriptInterface("InhouseNative");updateChecker.removeJavascriptInterface("InhouseUpdateHost");updateChecker.destroy();}disposeTab(homeWeb);disposeTab(directWeb);super.onDestroy(); }
}
