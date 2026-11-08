package com.dexkeybridge.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView logView;
    private EditText targetPackage;
    private Switch windowsMode;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("DexKeyBridge");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        TextView title = text("DexKeyBridge", 28, true);
        root.addView(title);
        TextView sub = text("Samsung DeX keyboard diagnostic + experimental Windows App remapping", 14, false);
        sub.setTextColor(Color.LTGRAY);
        root.addView(sub);

        windowsMode = new Switch(this);
        windowsMode.setText("Windows Mode (experimental remapping)");
        windowsMode.setChecked(AppState.isWindowsMode(this));
        windowsMode.setPadding(0, dp(16), 0, dp(8));
        windowsMode.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> AppState.setWindowsMode(this, isChecked));
        root.addView(windowsMode);

        status = text("", 15, false);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding(0, dp(8), 0, dp(12));
        root.addView(status);

        Button accessibility = button("1. Open Accessibility settings");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        Button imeSettings = button("2. Enable DexKeyBridge IME");
        imeSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(imeSettings);

        Button picker = button("Show keyboard picker");
        picker.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(picker);

        targetPackage = new EditText(this);
        targetPackage.setSingleLine(true);
        targetPackage.setHint("Target package (blank = any app)");
        targetPackage.setText(AppState.getTarget(this));
        root.addView(targetPackage);

        Button useForeground = button("Use current foreground app as target");
        useForeground.setOnClickListener(v -> {
            String pkg = AppState.getForeground(this);
            targetPackage.setText(pkg);
            AppState.setTarget(this, pkg);
            toast("Target: " + pkg);
        });
        root.addView(useForeground);

        Button saveTarget = button("Save target package");
        saveTarget.setOnClickListener(v -> {
            AppState.setTarget(this, targetPackage.getText().toString().trim());
            toast("Target saved");
        });
        root.addView(saveTarget);

        Button testWinE = button("TEST injection: Win + E");
        testWinE.setOnClickListener(v -> {
            boolean ok = DexKeyboardImeService.sendCombo(android.view.KeyEvent.KEYCODE_E, false, false, false, true);
            AppState.setLastInjection(this, ok ? "SENDKEYEVENT returned true" : "NO ACTIVE INPUT CONNECTION / SEND FAILED");
            toast(ok ? "Injection call accepted" : "Injection unavailable");
        });
        root.addView(testWinE);

        TextView help = text(
                "TEST PLAN\n" +
                "A) Enable the Accessibility service.\n" +
                "B) In DeX press Win, Win+E and Alt+Tab.\n" +
                "C) META / ALT+TAB should become PASS if Samsung exposes those events.\n" +
                "D) Enable DexKeyBridge IME, focus Windows App, then test Win+E.\n\n" +
                "Fallback while Windows Mode is ON:\n" +
                "Right Ctrl + E/R/D/S => remote Win + E/R/D/S.\n" +
                "Alt + Tab => Alt + PageUp.\n\n" +
                "No Internet permission is requested.",
                14, false);
        help.setPadding(0, dp(16), 0, dp(12));
        root.addView(help);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = button("Clear log");
        clear.setOnClickListener(v -> AppState.clearLog(this));
        Button copy = button("Copy log");
        copy.setOnClickListener(v -> copyLog());
        buttons.addView(clear, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        buttons.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttons);

        logView = text("", 11, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresher); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresher); }

    private void refreshUi() {
        String s =
                "Foreground: " + empty(AppState.getForeground(this)) + "\n" +
                "Target:     " + empty(AppState.getTarget(this)) + "\n" +
                "META INTERCEPT:       " + (AppState.isMetaDetected(this) ? "PASS" : "NOT SEEN") + "\n" +
                "ALT+TAB INTERCEPT:    " + (AppState.isAltTabDetected(this) ? "PASS" : "NOT SEEN") + "\n" +
                "IME input channel:    " + (DexKeyboardImeService.isAvailable() ? "AVAILABLE" : "UNAVAILABLE") + "\n" +
                "Last injection test:  " + AppState.getLastInjection(this);
        status.setText(s);
        logView.setText(AppState.getLog(this));
    }

    private String empty(String s) { return s == null || s.isEmpty() ? "<none>" : s; }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("DexKeyBridge diagnostics", AppState.getLog(this)));
            toast("Diagnostic log copied");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
