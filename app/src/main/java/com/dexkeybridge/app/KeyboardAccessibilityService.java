package com.dexkeybridge.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KeyboardAccessibilityService extends AccessibilityService {
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        AppState.appendLog(this, "ACCESSIBILITY CONNECTED - global key filter requested");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally unused. This v2 only tests global physical keyboard events.
    }

    @Override
    public void onInterrupt() {
        AppState.appendLog(this, "ACCESSIBILITY INTERRUPTED");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;

        int code = event.getKeyCode();

        if (code == KeyEvent.KEYCODE_META_LEFT ||
            code == KeyEvent.KEYCODE_META_RIGHT ||
            event.isMetaPressed()) {
            AppState.setMetaDetected(this, true);
        }

        if (code == KeyEvent.KEYCODE_TAB && event.isAltPressed()) {
            AppState.setAltTabDetected(this, true);
        }

        if (shouldLog(event)) {
            AppState.appendLog(this, format(event));
        }

        // Diagnostic only: never consume the event.
        return false;
    }

    private boolean shouldLog(KeyEvent e) {
        int c = e.getKeyCode();

        if (c == KeyEvent.KEYCODE_META_LEFT || c == KeyEvent.KEYCODE_META_RIGHT ||
            c == KeyEvent.KEYCODE_ALT_LEFT || c == KeyEvent.KEYCODE_ALT_RIGHT ||
            c == KeyEvent.KEYCODE_CTRL_LEFT || c == KeyEvent.KEYCODE_CTRL_RIGHT ||
            c == KeyEvent.KEYCODE_SHIFT_LEFT || c == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            c == KeyEvent.KEYCODE_TAB || c == KeyEvent.KEYCODE_ESCAPE ||
            c == KeyEvent.KEYCODE_CAPS_LOCK ||
            c >= KeyEvent.KEYCODE_F1 && c <= KeyEvent.KEYCODE_F12) {
            return true;
        }

        // Log regular keys only when part of a shortcut.
        return e.isMetaPressed() || e.isAltPressed() || e.isCtrlPressed();
    }

    private String format(KeyEvent e) {
        InputDevice d = InputDevice.getDevice(e.getDeviceId());
        String device = d == null ? "unknown" : d.getName();
        int vendor = d == null ? -1 : d.getVendorId();
        int product = d == null ? -1 : d.getProductId();

        String action;
        if (e.getAction() == KeyEvent.ACTION_DOWN) action = "DOWN";
        else if (e.getAction() == KeyEvent.ACTION_UP) action = "UP";
        else action = String.valueOf(e.getAction());

        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());

        return time +
            " " + action +
            " key=" + KeyEvent.keyCodeToString(e.getKeyCode()) +
            " code=" + e.getKeyCode() +
            " scan=" + e.getScanCode() +
            " meta=0x" + Integer.toHexString(e.getMetaState()) +
            " [META=" + e.isMetaPressed() +
            " CTRL=" + e.isCtrlPressed() +
            " ALT=" + e.isAltPressed() +
            " SHIFT=" + e.isShiftPressed() + "]" +
            " repeat=" + e.getRepeatCount() +
            " deviceId=" + e.getDeviceId() +
            " device=\"" + device + "\"" +
            " vendor=" + vendor +
            " product=" + product +
            " source=0x" + Integer.toHexString(e.getSource());
    }
}
