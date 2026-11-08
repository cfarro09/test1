package com.dexkeybridge.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final StringBuilder log = new StringBuilder();
    private TextView metaStatus;
    private TextView altTabStatus;
    private TextView lastEvent;
    private TextView logView;
    private boolean metaSeen = false;
    private boolean altTabSeen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        TextView title = text("DexKeyBridge Lite", 28, true);
        root.addView(title);

        TextView subtitle = text(
                "Diagnóstico local de teclado físico para Samsung DeX.\n" +
                "No solicita Accesibilidad, IME, Internet ni permisos especiales.",
                14, false);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        metaStatus = text("", 18, true);
        altTabStatus = text("", 18, true);
        lastEvent = text("Último evento: <ninguno>", 14, false);
        lastEvent.setTypeface(Typeface.MONOSPACE);

        root.addView(metaStatus);
        root.addView(altTabStatus);
        root.addView(lastEvent);

        TextView instructions = text(
                "\nPrueba en DeX, con esta app enfocada:\n" +
                "• tecla Windows / Meta\n" +
                "• Win + E\n" +
                "• Win + R\n" +
                "• Alt + Tab\n" +
                "• Ctrl, Alt, Shift, Esc\n\n" +
                "Si META cambia a PASS, Samsung está entregando la tecla Windows a una app Android normal.\n" +
                "Si no cambia, DeX probablemente la consume antes.",
                14, false);
        root.addView(instructions);

        Button clear = new Button(this);
        clear.setText("Limpiar diagnóstico");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> {
            log.setLength(0);
            metaSeen = false;
            altTabSeen = false;
            lastEvent.setText("Último evento: <ninguno>");
            refreshStatus();
            refreshLog();
        });
        root.addView(clear);

        Button copy = new Button(this);
        copy.setText("Copiar log");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("DexKeyBridge Lite", log.toString()));
            }
        });
        root.addView(copy);

        logView = text("", 11, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(12), 0, 0);
        root.addView(logView);

        refreshStatus();
        setContentView(scroll);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        record(event);
        // Important: do not consume anything in Lite mode.
        return super.dispatchKeyEvent(event);
    }

    private void record(KeyEvent e) {
        if (e == null) return;

        int code = e.getKeyCode();

        if (code == KeyEvent.KEYCODE_META_LEFT ||
                code == KeyEvent.KEYCODE_META_RIGHT ||
                e.isMetaPressed()) {
            metaSeen = true;
        }

        if (code == KeyEvent.KEYCODE_TAB && e.isAltPressed()) {
            altTabSeen = true;
        }

        InputDevice d = InputDevice.getDevice(e.getDeviceId());
        String deviceName = d == null ? "unknown" : d.getName();
        int vendor = d == null ? -1 : d.getVendorId();
        int product = d == null ? -1 : d.getProductId();

        String action;
        if (e.getAction() == KeyEvent.ACTION_DOWN) action = "DOWN";
        else if (e.getAction() == KeyEvent.ACTION_UP) action = "UP";
        else action = String.valueOf(e.getAction());

        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());

        String line =
                time +
                " " + action +
                " key=" + KeyEvent.keyCodeToString(code) +
                " code=" + code +
                " scan=" + e.getScanCode() +
                " meta=0x" + Integer.toHexString(e.getMetaState()) +
                " [META=" + e.isMetaPressed() +
                " CTRL=" + e.isCtrlPressed() +
                " ALT=" + e.isAltPressed() +
                " SHIFT=" + e.isShiftPressed() + "]" +
                " repeat=" + e.getRepeatCount() +
                " deviceId=" + e.getDeviceId() +
                " device=\"" + deviceName + "\"" +
                " vendor=" + vendor +
                " product=" + product +
                " source=0x" + Integer.toHexString(e.getSource());

        if (log.length() > 30000) {
            log.delete(0, 12000);
        }
        log.append(line).append('\n');

        runOnUiThread(() -> {
            lastEvent.setText("Último evento:\n" + line);
            refreshStatus();
            refreshLog();
        });
    }

    private void refreshStatus() {
        metaStatus.setText("META / WIN: " + (metaSeen ? "PASS" : "NOT SEEN"));
        metaStatus.setTextColor(metaSeen ? Color.GREEN : Color.YELLOW);

        altTabStatus.setText("ALT + TAB: " + (altTabSeen ? "PASS" : "NOT SEEN"));
        altTabStatus.setTextColor(altTabSeen ? Color.GREEN : Color.YELLOW);
    }

    private void refreshLog() {
        logView.setText(log.toString());
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
