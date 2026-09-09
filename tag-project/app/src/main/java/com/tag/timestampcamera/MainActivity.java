package com.tag.timestampcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://seashell-giraffe-571836.hostingersite.com/";
    private static final String HOME_HOST = "seashell-giraffe-571836.hostingersite.com";
    private static final int REQ_PERMISSIONS = 1001;

    private WebView webView;
    private PermissionRequest pendingWebPermission;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(2, 6, 23));
        getWindow().setNavigationBarColor(Color.rgb(2, 6, 23));
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(s.getUserAgentString() + " TAGTimestampCamera/1.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNativeBridge();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!isTrustedOrigin(request.getOrigin())) {
                        request.deny();
                        return;
                    }
                    if (hasRuntimePermissions()) {
                        request.grant(request.getResources());
                    } else {
                        pendingWebPermission = request;
                        requestRuntimePermissions();
                    }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (isTrustedOrigin(Uri.parse(origin)) && hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else if (isTrustedOrigin(Uri.parse(origin))) {
                    requestRuntimePermissions();
                    callback.invoke(origin, true, false);
                } else {
                    callback.invoke(origin, false, false);
                }
            }
        });

        requestRuntimePermissions();
        webView.loadUrl(HOME_URL);
    }

    private boolean isTrustedOrigin(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && HOME_HOST.equalsIgnoreCase(uri.getHost());
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return false;
        if (isTrustedOrigin(uri)) return false;

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        try {
            Intent intent;
            if ("intent".equals(scheme)) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRuntimePermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && hasLocationPermission();
    }

    private void requestRuntimePermissions() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA);
        if (!hasLocationPermission()) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS && pendingWebPermission != null) {
            if (hasRuntimePermissions()) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
    }

    private void injectNativeBridge() {
        String js = "(function(){" +
                "if(!window.AndroidBridge||window.__TAG_NATIVE_READY)return;window.__TAG_NATIVE_READY=true;" +
                "function dataUrlFromBlob(blob){return new Promise(function(res,rej){var r=new FileReader();r.onload=function(){res(r.result)};r.onerror=rej;r.readAsDataURL(blob);});}" +
                "try{Object.defineProperty(navigator,'canShare',{configurable:true,value:function(){return true;}});}catch(e){}" +
                "try{Object.defineProperty(navigator,'share',{configurable:true,value:async function(d){d=d||{};var text=d.text||'';var title=d.title||'TAG Timestamp Camera';if(d.files&&d.files.length){var f=d.files[0];var u=await dataUrlFromBlob(f);AndroidBridge.shareBase64(String(u).split(',')[1]||'',f.name||'tag_timestamp.jpg',f.type||'image/jpeg',text,title);}else{AndroidBridge.shareText(text,title);}}});}catch(e){}" +
                "var oldClick=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){var a=this;if(a.download&&a.href&&a.href.indexOf('blob:')===0){fetch(a.href).then(function(b){return b.blob()}).then(function(b){return dataUrlFromBlob(b)}).then(function(u){AndroidBridge.saveBase64(String(u).split(',')[1]||'',a.download||'tag_timestamp.jpg','image/jpeg');}).catch(function(){oldClick.call(a);});return;}return oldClick.call(a);};" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveBase64(String base64, String filename, String mimeType) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    String safeName = (filename == null || filename.trim().isEmpty()) ? "tag_timestamp.jpg" : filename;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Images.Media.DISPLAY_NAME, safeName);
                        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType == null ? "image/jpeg" : mimeType);
                        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TAG Timestamp Camera");
                        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new Exception("Gagal membuat file");
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os == null) throw new Exception("Gagal membuka penyimpanan");
                            os.write(bytes);
                        }
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TAG Timestamp Camera");
                        if (!dir.exists()) dir.mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(new File(dir, safeName))) {
                            fos.write(bytes);
                        }
                    }
                    Toast.makeText(MainActivity.this, "Foto disimpan ke galeri", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareBase64(String base64, String filename, String mimeType, String text, String title) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, (filename == null || filename.isEmpty()) ? "tag_timestamp.jpg" : filename);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(bytes);
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, (title == null || title.isEmpty()) ? "Laporan Kunjungan" : title));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Gagal share: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareText(String text, String title) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                startActivity(Intent.createChooser(send, (title == null || title.isEmpty()) ? "Laporan Kunjungan" : title));
            });
        }
    }
}
