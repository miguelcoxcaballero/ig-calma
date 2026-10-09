package es.calma.instagram;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
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
    private WebView web;
    private SharedPreferences prefs;
    private String filters, relations, settingsPage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable identityWatcher = new Runnable() {
        @Override public void run() {
            if (web != null && trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) {
                web.evaluateJavascript("if(window.__calmaRelationsController)window.__calmaRelationsController.refreshIdentity(" + JSONObject.quote(owner()) + ");", null);
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("calma", MODE_PRIVATE);
        try { filters = read("filter.js"); relations = read("relations.js"); settingsPage = read("settings.js"); }
        catch (IOException e) { throw new IllegalStateException(e); }
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        WebSettings ws=web.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false); ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(true); ws.setSupportMultipleWindows(false);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request) {
                Uri uri=request.getUrl();
                if(request.isForMainFrame() && handleSettingsAction(uri))return true;
                if(!trusted(uri)) { toast("Abre este enlace externo desde tu navegador."); return true; }
                if(prefs.getBoolean("reels",true) && reelPath(uri.getPath())) { toast("Reels desactivados"); return true; }
                return false;
            }
            @Override public void onPageFinished(WebView v,String url) { inject(); }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) {
                if(r.isForMainFrame())toast("No se pudo cargar Instagram. Comprueba tu conexión y vuelve a abrir la app.");
            }
        });
        web.setWebChromeClient(new WebChromeClient()); setContentView(root);
        openInitial();
    }
    private void openInitial() { web.loadUrl(getIntent().getBooleanExtra("open_dm",false)?"https://www.instagram.com/direct/inbox/":"https://www.instagram.com/"); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if(intent.getBooleanExtra("open_dm",false))web.loadUrl("https://www.instagram.com/direct/inbox/"); }
    private boolean trusted(Uri uri) { String host=uri.getHost(); return "https".equalsIgnoreCase(uri.getScheme()) && host!=null && (host.equals("instagram.com") || host.endsWith(".instagram.com")); }
    private boolean reelPath(String path) { return path!=null && path.matches("^/reels?(/.*)?$"); }
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
        return path!=null && (path.equals("/accounts/edit/") || path.equals("/accounts/edit") || path.equals("/accounts/settings/") || path.equals("/settings/") || path.equals("/settings"));
    }
    private boolean handleSettingsAction(Uri uri) {
        if(!trusted(uri) || !settingsPath(uri.getPath()) || uri.getQueryParameter("calma_action")==null)return false;
        String current=web.getUrl();
        if(current==null || !trusted(Uri.parse(current)) || !settingsPath(Uri.parse(current).getPath()))return true;
        String action=uri.getQueryParameter("calma_action");
        if("new_session".equals(action)) { web.loadUrl("https://www.instagram.com/"); return true; }
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
    private void inject() {
        if(web.getUrl()==null || !trusted(Uri.parse(web.getUrl())))return;
        try {
            JSONObject config=new JSONObject(); config.put("mode",prefs.getInt("mode",1)); config.put("limit",prefs.getInt("limit",20)); config.put("reels",prefs.getBoolean("reels",true)); config.put("owner",owner()); config.put("dmMirror",prefs.getBoolean("dm_mirror",false));
            config.put("notificationsEnabled",((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()); config.put("listenerEnabled",listenerEnabled());
            web.evaluateJavascript("window.CALMA_CONFIG="+config+";\n"+relations+"\n"+filters+"\n"+settingsPage,null);
        }catch(JSONException e){toast("No se pudo aplicar la configuración");}
    }
    private String read(String name)throws IOException {
        try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");
        }
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if(web.canGoBack())web.goBack();else super.onBackPressed(); }
    @Override protected void onPause() { handler.removeCallbacks(identityWatcher);super.onPause();web.onPause();CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume();if(web!=null){web.onResume();if(web.getUrl()!=null && settingsPath(Uri.parse(web.getUrl()).getPath()))inject();handler.removeCallbacks(identityWatcher);handler.post(identityWatcher);} }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null);if(web!=null)web.destroy();super.onDestroy(); }
}
