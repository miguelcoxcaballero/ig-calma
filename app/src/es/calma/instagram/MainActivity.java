package es.calma.instagram;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences prefs;
    private TextView status;
    private String script;
    private EditText importTarget;
    private static final int IMPORT = 7;
    private final int green = Color.rgb(22,78,70);
    private final int cream = Color.rgb(247,245,239);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("calma", MODE_PRIVATE);
        try { script = read(getAssets().open("filter.js"), 300000); }
        catch (Exception e) { throw new IllegalStateException(e); }
        getWindow().setStatusBarColor(green);
        getWindow().setNavigationBarColor(green);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(cream);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout bar = new LinearLayout(this); bar.setPadding(dp(8),dp(4),dp(8),dp(4));
        bar.setGravity(Gravity.CENTER_VERTICAL); bar.setBackgroundColor(green);
        TextView title = new TextView(this); title.setText("IG Calma"); title.setTextSize(20); title.setTextColor(Color.WHITE);
        bar.addView(title, new LinearLayout.LayoutParams(0,dp(48),1)); title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(button("Inicio", v -> home()));
        bar.addView(button("Ajustes", v -> settings())); root.addView(bar);
        status = new TextView(this); status.setTextSize(12); status.setTextColor(green); status.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.addView(status);
        web = new WebView(this); root.addView(web, new LinearLayout.LayoutParams(-1,0,1));
        WebSettings ws = web.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false); ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(true); ws.setSupportMultipleWindows(false);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!trusted(uri)) { toast("Este enlace sale de Instagram. Ábrelo desde tu navegador si lo necesitas."); return true; }
                if (prefs.getBoolean("reels",true) && reelPath(uri.getPath())) { toast("Reels desactivados"); return true; }
                return false;
            }
            @Override public void onPageFinished(WebView v, String url) { inject(); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) status.setText("No se pudo cargar Instagram. Comprueba la conexión y pulsa Inicio.");
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        setContentView(root); refreshStatus();
        if (!prefs.getBoolean("intro",false)) {
            new AlertDialog.Builder(this).setTitle("Tu Instagram, con límites")
                .setMessage("Cliente independiente de Instagram web, experimental. Los filtros pueden necesitar ajustes si cambia la web.\n\nAñade o importa las cuentas que sigues; la lista de amigos se configura por separado. El feed termina tras el número de publicaciones que elijas.\n\nEl inicio de sesión ocurre en instagram.com. Esta app no lee tus contraseñas ni envía tus listas a ningún servidor. Instagram podría limitar el acceso desde WebView.")
                .setPositiveButton("Configurar", (d,w) -> { prefs.edit().putBoolean("intro",true).apply(); home(); settings(); }).setCancelable(false).show();
        } else home();
    }
    private boolean trusted(Uri uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null && (host.equals("instagram.com") || host.endsWith(".instagram.com"));
    }
    private boolean reelPath(String path) { return path != null && (path.matches("^/reels?(/.*)?$") || path.startsWith("/reels/")); }
    private void home() { web.loadUrl("https://www.instagram.com/"); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button button(String label, View.OnClickListener click) { Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setOnClickListener(click); return b; }
    private TextView label(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextColor(green); t.setTextSize(size); t.setPadding(0,dp(12),0,dp(5)); return t; }
    private EditText input(String value, String hint) { EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setTextSize(14); return e; }
    private JSONArray accounts(String value) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : value.toLowerCase(Locale.ROOT).split("[\\s,;]+")) {
            String name = raw.startsWith("@") ? raw.substring(1) : raw;
            if (name.matches("[a-z0-9._]{1,30}")) names.add(name);
        }
        return new JSONArray(names);
    }
    private void settings() {
        ScrollView scroll = new ScrollView(this); LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),dp(20)); scroll.addView(box);
        box.addView(label("Elige qué entra en tu feed",20));
        RadioGroup group = new RadioGroup(this);
        String[] modes = {"Todas las cuentas", "Solo cuentas que sigo (lista local)", "Solo mis amigos (lista local)"};
        for (int i=0;i<3;i++) { RadioButton radio = new RadioButton(this); radio.setId(100+i); radio.setText(modes[i]); group.addView(radio); }
        group.check(100+prefs.getInt("mode",1)); box.addView(group);
        box.addView(label("Cuentas que sigo",16));
        final EditText following = input(prefs.getString("following",""),"@usuario1, @usuario2…"); following.setMinLines(2); box.addView(following);
        box.addView(button("Importar following.json de Instagram", v -> {
            importTarget = following;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, IMPORT);
        }));
        box.addView(label("En Instagram: Centro de cuentas → Tu información y permisos → Descargar o exportar tu información. Elige seguidores y seguidos, formato JSON. Descomprime la descarga y selecciona following.json. También puedes escribir los usuarios aquí. Esta lista no se actualiza automáticamente.",12));
        box.addView(label("Mis amigos",16));
        final EditText friends = input(prefs.getString("friends",""),"@amiga, @amigo…"); friends.setMinLines(2); box.addView(friends);
        box.addView(label("Tú eliges esta lista; no se sincroniza con Mejores amigos ni detecta quién te sigue de vuelta. En los modos de lista, las publicaciones cuyo autor no se pueda identificar se ocultan. El filtro de cuentas se aplica al inicio; los perfiles y mensajes siguen accesibles.",12));
        Switch reels = new Switch(this); reels.setText("Desactivar Reels"); reels.setChecked(prefs.getBoolean("reels",true)); reels.setPadding(0,dp(18),0,dp(12)); box.addView(reels);
        box.addView(label("Publicaciones por sesión",16));
        EditText limit = input(String.valueOf(prefs.getInt("limit",20)),"20"); limit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); box.addView(limit);
        box.addView(label("Entre 1 y 100. Al llegar al límite, el feed se corta. Pulsa Nueva sesión para empezar otra tanda. Es un límite local por sesión, no una marca de publicaciones ya vistas.",12));
        box.addView(button("Nueva sesión", v -> { web.evaluateJavascript("if(window.__calma)window.__calma.destroy();",null); home(); toast("Nueva sesión iniciada"); }));
        box.addView(button("Cerrar sesión y borrar datos web", v -> {
            new AlertDialog.Builder(this).setTitle("¿Cerrar sesión?").setMessage("Se borrarán las cookies y los datos web de IG Calma. Tus ajustes y listas se conservarán.")
                .setPositiveButton("Cerrar sesión",(d,w) -> { CookieManager.getInstance().removeAllCookies(ok -> { CookieManager.getInstance().flush(); WebStorage.getInstance().deleteAllData(); web.clearCache(true); web.clearHistory(); home(); }); })
                .setNegativeButton("Cancelar",null).show();
        }));
        new AlertDialog.Builder(this).setTitle("Ajustes de IG Calma").setView(scroll).setPositiveButton("Guardar",(d,w) -> {
            int n=20; try { n=Integer.parseInt(limit.getText().toString()); } catch (Exception ignored) {}
            n=Math.max(1,Math.min(100,n));
            prefs.edit().putInt("mode",group.getCheckedRadioButtonId()-100).putInt("limit",n)
                .putBoolean("reels",reels.isChecked()).putString("following",join(accounts(following.getText().toString())))
                .putString("friends",join(accounts(friends.getText().toString()))).apply();
            refreshStatus(); home();
        }).setNegativeButton("Cancelar",null).show();
    }
    private String join(JSONArray array) { StringBuilder b = new StringBuilder(); for(int i=0;i<array.length();i++) { if(i>0)b.append(", "); b.append(array.optString(i)); } return b.toString(); }
    private void refreshStatus() { int mode=prefs.getInt("mode",1); status.setText((mode==0?"Todas las cuentas":mode==1?"Solo seguidos · lista local":"Solo amigos · lista local")+" · "+prefs.getInt("limit",20)+" publicaciones"+(prefs.getBoolean("reels",true)?" · Sin Reels":"")); }
    private void inject() {
        String url=web.getUrl(); if(url==null || !trusted(Uri.parse(url)))return;
        try {
            JSONObject config=new JSONObject(); config.put("mode",prefs.getInt("mode",1)); config.put("limit",prefs.getInt("limit",20)); config.put("reels",prefs.getBoolean("reels",true));
            config.put("following",accounts(prefs.getString("following",""))); config.put("friends",accounts(prefs.getString("friends","")));
            web.evaluateJavascript("window.CALMA_CONFIG="+config.toString()+";\n"+script,null);
        } catch(JSONException e) { toast("No se pudo aplicar la configuración"); }
    }
    private String read(InputStream stream,int max) throws IOException {
        try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1) { if(out.size()+n>max)throw new IOException("Archivo demasiado grande"); out.write(buffer,0,n); }
            return out.toString("UTF-8");
        }
    }
    private void parseFollowing(Object obj,LinkedHashSet<String> result) throws JSONException {
        if(obj instanceof JSONArray) { JSONArray a=(JSONArray)obj; for(int i=0;i<a.length();i++)parseFollowing(a.get(i),result); }
        else if(obj instanceof JSONObject) {
            JSONObject o=(JSONObject)obj;
            if(o.has("relationships_following"))parseFollowing(o.get("relationships_following"),result);
            if(o.has("string_list_data")) {
                JSONArray a=o.getJSONArray("string_list_data");
                for(int i=0;i<a.length();i++) {
                    JSONObject item=a.getJSONObject(i); String value=item.optString("value","");
                    if(value.isEmpty()) { Uri uri=Uri.parse(item.optString("href","")); value=uri.getLastPathSegment(); if(value==null)value=""; }
                    JSONArray clean=accounts(value); for(int j=0;j<clean.length();j++)result.add(clean.getString(j));
                }
            }
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==IMPORT && result==RESULT_OK && data!=null && data.getData()!=null) {
            try {
                Object json=new JSONTokener(read(getContentResolver().openInputStream(data.getData()),8*1024*1024)).nextValue();
                LinkedHashSet<String> names=new LinkedHashSet<>(); parseFollowing(json,names);
                if(names.isEmpty()) { toast("No se encontraron usuarios. Elige el archivo following.json."); return; }
                if(importTarget!=null)importTarget.setText(join(new JSONArray(names)));
                toast(names.size()+" cuentas importadas. Pulsa Guardar.");
            } catch(Exception e) { toast("No se pudo leer el JSON de cuentas seguidas (máximo 8 MB)."); }
        }
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if(web.canGoBack())web.goBack(); else super.onBackPressed(); }
    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); if(web!=null)web.onResume(); }
    @Override protected void onDestroy() { if(web!=null)web.destroy(); super.onDestroy(); }
}
