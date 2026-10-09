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
import android.widget.*;
import org.json.*;
import java.io.*;

public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences prefs;
    private String filters, relations;
    private TextView syncStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable identityWatcher = new Runnable() {
        @Override public void run() {
            if (web != null && trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) {
                web.evaluateJavascript("if(window.__calmaRelationsController)window.__calmaRelationsController.refreshIdentity(" + JSONObject.quote(owner()) + ");", null);
                updateSyncStatus();
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("calma", MODE_PRIVATE);
        try { filters = read("filter.js"); relations = read("relations.js"); }
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
        // No wrapper toolbar. A small extra settings control sits above the web navigation.
        TextView gear = new TextView(this); gear.setText("⚙"); gear.setTextSize(24);
        gear.setGravity(Gravity.CENTER); gear.setTextColor(Color.rgb(80,80,80));
        gear.setBackgroundColor(Color.argb(215,255,255,255));
        gear.setContentDescription("Ajustes adicionales de Instagram Calma"); gear.setOnClickListener(v -> settings());
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.END | Gravity.BOTTOM);
        gp.rightMargin=dp(8); gp.bottomMargin=dp(68); root.addView(gear,gp);
        WebSettings ws=web.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false); ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(true); ws.setSupportMultipleWindows(false);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request) {
                Uri uri=request.getUrl();
                if(!trusted(uri)) { toast("Abre este enlace externo desde tu navegador."); return true; }
                if(prefs.getBoolean("reels",true) && reelPath(uri.getPath())) { toast("Reels desactivados"); return true; }
                return false;
            }
            @Override public void onPageFinished(WebView v,String url) { inject(); }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) {
                if(r.isForMainFrame())toast("No se pudo cargar Instagram. Abre los ajustes adicionales y pulsa Inicio.");
            }
        });
        web.setWebChromeClient(new WebChromeClient()); setContentView(root);
        if(!prefs.getBoolean("intro_v2",false)) {
            new AlertDialog.Builder(this).setTitle("Instagram con tus ajustes")
                .setMessage("Cliente independiente basado en Instagram web. Inicia sesión normalmente: se intentará sincronizar a quién sigues y quién te sigue. Amigos significa seguimiento mutuo. Instagram puede bloquear estas consultas.\n\nPara los avisos de DM en segundo plano, mantén Instagram oficial instalado y activa la integración desde los ajustes ⚙. Los filtros se aplican a esta app.")
                .setPositiveButton("Continuar",(d,w) -> { prefs.edit().putBoolean("intro_v2",true).apply(); openInitial(); }).setCancelable(false).show();
        } else openInitial();
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
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView label(String value,int size) { TextView t=new TextView(this); t.setText(value); t.setTextColor(Color.rgb(38,38,38)); t.setTextSize(size); t.setPadding(0,dp(12),0,dp(6)); return t; }
    private Button button(String text,View.OnClickListener click) { Button b=new Button(this); b.setText(text); b.setOnClickListener(click); return b; }
    private void settings() {
        ScrollView scroll=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),dp(20)); scroll.addView(box);
        box.addView(label("Contenido del inicio",18));
        RadioGroup group=new RadioGroup(this);
        String[] modes={"Todas las cuentas","Solo cuentas que sigo","Solo amigos (seguimiento mutuo)"};
        for(int i=0;i<3;i++) { RadioButton r=new RadioButton(this); r.setId(100+i); r.setText(modes[i]); group.addView(r); }
        group.check(100+prefs.getInt("mode",1)); box.addView(group);
        syncStatus=label("Comprobando la sincronización…",13); box.addView(syncStatus); updateSyncStatus();
        box.addView(button("Sincronizar ahora",v -> { web.evaluateJavascript("if(window.__calmaRelationsController)window.__calmaRelationsController.sync();",null); handler.postDelayed(() -> updateSyncStatus(),250); }));
        box.addView(label("Se consulta tu sesión, sin introducir usuarios. Amigos = tú les sigues y ellos te siguen. Actualización automática cada 24 horas al usar la app; puedes actualizar ahora. Si Instagram rechaza una consulta, no se repite automáticamente en esa página.",12));
        Switch reels=new Switch(this); reels.setText("Desactivar Reels"); reels.setChecked(prefs.getBoolean("reels",true)); reels.setPadding(0,dp(18),0,dp(12)); box.addView(reels);
        box.addView(label("Publicaciones por sesión (1–100)",16));
        EditText limit=new EditText(this); limit.setText(String.valueOf(prefs.getInt("limit",20))); limit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); box.addView(limit);
        box.addView(label("El feed termina al alcanzar el límite. Inicio y Nueva sesión comienzan otra tanda.",12));
        box.addView(label("Notificaciones de mensajes",18));
        Switch dm=new Switch(this); dm.setText("Avisos de DM desde Instagram oficial"); dm.setChecked(prefs.getBoolean("dm_mirror",false)); box.addView(dm);
        box.addView(label("Mantén Instagram oficial instalado, con su misma cuenta y sus avisos de mensajes activados. Esta integración copia sus avisos reconocidos como DM y abre tu bandeja aquí; no tiene un servicio push propio. Puede haber dos avisos. Los filtros de contenido no cambian la app oficial. Android concede acceso general a notificaciones; el código solo procesa las de Instagram oficial y descarta las demás.",12));
        box.addView(button("Activar permisos de notificaciones",v -> {
            prefs.edit().putBoolean("dm_mirror",true).apply(); dm.setChecked(true);
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},33);
            else notificationAccess();
        }));
        box.addView(button("Inicio / Nueva sesión",v -> web.loadUrl("https://www.instagram.com/")));
        box.addView(button("Cerrar sesión y borrar datos web",v -> new AlertDialog.Builder(this).setTitle("¿Cerrar sesión?")
            .setMessage("Borra cookies, relaciones sincronizadas y datos web de esta app. Instagram oficial conserva su propia sesión.")
            .setPositiveButton("Cerrar sesión",(d,w) -> CookieManager.getInstance().removeAllCookies(ok -> {
                CookieManager.getInstance().flush(); WebStorage.getInstance().deleteAllData(); web.clearCache(true); web.clearHistory(); web.loadUrl("https://www.instagram.com/");
            })).setNegativeButton("Cancelar",null).show()));
        box.addView(label("Instagram Calma 0.2.0 · Cliente independiente experimental. Conserva la interfaz web de Instagram; no equivale a su app nativa. Los filtros y la sincronización pueden cambiar de comportamiento si Instagram cambia su web.",12));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Ajustes adicionales").setView(scroll)
            .setPositiveButton("Guardar",(d,w) -> {
                int n=20; try {n=Integer.parseInt(limit.getText().toString());}catch(Exception ignored){}
                prefs.edit().putInt("mode",group.getCheckedRadioButtonId()-100).putInt("limit",Math.max(1,Math.min(100,n)))
                    .putBoolean("reels",reels.isChecked()).putBoolean("dm_mirror",dm.isChecked()).remove("following").remove("friends").apply();
                if(!dm.isChecked())((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll();
                inject();
            }).setNegativeButton("Cancelar",null).create();
        dialog.setOnDismissListener(d -> syncStatus=null); dialog.show();
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
    private void updateSyncStatus() {
        if(syncStatus==null)return;
        web.evaluateJavascript("JSON.stringify(window.__calmaRelations ? {phase:window.__calmaRelations.phase,error:window.__calmaRelations.error,following:window.__calmaRelations.following.length,friends:window.__calmaRelations.friends.length} : {})",value -> {
            if(syncStatus==null)return;
            try {
                Object raw=new JSONTokener(value).nextValue(); JSONObject data=new JSONObject(String.valueOf(raw));
                String phase=data.optString("phase","waiting");
                syncStatus.setText("ready".equals(phase)?data.optInt("following")+" seguidos · "+data.optInt("friends")+" amigos mutuos":
                    "error".equals(phase)?data.optString("error"):"syncing".equals(phase)?"Sincronizando seguidos y seguidores…":"Inicia sesión para sincronizar tu cuenta.");
            }catch(Exception e){syncStatus.setText("Inicia sesión para sincronizar tu cuenta.");}
        });
    }
    private void inject() {
        if(web.getUrl()==null || !trusted(Uri.parse(web.getUrl())))return;
        try {
            JSONObject config=new JSONObject(); config.put("mode",prefs.getInt("mode",1)); config.put("limit",prefs.getInt("limit",20)); config.put("reels",prefs.getBoolean("reels",true)); config.put("owner",owner());
            web.evaluateJavascript("window.CALMA_CONFIG="+config+";\n"+relations+"\n"+filters,null);
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
    @Override protected void onResume() { super.onResume();if(web!=null){web.onResume();handler.removeCallbacks(identityWatcher);handler.post(identityWatcher);} }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null);if(web!=null)web.destroy();super.onDestroy(); }
}
