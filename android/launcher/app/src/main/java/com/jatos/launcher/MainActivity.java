package com.jatos.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.net.Uri;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {

    private WebView webView;
    private LinearLayout gateRoot;
    private EditText passwordInput;
    private EditText confirmInput;
    private TextView terminalOutput;
    private Button accessButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean unlocked = false;
    private boolean receiverRegistered = false;
    private boolean gateVisible = false;
    private int animationStep = 0;

    private SharedPreferences security;

    private final String[] terminalLines = {
        "INITIALIZING JAT SECURITY CORE...",
        "CHECKING LOCAL ENVIRONMENT...",
        "ENCRYPTED SESSION CHANNEL READY",
        "VERIFYING SYSTEM INTEGRITY...",
        "ACCESS CONTROL ACTIVE",
        "WAITING FOR AUTHENTICATION...",
        "LOCAL SECURITY MODULE ONLINE",
        "JAT OS // PRIVATE SESSION"
    };

    private final Runnable terminalAnimation = new Runnable() {
        @Override
        public void run() {
            if (!gateVisible || terminalOutput == null) return;

            StringBuilder output = new StringBuilder();
            int start = Math.max(0, animationStep - 4);

            for (int i = start; i <= animationStep; i++) {
                output.append("> ")
                      .append(terminalLines[i % terminalLines.length])
                      .append("\n");
            }

            output.append("\n");
            output.append("SYSTEM // ")
                  .append(String.format("%04d", animationStep % 10000));

            terminalOutput.setText(output.toString());
            animationStep++;
            handler.postDelayed(this, 1100);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                unlocked = false;
                gateVisible = false;
                handler.removeCallbacks(terminalAnimation);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                unlocked = false;
                showGate();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        security = getSharedPreferences("jat_security", MODE_PRIVATE);

        createWebView();
        registerScreenReceiver();
        showGate();
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF05080C);

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

        webView.loadUrl("file:///android_asset/jat-os/index.html");
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }

        receiverRegistered = true;
    }

    private GradientDrawable background(int color, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void showGate() {
        if (isFinishing()) return;

        unlocked = false;
        gateVisible = true;

        handler.removeCallbacks(terminalAnimation);

        Window window = getWindow();
        window.setStatusBarColor(0xFF030609);
        window.setNavigationBarColor(0xFF030609);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(27), dp(25), dp(27), dp(25));
        root.setBackgroundColor(0xFF030609);
        gateRoot = root;

        TextView logo = text("JAT // OS", 27, 0xFF65F5B8);
        logo.setTypeface(android.graphics.Typeface.MONOSPACE, 1);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(
            -1, dp(60)
        ));

        TextView subtitle = text("PRIVATE SYSTEM ACCESS", 10, 0xFF78948B);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(0.22f);
        root.addView(subtitle);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(28)));

        LinearLayout terminal = new LinearLayout(this);
        terminal.setOrientation(LinearLayout.VERTICAL);
        terminal.setPadding(dp(16), dp(15), dp(16), dp(15));
        terminal.setBackground(background(0xFF07100F, 0xFF174B3B));

        TextView terminalTitle = text(
            "SECURITY TERMINAL  /  LIVE",
            9, 0xFF55CFA0
        );
        terminalTitle.setTypeface(android.graphics.Typeface.MONOSPACE, 1);
        terminal.addView(terminalTitle);

        terminalOutput = text("", 11, 0xFF65DFAE);
        terminalOutput.setTypeface(android.graphics.Typeface.MONOSPACE);
        terminalOutput.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams terminalTextParams =
            new LinearLayout.LayoutParams(-1, dp(125));
        terminalTextParams.topMargin = dp(13);
        terminal.addView(terminalOutput, terminalTextParams);

        root.addView(terminal, new LinearLayout.LayoutParams(
            -1, dp(190)
        ));

        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, dp(30)));

        boolean setup = !security.contains("hash");

        TextView accessTitle = text(
            setup ? "CREATE ACCESS PASSWORD" : "AUTHENTICATION REQUIRED",
            13, 0xFFE3F1EB
        );
        accessTitle.setGravity(Gravity.CENTER);
        accessTitle.setTypeface(android.graphics.Typeface.MONOSPACE, 1);
        root.addView(accessTitle);

        TextView accessSub = text(
            setup ? "Set a password for your private interface." :
                    "Enter your password to continue.",
            10, 0xFF82958E
        );
        accessSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams =
            new LinearLayout.LayoutParams(-1, dp(34));
        root.addView(accessSub, subParams);

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setTextColor(0xFFE6F5EE);
        passwordInput.setHintTextColor(0xFF63756D);
        passwordInput.setHint(setup ? "Create password" : "Enter password");
        passwordInput.setTextSize(13);
        passwordInput.setInputType(
            129 | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        passwordInput.setPadding(dp(15), 0, dp(15), 0);
        passwordInput.setBackground(background(0xFF0B1513, 0xFF235744));

        LinearLayout.LayoutParams inputParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        inputParams.topMargin = dp(17);
        root.addView(passwordInput, inputParams);

        confirmInput = new EditText(this);
        confirmInput.setSingleLine(true);
        confirmInput.setTextColor(0xFFE6F5EE);
        confirmInput.setHintTextColor(0xFF63756D);
        confirmInput.setHint("Confirm password");
        confirmInput.setTextSize(13);
        confirmInput.setInputType(
            129 | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        confirmInput.setPadding(dp(15), 0, dp(15), 0);
        confirmInput.setBackground(background(0xFF0B1513, 0xFF235744));

        LinearLayout.LayoutParams confirmParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        confirmParams.topMargin = dp(10);

        if (setup) {
            root.addView(confirmInput, confirmParams);
        }

        accessButton = new Button(this);
        accessButton.setText(setup ? "INITIALIZE ACCESS" : "UNLOCK SYSTEM");
        accessButton.setTextColor(0xFF03100B);
        accessButton.setTextSize(11);
        accessButton.setLetterSpacing(0.08f);
        accessButton.setAllCaps(false);
        accessButton.setBackground(background(0xFF65EAB2, 0xFF65EAB2));

        LinearLayout.LayoutParams buttonParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(17);
        root.addView(accessButton, buttonParams);

        TextView footer = text(
            "JAT OS  /  LOCAL ACCESS CONTROL",
            8, 0xFF4F6960
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams =
            new LinearLayout.LayoutParams(-1, dp(45));
        footerParams.topMargin = dp(15);
        root.addView(footer, footerParams);

        accessButton.setOnClickListener(v -> authenticate(setup));

        setContentView(root);

        animationStep = 0;
        handler.post(terminalAnimation);
    }

    private void authenticate(boolean setup) {
        String password = passwordInput.getText().toString();

        if (password.length() < 6) {
            showMessage("PASSWORD MUST CONTAIN AT LEAST 6 CHARACTERS");
            return;
        }

        if (setup) {
            String confirmation = confirmInput.getText().toString();

            if (!password.equals(confirmation)) {
                showMessage("PASSWORDS DO NOT MATCH");
                return;
            }

            try {
                byte[] salt = new byte[16];
                new SecureRandom().nextBytes(salt);

                byte[] hash = hashPassword(password, salt);

                security.edit()
                    .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();

                unlockSystem();
            } catch (Exception e) {
                showMessage("SECURITY INITIALIZATION FAILED");
            }

            return;
        }

        try {
            byte[] salt = Base64.decode(
                security.getString("salt", ""), Base64.NO_WRAP
            );

            byte[] expected = Base64.decode(
                security.getString("hash", ""), Base64.NO_WRAP
            );

            byte[] actual = hashPassword(password, salt);

            if (MessageDigest.isEqual(expected, actual)) {
                unlockSystem();
            } else {
                passwordInput.setText("");
                showMessage("ACCESS DENIED // TRY AGAIN");
            }
        } catch (Exception e) {
            showMessage("AUTHENTICATION ERROR");
        }
    }

    private byte[] hashPassword(String password, byte[] salt)
        throws Exception {

        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, 120000, 256
        );

        try {
            return SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private void showMessage(String message) {
        if (terminalOutput != null) {
            terminalOutput.setText(
                terminalOutput.getText() + "\n\n> " + message
            );
        }
    }

    private void unlockSystem() {
        unlocked = true;
        gateVisible = false;
        handler.removeCallbacks(terminalAnimation);

        try {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(
                    passwordInput.getWindowToken(), 0
                );
        } catch (Exception ignored) {}

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(0xFF05080C);
        window.setNavigationBarColor(0xFF05080C);
        window.getDecorView().setSystemUiVisibility(0);

        setContentView(webView);
    }

    public class JatBridge {

        @JavascriptInterface
        public String getApps() {
            JSONArray result = new JSONArray();

            try {
                PackageManager pm = getPackageManager();

                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> apps =
                    pm.queryIntentActivities(intent, 0);

                Collections.sort(apps, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        return a.loadLabel(pm).toString()
                            .compareToIgnoreCase(
                                b.loadLabel(pm).toString()
                            );
                    }
                });

                for (ResolveInfo info : apps) {
                    try {
                        String packageName = info.activityInfo.packageName;
                        String activityName = info.activityInfo.name;
                        String label = info.loadLabel(pm).toString();

                        android.graphics.drawable.Drawable drawable =
                            info.loadIcon(pm);

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

                        ByteArrayOutputStream stream =
                            new ByteArrayOutputStream();

                        bitmap.compress(
                            Bitmap.CompressFormat.PNG, 90, stream
                        );

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
                    Intent fallback =
                        getPackageManager().getLaunchIntentForPackage(
                            packageName
                        );

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
            return Build.MODEL;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!unlocked && !gateVisible) {
            showGate();
        }
    }

    @Override
    public void onBackPressed() {
        if (!unlocked) return;

        if (webView != null) {
            webView.evaluateJavascript(
                "if(window.goBackScreen){goBackScreen();}", null
            );
        }
    }

    @Override
    protected void onDestroy() {
        gateVisible = false;
        handler.removeCallbacks(terminalAnimation);

        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {}
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
