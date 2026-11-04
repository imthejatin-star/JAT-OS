package com.jatos.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.SharedPreferences;
import android.widget.*;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;

import java.security.SecureRandom;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Random;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(3, 7, 6);
    private final int GREEN = Color.rgb(0, 255, 145);
    private final int MUTED = Color.rgb(105, 145, 125);

    private boolean unlocked = false;
    private boolean confirming = false;
    private String firstPassword = null;

    private LinearLayout content;
    private EditText passwordInput;
    private TextView message;
    private BroadcastReceiver screenReceiver;

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

    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
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

    private void addSpace(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void showGate() {
        if (isFinishing()) return;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(new MatrixBackground(this),
            new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(26), dp(28), dp(26), dp(28));

        scroll.addView(content);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView top = text("JAT OS   /   PRIVATE ENVIRONMENT", 10, GREEN, true);
        content.addView(top, new LinearLayout.LayoutParams(-1, dp(32)));

        addSpace(content, 30);

        TextView logo = text("JAT", 62, Color.WHITE, true);
        content.addView(logo, new LinearLayout.LayoutParams(-1, dp(75)));

        TextView os = text("OPERATING INTERFACE", 10, GREEN, true);
        content.addView(os, new LinearLayout.LayoutParams(-1, dp(28)));

        addSpace(content, 25);

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(14), dp(10), dp(14), dp(10));
        status.setBackground(shape(0xCC07150F, 0x8840B87B, 12));

        TextView statusText = text("●  ENCRYPTED SESSION  /  LOCAL", 10, GREEN, true);
        status.addView(statusText);
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(42)));

        addSpace(content, 18);

        TextView terminal = text(
            "INITIALIZING CORE........... OK\n" +
            "LOADING INTERFACE.......... OK\n" +
            "VERIFYING SESSION........... READY\n" +
            "--------------------------------",
            10, MUTED, false
        );
        terminal.setLineSpacing(dp(7), 1f);
        content.addView(terminal, new LinearLayout.LayoutParams(-1, dp(112)));

        SharedPreferences prefs = getSharedPreferences("jat_security", MODE_PRIVATE);
        boolean hasPassword = prefs.contains("password_hash");

        String heading = !hasPassword
            ? (confirming ? "CONFIRM YOUR ACCESS KEY" : "CREATE YOUR ACCESS KEY")
            : "IDENTITY VERIFICATION";

        String hint = !hasPassword
            ? (confirming ? "Re-enter your password" : "Create a password")
            : "Enter your password";

        String buttonText = !hasPassword
            ? (confirming ? "CONFIRM ACCESS KEY" : "CREATE ACCESS KEY")
            : "ENTER JAT OS";

        addSpace(content, 20);

        TextView label = text(heading, 11, Color.WHITE, true);
        content.addView(label, new LinearLayout.LayoutParams(-1, dp(42)));

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setGravity(Gravity.CENTER);
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setHintTextColor(0xFF789486);
        passwordInput.setHint(hint);
        passwordInput.setTextSize(15);
        passwordInput.setTypeface(Typeface.MONOSPACE);
        passwordInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        passwordInput.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        passwordInput.setBackground(shape(0xEE07100C, 0xFF1D7650, 10));

        content.addView(passwordInput,
            new LinearLayout.LayoutParams(-1, dp(56)));

        message = text("ACCESS RESTRICTED  /  WAITING FOR INPUT", 9, MUTED, false);
        content.addView(message, new LinearLayout.LayoutParams(-1, dp(45)));

        Button enter = new Button(this);
        enter.setText(buttonText);
        enter.setTextColor(Color.BLACK);
        enter.setTextSize(12);
        enter.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        enter.setAllCaps(false);
        enter.setBackground(shape(GREEN, GREEN, 10));

        LinearLayout.LayoutParams buttonParams =
            new LinearLayout.LayoutParams(-1, dp(54));
        buttonParams.topMargin = dp(4);
        content.addView(enter, buttonParams);

        addSpace(content, 26);

        TextView footer = text(
            "JAT OS // PERSONAL SYSTEM\nALL OPERATIONS RUN LOCALLY",
            9, MUTED, false
        );
        footer.setLineSpacing(dp(5), 1f);
        content.addView(footer, new LinearLayout.LayoutParams(-1, dp(50)));

        enter.setOnClickListener(v -> handlePassword());

        frame.setFocusableInTouchMode(true);
        frame.requestFocus();
        setContentView(frame);
    }

    private void handlePassword() {
        String entered = passwordInput.getText().toString();

        if (entered.length() < 6) {
            message.setText("ACCESS KEY MUST HAVE AT LEAST 6 CHARACTERS");
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
                message.setText("ACCESS KEYS DO NOT MATCH");
                message.setTextColor(0xFFFF6969);
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
            message.setText("ACCESS DENIED  /  INVALID KEY");
            message.setTextColor(0xFFFF6969);
        }
    }

    private void savePassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(password, salt);

            getSharedPreferences("jat_security", MODE_PRIVATE).edit()
                .putString("password_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("password_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save access key", Toast.LENGTH_LONG).show();
        }
    }

    private boolean verifyPassword(String password) {
        try {
            SharedPreferences p = getSharedPreferences("jat_security", MODE_PRIVATE);
            byte[] salt = Base64.decode(p.getString("password_salt", ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(p.getString("password_hash", ""), Base64.NO_WRAP);
            byte[] actual = derive(password, salt);
            return MessageDigest.isEqual(expected, actual);
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

        android.webkit.WebView web = new android.webkit.WebView(this);
        web.setBackgroundColor(BG);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new android.webkit.WebViewClient());

        setContentView(web);
        web.loadUrl("file:///android_asset/jat-os/index.html");
    }

    @Override
    public void onBackPressed() {
        // JAT OS remains the Home environment.
    }

    @Override
    protected void onDestroy() {
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); }
            catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    // Locally rendered animated digital-rain background.
    private class MatrixBackground extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private float[] drops;
        private float[] speeds;
        private int columns;
        private float density;
        private long frame = 0;
        private final String chars = "01アイウエオカキクケコ0123456789ABCDEF";

        MatrixBackground(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            columns = Math.max(1, (int)(w / (16 * density)));
            drops = new float[columns];
            speeds = new float[columns];
            for (int i = 0; i < columns; i++) {
                drops[i] = random.nextInt(Math.max(1, h / 10));
                speeds[i] = (1.1f + random.nextFloat() * 2.5f) * density;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            canvas.drawColor(Color.rgb(3, 7, 6));

            p.setStrokeWidth(1);
            p.setColor(0x1830A875);
            for (int x = 0; x < w; x += (int)(34 * density)) {
                canvas.drawLine(x, 0, x, h, p);
            }
            for (int y = 0; y < h; y += (int)(34 * density)) {
                canvas.drawLine(0, y, w, y, p);
            }

            p.setTypeface(Typeface.MONOSPACE);
            p.setTextSize(11 * density);

            if (drops != null) {
                for (int i = 0; i < columns; i++) {
                    float x = i * 16 * density;
                    float y = drops[i] * 13 * density;
                    for (int j = 0; j < 9; j++) {
                        float yy = y - j * 14 * density;
                        if (yy < 0 || yy > h) continue;

                        int alpha = Math.max(8, 95 - j * 10);
                        p.setColor(Color.argb(alpha, 0, 255, 130));
                        if (j == 0) p.setColor(0xCCB7FFD9);

                        char c = chars.charAt(random.nextInt(chars.length()));
                        canvas.drawText(String.valueOf(c), x, yy, p);
                    }

                    drops[i] += speeds[i];
                    if (drops[i] * 13 * density > h + random.nextInt(500)) {
                        drops[i] = -random.nextInt(50);
                        speeds[i] = (1.1f + random.nextFloat() * 2.5f) * density;
                    }
                }
            }

            // Animated scanning beam.
            float scan = (frame * 3 * density) % (h + 100 * density);
            p.setShader(new LinearGradient(
                0, scan - 30 * density, 0, scan + 30 * density,
                0x0000FF88, 0x2230FF99, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, scan - 30 * density, w, scan + 30 * density, p);
            p.setShader(null);

            // Subtle corner indicators.
            p.setColor(0x6630FF99);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * density);
            float m = 15 * density;
            float l = 28 * density;
            canvas.drawLine(m, m, m + l, m, p);
            canvas.drawLine(m, m, m, m + l, p);
            canvas.drawLine(w - m, m, w - m - l, m, p);
            canvas.drawLine(w - m, m, w - m, m + l, p);
            canvas.drawLine(m, h - m, m + l, h - m, p);
            canvas.drawLine(m, h - m, m, h - m - l, p);
            canvas.drawLine(w - m, h - m, w - m - l, h - m, p);
            canvas.drawLine(w - m, h - m, w - m, h - m - l, p);
            p.setStyle(Paint.Style.FILL);

            frame++;
            postInvalidateDelayed(34);
        }
    }
}
