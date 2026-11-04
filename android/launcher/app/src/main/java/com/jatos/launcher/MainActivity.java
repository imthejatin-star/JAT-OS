package com.jatos.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.rgb(5, 8, 12));
        window.setNavigationBarColor(android.graphics.Color.rgb(5, 8, 12));
        window.getDecorView().setSystemUiVisibility(0);

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(5, 8, 12));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);

        webView.addJavascriptInterface(new JatBridge(), "JAT");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());

        setContentView(webView);
        webView.loadUrl("file:///android_asset/jat-os/index.html");
    }

    public class JatBridge {
        @JavascriptInterface
        public String getApps() {
            JSONArray result = new JSONArray();

            try {
                PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

                Collections.sort(apps, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        return a.loadLabel(pm).toString().compareToIgnoreCase(
                            b.loadLabel(pm).toString()
                        );
                    }
                });

                for (ResolveInfo info : apps) {
                    try {
                        String packageName = info.activityInfo.packageName;
                        String activityName = info.activityInfo.name;
                        String label = info.loadLabel(pm).toString();

                        Drawable drawable = info.loadIcon(pm);
                        int width = Math.max(1, drawable.getIntrinsicWidth());
                        int height = Math.max(1, drawable.getIntrinsicHeight());

                        width = Math.min(width, 192);
                        height = Math.min(height, 192);

                        Bitmap bitmap = Bitmap.createBitmap(
                            width, height, Bitmap.Config.ARGB_8888
                        );

                        Canvas canvas = new Canvas(bitmap);
                        drawable.setBounds(0, 0, width, height);
                        drawable.draw(canvas);

                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
                        String icon = Base64.encodeToString(
                            stream.toByteArray(), Base64.NO_WRAP
                        );

                        JSONObject app = new JSONObject();
                        app.put("name", label);
                        app.put("package", packageName);
                        app.put("activity", activityName);
                        app.put("icon", "data:image/png;base64," + icon);
                        result.put(app);

                        bitmap.recycle();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            return result.toString();
        }

        @JavascriptInterface
        public boolean launchApp(String packageName, String activityName) {
            try {
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setClassName(packageName, activityName);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return true;
            } catch (Exception e) {
                try {
                    Intent fallback = getPackageManager()
                        .getLaunchIntentForPackage(packageName);
                    if (fallback != null) {
                        startActivity(fallback);
                        return true;
                    }
                } catch (Exception ignored) {}
                return false;
            }
        }

        @JavascriptInterface
        public void openDeviceSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void openAppInfo(String packageName) {
            try {
                Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String getDeviceName() {
            return android.os.Build.MODEL;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript(
                "if(window.goBackScreen){goBackScreen();}", null
            );
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
