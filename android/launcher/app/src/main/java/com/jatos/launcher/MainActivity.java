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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.net.Uri;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {
    private WebView webView;
    private EditText passwordInput;
    private EditText confirmInput;
    private TextView terminalOutput;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences security;

    private boolean unlocked = false;
    private boolean gateVisible = false;
    private boolean receiverRegistered = false;
    private boolean webReady = false;
    private int animationStep = 0;

    private final String[] terminalLines = {
        "INITIALIZING JAT SECURITY CORE...",
        "CHECKING ACCESS CONTROL...",
        "SESSION CHANNEL READY",
        "VERIFYING CREDENTIALS...",
        "INTERFACE MODULE STANDBY",
        "WAITING FOR AUTHENTICATION...",
        "SECURITY SESSION ACTIVE",
        "JAT OS // PRIVATE SESSION"
    };

    private final Runnable terminalAnimation = new Runnable() {
        @Override public void run() {
            if (!gateVisible || terminalOutput == null) return;
            StringBuilder output = new StringBuilder();
            int start = Math.max(0, animationStep - 4);
            for (int i=start; i<=animationStep; i++) {
                output.append("> ")
                    .append(terminalLines[i % terminalLines.length])
                    .append("\n");
            }
            output.append("\nSESSION // ")
                .append(String.format("%04d", animationStep % 10000));
            terminalOutput.setText(output.toString());
            animationStep++;
            handler.postDelayed(this,1100);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                unlocked = false;
                gateVisible = false;
                handler.removeCallbacks(terminalAnimation);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                unlocked = false;
                showGate();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        security = getSharedPreferences("jat_security",MODE_PRIVATE);
        createWebView();
        registerScreenReceiver();
        showGate();
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF080D0A);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        webView.addJavascriptInterface(new JatBridge(),"JAT");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view,String url) {
                webReady = true;
                if (unlocked) triggerSystemBoot();
            }
        });
        webView.loadUrl("file:///android_asset/jat-os/index.html");
    }

    private void triggerSystemBoot() {
        if (webView == null || !webReady || !unlocked) return;
        webView.postDelayed(() -> {
            if (webView != null && unlocked) {
                webView.evaluateJavascript(
                    "if(window.JATSystemBoot){window.JATSystemBoot();}",null
                );
            }
        },250);
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver,filter);
        }
        receiverRegistered = true;
    }

    private int dp(float value) {
        return (int)(value*getResources().getDisplayMetrics().density+0.5f);
    }

    private android.graphics.drawable.GradientDrawable background(int color,int stroke) {
        android.graphics.drawable.GradientDrawable drawable =
            new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(5));
        drawable.setStroke(dp(1),stroke);
        return drawable;
    }

    private TextView text(String value,int size,int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.MONOSPACE);
        return view;
    }

    private void showGate() {
        if (isFinishing()) return;
        unlocked = false;
        gateVisible = true;
        handler.removeCallbacks(terminalAnimation);

        Window window = getWindow();
        window.setStatusBarColor(0xFF050907);
        window.setNavigationBarColor(0xFF050907);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF050907);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26),dp(28),dp(26),dp(24));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        TextView logo = text("JAT // OS",28,0xFF9BC99D);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(android.graphics.Typeface.MONOSPACE,1);
        root.addView(logo,new LinearLayout.LayoutParams(-1,dp(65)));

        TextView subtitle = text("PRIVATE SYSTEM ACCESS",10,0xFF748879);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(.2f);
        root.addView(subtitle);

        View gap = new View(this);
        root.addView(gap,new LinearLayout.LayoutParams(1,dp(26)));

        LinearLayout terminal = new LinearLayout(this);
        terminal.setOrientation(LinearLayout.VERTICAL);
        terminal.setPadding(dp(15),dp(14),dp(15),dp(14));
        terminal.setBackground(background(0xFF0C1510,0xFF294B34));

        TextView terminalTitle = text("SECURITY TERMINAL  /  LIVE",9,0xFF9BC99D);
        terminal.addView(terminalTitle);

        terminalOutput = text("",11,0xFF9BC99D);
        terminalOutput.setLineSpacing(dp(4),1f);
        LinearLayout.LayoutParams terminalParams =
            new LinearLayout.LayoutParams(-1,dp(130));
        terminalParams.topMargin=dp(12);
        terminal.addView(terminalOutput,terminalParams);
        root.addView(terminal,new LinearLayout.LayoutParams(-1,dp(185)));

        View spacer = new View(this);
        root.addView(spacer,new LinearLayout.LayoutParams(1,dp(27)));

        boolean setup = !security.contains("hash");

        TextView title = text(
            setup ? "CREATE ACCESS PASSWORD" : "AUTHENTICATION REQUIRED",
            13,0xFFD6E5D8
        );
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.MONOSPACE,1);
        root.addView(title);

        TextView hint = text(
            setup ? "Create a password to initialize JAT OS." :
                    "Enter your password to initialize the system.",
            10,0xFF748879
        );
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1,dp(35));
        root.addView(hint,hintParams);

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setTextColor(0xFFD6E5D8);
        passwordInput.setHintTextColor(0xFF748879);
        passwordInput.setHint(setup ? "Create password" : "Enter password");
        passwordInput.setTextSize(13);
        passwordInput.setInputType(
            129 | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        passwordInput.setPadding(dp(14),0,dp(14),0);
        passwordInput.setBackground(background(0xFF0D1711,0xFF365B40));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1,dp(51));
        inputParams.topMargin=dp(15);
        root.addView(passwordInput,inputParams);

        confirmInput = new EditText(this);
        confirmInput.setSingleLine(true);
        confirmInput.setTextColor(0xFFD6E5D8);
        confirmInput.setHintTextColor(0xFF748879);
        confirmInput.setHint("Confirm password");
        confirmInput.setTextSize(13);
        confirmInput.setInputType(
            129 | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        confirmInput.setPadding(dp(14),0,dp(14),0);
        confirmInput.setBackground(background(0xFF0D1711,0xFF365B40));
        LinearLayout.LayoutParams confirmParams =
            new LinearLayout.LayoutParams(-1,dp(51));
        confirmParams.topMargin=dp(9);
        if (setup) root.addView(confirmInput,confirmParams);

        Button access = new Button(this);
        access.setText("INITIALIZE SYSTEM");
        access.setTextColor(0xFF07100A);
        access.setTextSize(11);
        access.setLetterSpacing(.08f);
        access.setAllCaps(false);
        access.setBackground(background(0xFF9BC99D,0xFF9BC99D));
        LinearLayout.LayoutParams buttonParams =
            new LinearLayout.LayoutParams(-1,dp(51));
        buttonParams.topMargin=dp(16);
        root.addView(access,buttonParams);
        access.setOnClickListener(v -> authenticate(setup));

        TextView footer = text("JAT OS  /  ACCESS CONTROL",8,0xFF526A57);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams =
            new LinearLayout.LayoutParams(-1,dp(42));
        footerParams.topMargin=dp(12);
        root.addView(footer,footerParams);

        FrameLayout lockLayer = new FrameLayout(this);
        lockLayer.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView quickAccess = text("◉", 23, 0xFF65EAB2);
        quickAccess.setGravity(Gravity.CENTER);
        quickAccess.setBackground(background(0xFF0C1510, 0xFF294B34));
        quickAccess.setContentDescription("Quick access");
        FrameLayout.LayoutParams quickParams = new FrameLayout.LayoutParams(
            dp(46), dp(46), Gravity.BOTTOM | Gravity.RIGHT
        );
        quickParams.setMargins(0, 0, dp(20), dp(20));
        lockLayer.addView(quickAccess, quickParams);
        quickAccess.setOnClickListener(v -> showQuickPad());

        setContentView(lockLayer);
        animationStep=0;
        handler.post(terminalAnimation);
    }



    private void showQuickPad() {
        showPinEntry(false, null, security.contains("quick_pin_hash"));
    }

    private void showPinEntry(boolean confirming, String firstPin, boolean pinExists) {
        unlocked = false;
        gateVisible = true;
        handler.removeCallbacks(terminalAnimation);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFF050907);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(22), dp(15), dp(22), dp(18));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            -1, -2, Gravity.CENTER
        );
        frame.addView(panel, panelParams);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 32, 0xFF65EAB2);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(46)));
        back.setOnClickListener(v -> showGate());

        TextView heading = text(
            confirming ? "CONFIRM PIN" :
                (pinExists ? "ENTER PIN" : "CREATE PIN"),
            11, 0xFF9BC99D
        );
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(android.graphics.Typeface.MONOSPACE, 1);
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(46), 1f));

        View topSpace = new View(this);
        top.addView(topSpace, new LinearLayout.LayoutParams(dp(48), dp(46)));
        panel.addView(top);

        TextView dots = text("○     ○", 25, 0xFF65EAB2);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsParams =
            new LinearLayout.LayoutParams(-1, dp(66));
        dotsParams.topMargin = dp(10);
        dotsParams.bottomMargin = dp(17);
        panel.addView(dots, dotsParams);

        String[][] keys = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {"⌫", "0", "×"}
        };

        StringBuilder entered = new StringBuilder();

        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(-1, dp(61));
            rowParams.bottomMargin = dp(9);
            panel.addView(row, rowParams);

            for (String key : rowKeys) {
                TextView button = text(key, 21, 0xFFD6E5D8);
                button.setGravity(Gravity.CENTER);
                button.setBackground(background(0xFF101B14, 0xFF294B34));
                button.setClickable(true);
                button.setFocusable(true);

                LinearLayout.LayoutParams keyParams =
                    new LinearLayout.LayoutParams(0, -1, 1f);
                keyParams.setMargins(dp(5), 0, dp(5), 0);
                row.addView(button, keyParams);

                button.setOnClickListener(v -> {
                    if ("×".equals(key)) {
                        showGate();
                        return;
                    }

                    if ("⌫".equals(key)) {
                        if (entered.length() > 0) {
                            entered.deleteCharAt(entered.length() - 1);
                        }
                    } else if (entered.length() < 2) {
                        entered.append(key);
                    }

                    dots.setText(
                        (entered.length() >= 1 ? "●" : "○") +
                        "     " +
                        (entered.length() >= 2 ? "●" : "○")
                    );

                    if (entered.length() == 2) {
                        String value = entered.toString();

                        if (!pinExists) {
                            if (!confirming) {
                                showPinEntry(true, value, false);
                            } else if (value.equals(firstPin)) {
                                saveQuickPin(value);
                            } else {
                                showPinEntry(false, null, false);
                            }
                        } else {
                            verifyQuickCode(value);
                        }
                    }
                });
            }
        }

        setContentView(frame);
    }

    private void saveQuickPin(String code) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = hashPassword(code, salt);

            security.edit()
                .putString("quick_pin_salt",
                    Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("quick_pin_hash",
                    Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();

            showFingerprintGate();
        } catch (Exception e) {
            showGate();
        }
    }

    private void verifyQuickCode(String code) {
        try {
            byte[] salt = Base64.decode(
                security.getString("quick_pin_salt", ""),
                Base64.NO_WRAP
            );
            byte[] expected = Base64.decode(
                security.getString("quick_pin_hash", ""),
                Base64.NO_WRAP
            );
            byte[] actual = hashPassword(code, salt);

            if (MessageDigest.isEqual(expected, actual)) {
                showFingerprintGate();
            } else {
                showQuickPad();
            }
        } catch (Exception e) {
            showGate();
        }
    }

    private void showFingerprintGate() {
        unlocked = false;
        gateVisible = true;
        handler.removeCallbacks(terminalAnimation);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFF050907);

        TextView back = text("‹", 32, 0xFF65EAB2);
        back.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
            dp(54), dp(54), Gravity.TOP | Gravity.LEFT
        );
        backParams.setMargins(dp(17), dp(12), 0, 0);
        frame.addView(back, backParams);
        back.setOnClickListener(v -> showQuickPad());

        QuickFingerprintView fingerprint = new QuickFingerprintView(
            this, () -> unlockSystem()
        );

        FrameLayout.LayoutParams fingerprintParams =
            new FrameLayout.LayoutParams(dp(270), dp(310), Gravity.CENTER);
        frame.addView(fingerprint, fingerprintParams);

        setContentView(frame);
    }

    private class QuickFingerprintView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable onComplete;
        private boolean scanning = false;
        private long scanStarted = 0L;
        private static final long SCAN_DURATION = 5600L;

        private final Runnable scanTick = new Runnable() {
            @Override
            public void run() {
                if (!scanning) return;

                long elapsed = System.currentTimeMillis() - scanStarted;
                if (elapsed >= SCAN_DURATION) {
                    scanning = false;
                    invalidate();
                    onComplete.run();
                    return;
                }

                invalidate();
                handler.postDelayed(this, 16);
            }
        };

        QuickFingerprintView(Context context, Runnable callback) {
            super(context);
            onComplete = callback;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            // Nested curved ridges form a fingerprint rather than oval rings.
            paint.setColor(0xFF65EAB2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(2.0f * d);
            paint.setAlpha(225);

            for (int i = 0; i < 11; i++) {
                float rx = (24f + i * 7.0f) * d;
                float ry = (28f + i * 7.5f) * d;

                Path ridge = new Path();
                ridge.moveTo(cx - rx, cy + ry * 0.48f);
                ridge.cubicTo(
                    cx - rx * 1.08f, cy - ry * 0.55f,
                    cx - rx * 0.48f, cy - ry * 1.05f,
                    cx + rx * 0.08f, cy - ry * 0.98f
                );
                ridge.cubicTo(
                    cx + rx * 0.83f, cy - ry * 0.88f,
                    cx + rx * 1.08f, cy - ry * 0.12f,
                    cx + rx, cy + ry * 0.48f
                );
                ridge.cubicTo(
                    cx + rx * 0.94f, cy + ry * 0.92f,
                    cx + rx * 0.36f, cy + ry * 0.84f,
                    cx + rx * 0.10f, cy + ry * 0.56f
                );
                canvas.drawPath(ridge, paint);
            }

            Path center = new Path();
            center.moveTo(cx - 9f*d, cy + 48f*d);
            center.cubicTo(
                cx - 31f*d, cy + 13f*d,
                cx - 20f*d, cy - 34f*d,
                cx + 10f*d, cy - 39f*d
            );
            center.cubicTo(
                cx + 44f*d, cy - 43f*d,
                cx + 44f*d, cy + 8f*d,
                cx + 13f*d, cy + 17f*d
            );
            center.cubicTo(
                cx - 6f*d, cy + 22f*d,
                cx - 13f*d, cy - 1f*d,
                cx + 1f*d, cy - 12f*d
            );
            center.cubicTo(
                cx + 14f*d, cy - 22f*d,
                cx + 25f*d, cy - 5f*d,
                cx + 15f*d, cy + 5f*d
            );
            canvas.drawPath(center, paint);

            if (scanning) {
                float progress = Math.min(
                    1f,
                    (System.currentTimeMillis() - scanStarted) /
                        (float) SCAN_DURATION
                );

                float top = cy - 112f*d;
                float bottom = cy + 112f*d;
                float y = progress <= 0.5f
                    ? top + progress * 2f * (bottom - top)
                    : bottom - (progress - 0.5f) * 2f * (bottom - top);

                int save = canvas.save();
                canvas.clipRect(
                    cx - 100f*d, top, cx + 100f*d, bottom
                );

                paint.setStrokeWidth(13f*d);
                paint.setAlpha(35);
                canvas.drawLine(cx - 100f*d, y, cx + 100f*d, y, paint);

                paint.setStrokeWidth(2.2f*d);
                paint.setAlpha(255);
                canvas.drawLine(cx - 100f*d, y, cx + 100f*d, y, paint);

                canvas.restoreToCount(save);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!scanning) {
                        scanning = true;
                        scanStarted = System.currentTimeMillis();
                        handler.removeCallbacks(scanTick);
                        handler.post(scanTick);
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (scanning) {
                        scanning = false;
                        handler.removeCallbacks(scanTick);
                        invalidate();
                    }
                    return true;
            }
            return true;
        }

        @Override
        protected void onDetachedFromWindow() {
            scanning = false;
            handler.removeCallbacks(scanTick);
            super.onDetachedFromWindow();
        }
    }

    private void authenticate(boolean setup) {
        String password = passwordInput.getText().toString();
        if (password.length()<6) {
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
                byte[] hash = hashPassword(password,salt);
                security.edit()
                    .putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP))
                    .putString("hash",Base64.encodeToString(hash,Base64.NO_WRAP))
                    .apply();
                unlockSystem();
            } catch (Exception e) {
                showMessage("SECURITY INITIALIZATION FAILED");
            }
            return;
        }
        try {
            byte[] salt = Base64.decode(security.getString("salt",""),Base64.NO_WRAP);
            byte[] expected = Base64.decode(security.getString("hash",""),Base64.NO_WRAP);
            byte[] actual = hashPassword(password,salt);
            if (MessageDigest.isEqual(expected,actual)) {
                unlockSystem();
            } else {
                passwordInput.setText("");
                showMessage("ACCESS DENIED // TRY AGAIN");
            }
        } catch (Exception e) {
            showMessage("AUTHENTICATION ERROR");
        }
    }

    private byte[] hashPassword(String password,byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),salt,120000,256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private void showMessage(String message) {
        if (terminalOutput != null) {
            terminalOutput.setText(terminalOutput.getText()+"\n\n> "+message);
        }
    }

    private void unlockSystem() {
        unlocked=true;
        gateVisible=false;
        handler.removeCallbacks(terminalAnimation);
        try {
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(passwordInput.getWindowToken(),0);
        } catch (Exception ignored) {}

        Window window=getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(0xFF080D0A);
        window.setNavigationBarColor(0xFF080D0A);
        window.getDecorView().setSystemUiVisibility(0);
        setContentView(webView);
        triggerSystemBoot();
    }

    public class JatBridge {
        @JavascriptInterface
        public String getApps() {
            JSONArray result = new JSONArray();
            try {
                PackageManager pm=getPackageManager();
                Intent intent=new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> apps=pm.queryIntentActivities(intent,0);
                Collections.sort(apps,new Comparator<ResolveInfo>() {
                    @Override public int compare(ResolveInfo a,ResolveInfo b) {
                        return a.loadLabel(pm).toString().compareToIgnoreCase(
                            b.loadLabel(pm).toString()
                        );
                    }
                });

                for (ResolveInfo info:apps) {
                    try {
                        if (info.activityInfo==null) continue;
                        String packageName=info.activityInfo.packageName;
                        String activityName=info.activityInfo.name;
                        String label=info.loadLabel(pm).toString();
                        Drawable drawable=info.loadIcon(pm);

                        int width=drawable.getIntrinsicWidth();
                        int height=drawable.getIntrinsicHeight();
                        if (width<=0) width=96;
                        if (height<=0) height=96;
                        width=Math.min(width,144);
                        height=Math.min(height,144);

                        Bitmap bitmap=Bitmap.createBitmap(
                            width,height,Bitmap.Config.ARGB_8888
                        );
                        Canvas canvas=new Canvas(bitmap);
                        drawable.setBounds(0,0,width,height);
                        drawable.draw(canvas);

                        ByteArrayOutputStream stream=new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG,88,stream);
                        String icon=Base64.encodeToString(
                            stream.toByteArray(),Base64.NO_WRAP
                        );
                        bitmap.recycle();

                        JSONObject app=new JSONObject();
                        app.put("name",label);
                        app.put("package",packageName);
                        app.put("activity",activityName);
                        app.put("icon","data:image/png;base64,"+icon);
                        result.put(app);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            return result.toString();
        }

        @JavascriptInterface
        public boolean launchApp(String packageName,String activityName) {
            try {
                Intent launch=new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setClassName(packageName,activityName);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return true;
            } catch (Exception e) {
                try {
                    Intent fallback=getPackageManager().getLaunchIntentForPackage(packageName);
                    if (fallback!=null) {
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
                Intent intent=new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void openAppInfo(String packageName) {
            try {
                Intent intent=new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+packageName)
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
        if (!unlocked && !gateVisible) showGate();
    }

    @Override
    public void onBackPressed() {
        if (!unlocked) return;
        if (webView!=null) {
            webView.evaluateJavascript(
                "if(window.goBackScreen){goBackScreen();}",null
            );
        }
    }

    @Override
    protected void onDestroy() {
        gateVisible=false;
        handler.removeCallbacks(terminalAnimation);
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver); }
            catch (Exception ignored) {}
        }
        if (webView!=null) {
            webView.destroy();
            webView=null;
        }
        super.onDestroy();
    }
}
