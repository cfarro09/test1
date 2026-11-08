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
import android.widget.Toast;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView modeStatus;
    private TextView logView;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
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
                "v5 — misma app.\n" +
                "Windows App se detecta automáticamente como com.microsoft.rdc.androidx.",
                14, false);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle);

        status = text("", 14, false);
        status.setTypeface(Typeface.MONOSPACE);
        root.addView(status);

        modeStatus = text("", 18, true);
        modeStatus.setPadding(0, dp(12), 0, dp(8));
        root.addView(modeStatus);

        Button observe = button("Modo 1: OBSERVAR");
        observe.setOnClickListener(v -> {
            AppState.setMode(this, AppState.MODE_OBSERVE);
            refresh();
        });
        root.addView(observe);

        Button block = button("Modo 2: BLOQUEAR DEX GLOBAL");
        block.setOnClickListener(v -> {
            AppState.setMode(this, AppState.MODE_BLOCK);
            Toast.makeText(this, "Bloqueo global activo", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(block);

        Button capture = button("Usar última app externa como TARGET");
        capture.setOnClickListener(v -> {
            String fg = AppState.getForeground(this);
            AppState.setTarget(this, fg);
            Toast.makeText(this, "Target: " + fg, Toast.LENGTH_LONG).show();
            refresh();
        });
        root.addView(capture);

        Button forward = button("Modo 3: ENVIAR AL TARGET");
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

        TextView instructions = text(
                "\nPRUEBA AHORA\n" +
                "1. Activa BLOQUEAR DEX GLOBAL.\n" +
                "2. Sal de la app y entra a Windows App.\n" +
                "3. Prueba Win+E y Alt+Tab.\n" +
                "4. Vuelve aquí y busca BLOCK GLOBAL / CONSUME en el log.\n\n" +
                "Si DeX ya no cambia de ventana, el bloqueo está resuelto.\n" +
                "Después habilita el Keyboard Bridge, selecciona ENVIAR AL TARGET y prueba Win+E / Alt+Tab dentro de Windows App.",
                14, false);
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
                cm.setPrimaryClip(ClipData.newPlainText(
                        "DexKeyBridge log", AppState.getLog(this)));
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

    @Override protected void onResume() {
        super.onResume();
        handler.post(refresher);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    private void refresh() {
        int mode = AppState.getMode(this);
        String modeName = mode == AppState.MODE_BLOCK ? "BLOQUEAR DEX GLOBAL" :
                mode == AppState.MODE_FORWARD ? "ENVIAR AL TARGET" : "OBSERVAR";

        modeStatus.setText("MODO: " + modeName);
        modeStatus.setTextColor(
                mode == AppState.MODE_FORWARD ? Color.GREEN :
                mode == AppState.MODE_BLOCK ? Color.YELLOW : Color.LTGRAY);

        status.setText(
                "Accessibility:  " + (isAccessibilityEnabled() ? "ENABLED" : "DISABLED") + "\n" +
                "IME service:    " + (DexKeyboardImeService.isServiceAlive() ? "ALIVE" : "NOT ACTIVE") + "\n" +
                "InputConnection:" + (DexKeyboardImeService.hasInputConnection() ? " AVAILABLE" : " UNAVAILABLE") + "\n" +
                "Last external:  " + empty(AppState.getForeground(this)) + "\n" +
                "Target:         " + empty(AppState.getTarget(this)) + "\n" +
                "META detected:  " + (AppState.isMetaDetected(this) ? "YES" : "NO") + "\n" +
                "Alt+Tab seen:   " + (AppState.isAltTabDetected(this) ? "YES" : "NO") + "\n" +
                "Last injection: " + AppState.getLastInjection(this)
        );

        logView.setText(AppState.getLog(this));
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
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
