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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView serviceStatus;
    private TextView metaStatus;
    private TextView altTabStatus;
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
            "Prueba global de teclado para Samsung DeX.\n" +
            "Solo usa AccessibilityService para observar eventos.\n" +
            "No inyecta teclas, no usa Internet y nunca consume los eventos.",
            14, false
        );
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        serviceStatus = text("", 17, true);
        metaStatus = text("", 18, true);
        altTabStatus = text("", 18, true);
        root.addView(serviceStatus);
        root.addView(metaStatus);
        root.addView(altTabStatus);

        Button settings = new Button(this);
        settings.setText("Activar servicio de Accesibilidad");
        settings.setAllCaps(false);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings);

        TextView instructions = text(
            "\nDespués de activarlo, puedes salir de esta app y probar en cualquier lugar de DeX:\n\n" +
            "1. Win sola\n" +
            "2. Win + E\n" +
            "3. Win + R\n" +
            "4. Alt + Tab\n\n" +
            "Luego vuelve aquí. Si Android entrega esos eventos al servicio, verás PASS y el log correspondiente.",
            14, false
        );
        root.addView(instructions);

        Button clear = new Button(this);
        clear.setText("Limpiar diagnóstico");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> {
            AppState.clearLog(this);
            AppState.setMetaDetected(this, false);
            AppState.setAltTabDetected(this, false);
            refresh();
        });
        root.addView(clear);

        Button copy = new Button(this);
        copy.setText("Copiar log");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("DexKeyBridge Diagnostic v2", AppState.getLog(this)));
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
        boolean enabled = isServiceEnabled();
        boolean meta = AppState.isMetaDetected(this);
        boolean altTab = AppState.isAltTabDetected(this);

        serviceStatus.setText("Accessibility: " + (enabled ? "ENABLED" : "DISABLED"));
        serviceStatus.setTextColor(enabled ? Color.GREEN : Color.YELLOW);

        metaStatus.setText("META / WIN GLOBAL: " + (meta ? "PASS" : "NOT SEEN"));
        metaStatus.setTextColor(meta ? Color.GREEN : Color.YELLOW);

        altTabStatus.setText("ALT + TAB GLOBAL: " + (altTab ? "PASS" : "NOT SEEN"));
        altTabStatus.setTextColor(altTab ? Color.GREEN : Color.YELLOW);

        logView.setText(AppState.getLog(this));
    }

    private boolean isServiceEnabled() {
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
