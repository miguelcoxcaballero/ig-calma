package es.calma.instagram;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.graphics.Color;
import android.content.res.Configuration;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Native updater implementation copied from Inhouse Read; see vendor/UPSTREAM.md. */
public class UpdateActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web=new WebView(this);
        boolean dark=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        web.setBackgroundColor(dark?Color.BLACK:Color.WHITE);
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setUserAgentString(web.getSettings().getUserAgentString()+" InhouseReadApp/0.3.2");
        web.addJavascriptInterface(new InhouseNativeBridge(),"InhouseNative");
        web.setWebViewClient(new UpdateAssetClient(this));
        setContentView(web);
        web.loadUrl("https://appassets.androidplatform.net/updates/index.html?inhouse_app=1");
    }
    public class InhouseNativeBridge {
        private volatile boolean updateDownloadRunning=false;
        @JavascriptInterface public String getAppVersion(){return "0.3.2";}
        // Descarga el APK indicado (validado por src/js/android-update.js
        // contra una lista blanca de hosts antes de llegar aqui) y lanza el
        // instalador del sistema. Puerto de installAppUpdate() de
        // inhousenotes/MainActivity.java, sin el resto de su bridge de PDF.
        // expectedSha256Hex: vacio/nulo para saltarse la verificacion
        // (compatibilidad hacia atras); si viene relleno, un hash que no
        // coincide aborta la instalacion en vez de arriesgarse a instalar un
        // APK corrupto o manipulado en transito.
        @JavascriptInterface
        public void installAppUpdate(String url, String expectedSha256Hex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !getPackageManager().canRequestPackageInstalls()) {
                notifyAppUpdateResult("permission_required", "Allow Instagram Calma to install updates");
                runOnUiThread(() -> startActivity(new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())
                )));
                return;
            }
            if (updateDownloadRunning) {
                notifyAppUpdateResult("downloading", "The update is already downloading");
                return;
            }
            updateDownloadRunning = true;
            notifyAppUpdateResult("downloading", "Downloading update");
            new Thread(() -> {
                HttpURLConnection connection = null;
                try {
                    Uri parsed = Uri.parse(url);
                    String host = parsed.getHost();
                    boolean allowedHost = "github.com".equalsIgnoreCase(host)
                        || "raw.githubusercontent.com".equalsIgnoreCase(host)
                        || "miguelcoxcaballero.github.io".equalsIgnoreCase(host);
                    if (!"https".equalsIgnoreCase(parsed.getScheme()) || !allowedHost) {
                        throw new Exception("Update URL is not allowed");
                    }
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(45000);
                    connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
                    int responseCode = connection.getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) {
                        throw new Exception("Update download failed (" + responseCode + ")");
                    }
                    long expectedBytes = connection.getContentLengthLong();
                    notifyAppUpdateProgress(0, expectedBytes);
                    File updateDir = new File(getCacheDir(), "updates");
                    if (!updateDir.exists() && !updateDir.mkdirs()) {
                        throw new Exception("Could not prepare update storage");
                    }
                    File apkFile = new File(updateDir, "ig-calma-update.apk");
                    long totalBytes = 0;
                    long lastProgressAt = 0;
                    int lastProgressPercent = -1;
                    try (InputStream input = new BufferedInputStream(connection.getInputStream());
                         FileOutputStream output = new FileOutputStream(apkFile)) {
                        byte[] buffer = new byte[32768];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                            totalBytes += read;
                            long now = System.currentTimeMillis();
                            int progressPercent = expectedBytes > 0
                                ? (int) Math.min(99, (totalBytes * 100L) / expectedBytes)
                                : 0;
                            if ((expectedBytes <= 0 && now - lastProgressAt >= 120)
                                    || (progressPercent != lastProgressPercent && now - lastProgressAt >= 120)
                                    || (expectedBytes > 0 && totalBytes >= expectedBytes)) {
                                notifyAppUpdateProgress(totalBytes, expectedBytes);
                                lastProgressAt = now;
                                lastProgressPercent = progressPercent;
                            }
                        }
                    }
                    if (totalBytes < 100000) throw new Exception("Downloaded update is incomplete");
                    if (expectedSha256Hex != null && !expectedSha256Hex.isEmpty()) {
                        String actualSha256Hex = sha256Hex(apkFile);
                        if (!actualSha256Hex.equalsIgnoreCase(expectedSha256Hex)) {
                            apkFile.delete();
                            throw new Exception("Downloaded update failed integrity check");
                        }
                    }
                    Uri apkUri = FileProvider.getUriForFile(
                        UpdateActivity.this,
                        getPackageName() + ".fileprovider",
                        apkFile
                    );
                    notifyAppUpdateResult("ready", "Update downloaded", totalBytes,
                        expectedBytes > 0 ? expectedBytes : totalBytes, 100);
                    runOnUiThread(() -> {
                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(installIntent);
                    });
                } catch (Exception error) {
                    error.printStackTrace();
                    notifyAppUpdateResult("error", error.getMessage());
                } finally {
                    updateDownloadRunning = false;
                    if (connection != null) connection.disconnect();
                }
            }, "InhouseCalmaUpdate").start();
        }

        private String sha256Hex(File file) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        }

        private void notifyAppUpdateResult(String status, String message) {
            notifyAppUpdateResult(status, message, -1, -1, -1);
        }

        private void notifyAppUpdateProgress(long downloadedBytes, long totalBytes) {
            int percent = totalBytes > 0
                ? (int) Math.min(100, (downloadedBytes * 100L) / totalBytes)
                : -1;
            notifyAppUpdateResult("downloading", "Downloading update", downloadedBytes, totalBytes, percent);
        }

        private void notifyAppUpdateResult(String status, String message,
                long downloadedBytes, long totalBytes, int percent) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("status", status);
                payload.put("message", message == null ? "" : message);
                if (downloadedBytes >= 0) payload.put("downloadedBytes", downloadedBytes);
                if (totalBytes > 0) payload.put("totalBytes", totalBytes);
                if (percent >= 0) payload.put("percent", percent);
            } catch (Exception ignored) {}
            WebView webView = web;
            webView.post(() -> webView.evaluateJavascript(
                "window.handleInhouseUpdateResult && window.handleInhouseUpdateResult(" + payload.toString() + ");",
                null
            ));
        }
    }
    @Override public void onBackPressed(){finish();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("InhouseNative");web.destroy();}super.onDestroy();}
}
