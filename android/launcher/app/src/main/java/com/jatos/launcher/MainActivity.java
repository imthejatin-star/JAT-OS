package com.jatos.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import android.content.SharedPreferences;
import android.util.TypedValue;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Arrays;
import android.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {

    private static final String PREFS = "jat_security";
    private static final String SALT = "password_salt";
    private static final String HASH = "password_hash";

    private final int BG = Color.rgb(5, 5, 5);
    private final int GREEN = Color.rgb(0, 229, 138);
    private final int MUTED = Color.rgb(115, 125, 120);

    private boolean unlocked = false;
    private boolean confirming = false;
    private String firstPassword = null;

    private LinearLayout root;
    private EditText passwordInput;
    private TextView message;
    private BroadcastReceiver screenReceiver;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        applyImmersiveMode();
        registerScreenReceiver();
        showGate();
    }

    private void applyImmersiveMode() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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
        applyImmersiveMode();
        if (!unlocked) showGate();
    }

    private TextView makeText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setTypeface(Typeface.MONOSPACE);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private void showGate() {
        if (isFinishing()) return;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(BG);
        root.setPadding(dp(28), dp(24), dp(28), dp(24));

        TextView system = makeText("JAT OS  //  PRIVATE SYSTEM", 11, GREEN);
        root.addView(system, new LinearLayout.LayoutParams(-1, dp(35)));

        TextView title = makeText("JAT OS", 36, Color.WHITE);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams =
            new LinearLayout.LayoutParams(-1, dp(65));
        titleParams.topMargin = dp(18);
        root.addView(title, titleParams);

        TextView subtitle = makeText("INITIALIZING SECURE INTERFACE", 10, MUTED);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(35)));

        TextView animation = makeText(
            "01001010  01000001  01010100\n" +
            "SYSTEM // ACCESS CONTROL\n" +
            "01010011  01011001  01010011\n" +
            "●  ●  ●  ●  ●  ●  ●  ●",
            12, GREEN
        );
        animation.setLineSpacing(dp(8), 1f);
        LinearLayout.LayoutParams animParams =
            new LinearLayout.LayoutParams(-1, dp(125));
        animParams.topMargin = dp(20);
        root.addView(animation, animParams);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean hasPassword = prefs.contains(HASH);

        String heading;
        String hint;
        String buttonText;

        if (!hasPassword) {
            heading = confirming ? "CONFIRM YOUR PASSWORD" : "CREATE YOUR PASSWORD";
            hint = confirming ? "Re-enter your password" : "Create a password";
            buttonText = confirming ? "SAVE PASSWORD" : "CONTINUE";
        } else {
            heading = "IDENTITY VERIFICATION";
            hint = "Enter your password";
            buttonText = "UNLOCK JAT OS";
        }

        TextView label = makeText(heading, 11, MUTED);
        LinearLayout.LayoutParams labelParams =
            new LinearLayout.LayoutParams(-1, dp(45));
        labelParams.topMargin = dp(25);
        root.addView(label, labelParams);

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setHintTextColor(MUTED);
        passwordInput.setHint(hint);
        passwordInput.setTextSize(16);
        passwordInput.setTypeface(Typeface.MONOSPACE);
        passwordInput.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        passwordInput.setGravity(Gravity.CENTER);
        passwordInput.setPadding(dp(12), dp(14), dp(12), dp(14));

        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Color.rgb(15, 18, 17));
        inputBackground.setCornerRadius(dp(9));
        inputBackground.setStroke(dp(1), Color.rgb(50, 65, 57));
        passwordInput.setBackground(inputBackground);

        LinearLayout.LayoutParams inputParams =
            new LinearLayout.LayoutParams(-1, dp(55));
        root.addView(passwordInput, inputParams);

        message = makeText("ACCESS RESTRICTED", 10, MUTED);
        LinearLayout.LayoutParams msgParams =
            new LinearLayout.LayoutParams(-1, dp(42));
        root.addView(message, msgParams);

        Button unlock = new Button(this);
        unlock.setText(buttonText);
        unlock.setTextColor(Color.BLACK);
        unlock.setTextSize(12);
        unlock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        unlock.setAllCaps(false);
        unlock.setBackgroundColor(GREEN);

        LinearLayout.LayoutParams buttonParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(10);
        root.addView(unlock, buttonParams);

        TextView footer = makeText("JAT OS // LOCAL AUTHENTICATION", 9, MUTED);
        LinearLayout.LayoutParams footerParams =
            new LinearLayout.LayoutParams(-1, dp(55));
        footerParams.topMargin = dp(25);
        root.addView(footer, footerParams);

        unlock.setOnClickListener(v -> handlePassword());

        setContentView(root);
    }

    private void handlePassword() {
        String entered = passwordInput.getText().toString();

        if (entered.length() < 6) {
            message.setText("USE AT LEAST 6 CHARACTERS");
            message.setTextColor(Color.rgb(255, 170, 70));
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        if (!prefs.contains(HASH)) {
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
            message.setText("ACCESS DENIED // TRY AGAIN");
            message.setTextColor(Color.rgb(255, 85, 85));
        }
    }

    private void savePassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(password, salt);

            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save password", Toast.LENGTH_LONG).show();
        }
    }

    private boolean verifyPassword(String password) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            byte[] salt = Base64.decode(prefs.getString(SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(prefs.getString(HASH, ""), Base64.NO_WRAP);
            byte[] actual = derive(password, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private void unlockSystem() {
        unlocked = true;

        WebView webView = new WebView(this);
        webView.setBackgroundColor(BG);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.setWebViewClient(new WebViewClient());

        setContentView(webView);
        webView.loadUrl("file:///android_asset/jat-os/index.html");
    }

    @Override
    public void onBackPressed() {
        // Keep JAT OS as the Home environment.
    }

    @Override
    protected void onDestroy() {
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
