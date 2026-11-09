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
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView bridgeState;
    private TextView windowsState;
    private TextView setupState;
    private TextView diagnosticStatus;
    private TextView logView;
    private LinearLayout diagnosticsPanel;
    private Button bridgeToggle;
    private Button diagnosticsToggle;

    private boolean diagnosticsVisible = false;

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
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(root);

        TextView title = text("DexKeyBridge", 28, true);
        root.addView(title);

        TextView subtitle = text(
                "Teclado físico para Windows App en Samsung DeX",
                14,
                false);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        bridgeState = text("", 22, true);
        bridgeState.setPadding(0, dp(8), 0, dp(4));
        root.addView(bridgeState);

        windowsState = text("", 16, false);
        windowsState.setPadding(0, 0, 0, dp(12));
        root.addView(windowsState);

        bridgeToggle = button("");
        bridgeToggle.setOnClickListener(v -> {
            boolean active = AppState.getMode(this) == AppState.MODE_FORWARD;
            AppState.setMode(
                    this,
                    active ? AppState.MODE_OBSERVE : AppState.MODE_FORWARD
            );
            Toast.makeText(
                    this,
                    active ? "DexKeyBridge desactivado" : "DexKeyBridge activado",
                    Toast.LENGTH_SHORT
            ).show();
            refresh();
        });
        root.addView(bridgeToggle);

        setupState = text("", 14, false);
        setupState.setPadding(0, dp(16), 0, dp(6));
        root.addView(setupState);

        Button accessibility = button("Ajustes de Accesibilidad");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        Button imeSettings = button("Habilitar Keyboard Bridge");
        imeSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(imeSettings);

        Button imePicker = button("Seleccionar Keyboard Bridge");
        imePicker.setOnClickListener(v -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(imePicker);

        TextView help = text(
                "K380s: para F4 real usa Fn + F4. " +
                "Ejemplo para cerrar una ventana: Alt + Fn + F4.",
                13,
                false);
        help.setTextColor(Color.LTGRAY);
        help.setPadding(0, dp(14), 0, dp(14));
        root.addView(help);

        diagnosticsToggle = button("Diagnóstico >");
        diagnosticsToggle.setOnClickListener(v -> {
            diagnosticsVisible = !diagnosticsVisible;
            diagnosticsPanel.setVisibility(
                    diagnosticsVisible ? View.VISIBLE : View.GONE
            );
            diagnosticsToggle.setText(
                    diagnosticsVisible ? "Diagnóstico <" : "Diagnóstico >"
            );
        });
        root.addView(diagnosticsToggle);

        diagnosticsPanel = new LinearLayout(this);
        diagnosticsPanel.setOrientation(LinearLayout.VERTICAL);
        diagnosticsPanel.setVisibility(View.GONE);
        diagnosticsPanel.setPadding(0, dp(10), 0, 0);
        root.addView(diagnosticsPanel);

        diagnosticStatus = text("", 13, false);
        diagnosticStatus.setTypeface(Typeface.MONOSPACE);
        diagnosticsPanel.addView(diagnosticStatus);

        Button clear = button("Limpiar log");
        clear.setOnClickListener(v -> {
            AppState.clearLog(this);
            AppState.setLastInjection(this, "NOT TESTED");
            refresh();
        });
        diagnosticsPanel.addView(clear);

        Button copy = button("Copiar log");
        copy.setOnClickListener(v -> {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(
                        "DexKeyBridge log",
                        AppState.getLog(this)
                ));
                Toast.makeText(this, "Log copiado", Toast.LENGTH_SHORT).show();
            }
        });
        diagnosticsPanel.addView(copy);

        logView = text("", 11, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(10), 0, 0);
        diagnosticsPanel.addView(logView);

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
        boolean active = AppState.getMode(this) == AppState.MODE_FORWARD;
        boolean windowsSession = AppState.isWindowsSessionActive(this);
        boolean accessibility = isAccessibilityEnabled();
        boolean imeAlive = DexKeyboardImeService.isServiceAlive();
        boolean inputAvailable = DexKeyboardImeService.hasInputConnection();

        bridgeState.setText(active ? "● ACTIVO" : "○ INACTIVO");
        bridgeState.setTextColor(active ? Color.GREEN : Color.LTGRAY);

        if (!active) {
            windowsState.setText("Bridge desactivado");
            windowsState.setTextColor(Color.LTGRAY);
        } else if (windowsSession) {
            windowsState.setText("Windows App detectado · Teclado remoto ACTIVO");
            windowsState.setTextColor(Color.GREEN);
        } else {
            windowsState.setText("Esperando Windows App");
            windowsState.setTextColor(Color.LTGRAY);
        }

        bridgeToggle.setText(active ? "Desactivar Bridge" : "Activar Bridge");

        if (accessibility && imeAlive) {
            setupState.setText("Configuración: LISTA");
            setupState.setTextColor(Color.GREEN);
        } else {
            StringBuilder missing = new StringBuilder("Configuración pendiente:");
            if (!accessibility) missing.append(" Accesibilidad");
            if (!imeAlive) missing.append(" Keyboard Bridge");
            setupState.setText(missing.toString());
            setupState.setTextColor(Color.YELLOW);
        }

        diagnosticStatus.setText(
                "Accessibility:   " + (accessibility ? "ENABLED" : "DISABLED") + "\n" +
                "IME service:     " + (imeAlive ? "ALIVE" : "NOT ACTIVE") + "\n" +
                "InputConnection: " + (inputAvailable ? "AVAILABLE" : "UNAVAILABLE") + "\n" +
                "Windows session: " + (windowsSession ? "ON" : "OFF") + "\n" +
                "Foreground:      " + empty(AppState.getForeground(this)) + "\n" +
                "Target:          " + empty(AppState.getTarget(this)) + "\n" +
                "META detected:   " + (AppState.isMetaDetected(this) ? "YES" : "NO") + "\n" +
                "Alt+Tab seen:    " + (AppState.isAltTabDetected(this) ? "YES" : "NO") + "\n" +
                "Last injection:  " + AppState.getLastInjection(this)
        );

        logView.setText(AppState.getLog(this));
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;

        ComponentName me =
                new ComponentName(this, KeyboardAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);

        while (splitter.hasNext()) {
            ComponentName cn =
                    ComponentName.unflattenFromString(splitter.next());
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
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
