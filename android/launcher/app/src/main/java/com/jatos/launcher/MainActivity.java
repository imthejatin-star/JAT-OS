package com.jatos.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.content.pm.*;
import org.json.*;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(3,7,6);
    private final int GREEN = Color.rgb(0,255,145);
    private final int MUTED = Color.rgb(105,145,125);

    private boolean unlocked = false;
    private boolean confirming = false;
    private String firstPassword = null;

    private EditText passwordInput;
    private TextView message;
    private BroadcastReceiver screenReceiver;
    private WebView webView;

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        applyImmersive();
        registerScreenReceiver();
        showGate();
    }

    private void applyImmersive() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    unlocked = false;
                    confirming = false;
                    firstPassword = null;
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    unlocked = false;
                    showGate();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
        if (!unlocked) showGate();
    }

    private android.graphics.drawable.GradientDrawable shape(
        int color, int stroke, int radius
    ) {
        android.graphics.drawable.GradientDrawable d =
            new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void showGate() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(BG);
        root.setPadding(dp(28), dp(25), dp(28), dp(25));

        TextView brand = text("JAT OS // PRIVATE ENVIRONMENT", 10, GREEN, true);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(35)));

        TextView title = text("JAT", 62, Color.WHITE, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(85)));

        TextView subtitle = text("OPERATING INTERFACE", 10, GREEN, true);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(30)));

        TextView terminal = text(
            "INITIALIZING CORE........ OK\n" +
            "LOADING INTERFACE....... OK\n" +
            "VERIFYING SESSION....... READY\n" +
            "--------------------------------",
            10, MUTED, false
        );
        terminal.setLineSpacing(dp(7), 1f);
        root.addView(terminal, new LinearLayout.LayoutParams(-1, dp(115)));

        SharedPreferences prefs = getSharedPreferences("jat_security", MODE_PRIVATE);
        boolean hasPassword = prefs.contains("password_hash");

        String heading = !hasPassword
            ? (confirming ? "CONFIRM YOUR ACCESS KEY" : "CREATE YOUR ACCESS KEY")
            : "IDENTITY VERIFICATION";

        TextView label = text(heading, 11, Color.WHITE, true);
        root.addView(label, new LinearLayout.LayoutParams(-1, dp(48)));

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setGravity(Gravity.CENTER);
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setHintTextColor(MUTED);
        passwordInput.setHint(!hasPassword
            ? (confirming ? "Re-enter password" : "Create password")
            : "Enter password");
        passwordInput.setTypeface(Typeface.MONOSPACE);
        passwordInput.setInputType(
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        passwordInput.setBackground(shape(0xFF0A1510, 0xFF1D7650, 10));
        root.addView(passwordInput, new LinearLayout.LayoutParams(-1, dp(55)));

        message = text("ACCESS RESTRICTED", 10, MUTED, false);
        root.addView(message, new LinearLayout.LayoutParams(-1, dp(42)));

        Button enter = new Button(this);
        enter.setText(!hasPassword
            ? (confirming ? "CONFIRM ACCESS KEY" : "CREATE ACCESS KEY")
            : "ENTER JAT OS");
        enter.setTextColor(Color.BLACK);
        enter.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        enter.setAllCaps(false);
        enter.setBackground(shape(GREEN, GREEN, 10));
        root.addView(enter, new LinearLayout.LayoutParams(-1, dp(53)));

        enter.setOnClickListener(v -> handlePassword());
        setContentView(root);
    }

    private void handlePassword() {
        String entered = passwordInput.getText().toString();

        if (entered.length() < 6) {
            message.setText("USE AT LEAST 6 CHARACTERS");
            message.setTextColor(0xFFFFB45C);
            return;
        }

        SharedPreferences prefs = getSharedPreferences("jat_security", MODE_PRIVATE);

        if (!prefs.contains("password_hash")) {
            if (!confirming) {
                firstPassword = entered;
                confirming = true;
                showGate();
                return;
            }

            if (!entered.equals(firstPassword)) {
                firstPassword = null;
                confirming = false;
                showGate();
                message.setText("PASSWORDS DID NOT MATCH");
                return;
            }

            savePassword(entered);
            firstPassword = null;
            confirming = false;
            unlockSystem();
            return;
        }

        if (verifyPassword(entered)) {
            unlockSystem();
        } else {
            passwordInput.setText("");
            message.setText("ACCESS DENIED");
            message.setTextColor(0xFFFF6969);
        }
    }

    private void savePassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(password, salt);

            getSharedPreferences("jat_security", MODE_PRIVATE).edit()
                .putString("password_salt",
                    Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("password_hash",
                    Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save password", Toast.LENGTH_LONG).show();
        }
    }

    private boolean verifyPassword(String password) {
        try {
            SharedPreferences p = getSharedPreferences("jat_security", MODE_PRIVATE);
            byte[] salt = Base64.decode(
                p.getString("password_salt", ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(
                p.getString("password_hash", ""), Base64.NO_WRAP);
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 180000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private void unlockSystem() {
        unlocked = true;
        webView = new WebView(this);
        webView.setBackgroundColor(BG);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.addJavascriptInterface(new JatBridge(), "JAT");
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

                List<ResolveInfo> activities =
                    pm.queryIntentActivities(intent, 0);

                Collections.sort(activities, (a, b) ->
                    a.loadLabel(pm).toString().compareToIgnoreCase(
                        b.loadLabel(pm).toString()
                    )
                );

                HashSet<String> seen = new HashSet<>();

                for (ResolveInfo info : activities) {
                    String pkg = info.activityInfo.packageName;
                    String activity = info.activityInfo.name;

                    if (pkg.equals(getPackageName())) continue;

                    String unique = pkg + "/" + activity;
                    if (!seen.add(unique)) continue;

                    JSONObject item = new JSONObject();
                    item.put("name", info.loadLabel(pm).toString());
                    item.put("package", pkg);
                    item.put("activity", activity);
                    item.put("icon", iconToBase64(info.loadIcon(pm)));
                    result.put(item);
                }
            } catch (Exception e) {
                return "[]";
            }

            return result.toString();
        }

        @JavascriptInterface
        public void launchApp(String packageName, String activityName) {
            runOnUiThread(() -> {
                try {
                    Intent launch = new Intent(Intent.ACTION_MAIN);
                    launch.addCategory(Intent.CATEGORY_LAUNCHER);
                    launch.setClassName(packageName, activityName);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "Could not open application", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void openDeviceSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "Settings unavailable", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String getDeviceName() {
            return android.os.Build.MODEL;
        }

        private String iconToBase64(Drawable drawable) {
            try {
                int size = 96;
                Bitmap bitmap = Bitmap.createBitmap(
                    size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, size, size);
                drawable.draw(canvas);

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, output);
                bitmap.recycle();

                return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript(
                "if(window.JAT_BACK){window.JAT_BACK()}else{navigate('home')}", null);
        }
    }

    @Override
    protected void onDestroy() {
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); }
            catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
