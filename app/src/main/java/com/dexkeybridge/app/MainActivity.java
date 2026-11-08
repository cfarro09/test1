package com.dexkeybridge.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView status;
    private TextView modeStatus;
    private TextView logView;

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        TextView title = text("DexKeyBridge Diagnostic v2", 26, true);
        root.addView(title);

        TextView subtitle = text(
                "v3 experimental — misma app.\n" +
                "Intercepta atajos del K380s solo cuando Microsoft Windows App está al frente.",
                14,
                false
        );
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle);

        status = text("", 14, false);
        status.setTypeface(Typeface.MONOSPACE);
        root.addView(status);

        modeStatus = text("", 18, true);
        modeStatus.setPadding(0, dp(12), 0, dp(8));
        root.addView(modeStatus);

        Button observe = button("Modo 1: OBSERVAR (no bloquea nada)");
        observe.setOnClickListener(v -> {
            AppState.setMode(this, AppState.MODE_OBSERVE);
            refresh();
        });
        root.addView(observe);

        Button block = button("Modo 2: BLOQUEAR DEX (prueba)");
        block.setOnClickListener(v -> {
            AppState.setMode(this, AppState.MODE_BLOCK);
            refresh();
        });
        root.addView(block);

        Button forward = button("Modo 3: ENVIAR A WINDOWS (experimental)");
        forward.setOnClickListener(v -> {
            AppState.setMode(this, AppState.MODE_FORWARD);
            refresh();
        });
        root.addView(forward);

        Button accessibility = button("Abrir ajustes de Accesibilidad");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        Button imeSettings = button("Habilitar DexKeyBridge Keyboard Bridge");
        imeSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(imeSettings);

        Button imePicker = button("Seleccionar DexKeyBridge como teclado actual");
        imePicker.setOnClickListener(v -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(imePicker);

        Button openWindows = button("Abrir Microsoft Windows App");
        openWindows.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(AppState.WINDOWS_APP_PACKAGE);
            if (launch != null) {
                startActivity(launch);
            }
        });
        root.addView(openWindows);

        TextView instructions = text(
                "\nPRUEBA A — BLOQUEAR DEX\n" +
                "1. Selecciona BLOQUEAR DEX.\n" +
                "2. Abre Windows App y entra a tu PC.\n" +
                "3. Prueba Win, Win+E y Alt+Tab.\n" +
                "4. DeX no debería ejecutar esos atajos.\n\n" +
                "PRUEBA B — ENVIAR A WINDOWS\n" +
                "1. Habilita y selecciona DexKeyBridge Keyboard Bridge.\n" +
                "2. Selecciona ENVIAR A WINDOWS.\n" +
                "3. En Windows App prueba Win+E y Alt+Tab.\n" +
                "4. Win+E intenta llegar como Win+E; Alt+Tab se envía como Alt+PageUp.\n\n" +
                "Si InputConnection sale UNAVAILABLE, Android no nos está dando un canal público de inyección hacia Windows App.",
                14,
                false
        );
        root.addView(instructions);

        Button clear = button("Limpiar log");
        clear.setOnClickListener(v -> {
            AppState.clearLog(this);
            AppState.setLastInjection(this, "NOT TESTED");
            refresh();
        });
        root.addView(clear);

        Button copy = button("Copiar log");
        copy.setOnClickListener(v -> {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(
                        ClipData.newPlainText("DexKeyBridge log", AppState.getLog(this))
                );
            }
        });
        root.addView(copy);

        logView = text("", 11, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(12), 0, 0);
        root.addView(logView);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    private void refresh() {
        int mode = AppState.getMode(this);
        String modeName =
                mode == AppState.MODE_BLOCK ? "BLOQUEAR DEX" :
                mode == AppState.MODE_FORWARD ? "ENVIAR A WINDOWS" :
                "OBSERVAR";

        modeStatus.setText("MODO: " + modeName);
        modeStatus.setTextColor(
                mode == AppState.MODE_FORWARD ? Color.GREEN :
                mode == AppState.MODE_BLOCK ? Color.YELLOW :
                Color.LTGRAY
        );

        status.setText(
                "Accessibility: " + (isAccessibilityEnabled() ? "ENABLED" : "DISABLED") + "\n" +
                "IME service:    " + (DexKeyboardImeService.isServiceAlive() ? "ALIVE" : "NOT ACTIVE") + "\n" +
                "InputConnection:" + (DexKeyboardImeService.hasInputConnection() ? " AVAILABLE" : " UNAVAILABLE") + "\n" +
                "Foreground:     " + empty(AppState.getForeground(this)) + "\n" +
                "Target:         " + AppState.WINDOWS_APP_PACKAGE + "\n" +
                "META detected:  " + (AppState.isMetaDetected(this) ? "YES" : "NO") + "\n" +
                "Alt+Tab seen:   " + (AppState.isAltTabDetected(this) ? "YES" : "NO") + "\n" +
                "Last injection: " + AppState.getLastInjection(this)
        );

        logView.setText(AppState.getLog(this));
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (enabled == null) return false;

        ComponentName me = new ComponentName(this, KeyboardAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);

        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (me.equals(cn)) return true;
        }

        return false;
    }

    private String empty(String s) {
        return s == null || s.isEmpty() ? "<none>" : s;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
