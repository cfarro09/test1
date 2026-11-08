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
    private boolean rightCtrlDown = false;
    private boolean shiftDown = false;
    private boolean altDown = false;
    private boolean metaDown = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        AppState.appendLog(this, "ACCESSIBILITY: connected; key filtering requested");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg != null) AppState.setForeground(this, pkg.toString());
    }

    @Override
    public void onInterrupt() {
        AppState.appendLog(this, "ACCESSIBILITY: interrupted");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        if (DexKeyboardImeService.shouldSuppressAccessibilityLoop()) return false;

        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;

        if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT || event.isMetaPressed()) {
            AppState.setMetaDetected(this, true);
        }
        if (code == KeyEvent.KEYCODE_TAB && event.isAltPressed()) {
            AppState.setAltTabDetected(this, true);
        }

        logEvent(event);

        if (code == KeyEvent.KEYCODE_CTRL_RIGHT) rightCtrlDown = down ? true : (up ? false : rightCtrlDown);
        if (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT) shiftDown = down ? true : (up ? false : shiftDown);
        if (code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT) altDown = down ? true : (up ? false : altDown);
        if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT) metaDown = down ? true : (up ? false : metaDown);

        if (!shouldRemapNow()) return false;

        if (code == KeyEvent.KEYCODE_CTRL_RIGHT) return true;
        if (rightCtrlDown && (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT)) return true;

        if (rightCtrlDown && isWindowsShortcutKey(code)) {
            if (down && event.getRepeatCount() == 0) {
                boolean ok = DexKeyboardImeService.sendCombo(code, shiftDown || event.isShiftPressed(), false, false, true);
                AppState.appendLog(this, "MAPPING: RIGHT_CTRL+" + KeyEvent.keyCodeToString(code) + " -> META+" + KeyEvent.keyCodeToString(code) + " result=" + ok);
            }
            return true;
        }

        if ((metaDown || event.isMetaPressed()) && isWindowsShortcutKey(code)) {
            if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT) return true;
            if (down && event.getRepeatCount() == 0) {
                boolean ok = DexKeyboardImeService.sendCombo(code, shiftDown || event.isShiftPressed(), false, false, true);
                AppState.appendLog(this, "MAPPING: META+" + KeyEvent.keyCodeToString(code) + " -> META+" + KeyEvent.keyCodeToString(code) + " result=" + ok);
            }
            return true;
        }

        if ((altDown || event.isAltPressed()) && code == KeyEvent.KEYCODE_TAB) {
            if (down && event.getRepeatCount() == 0) {
                boolean ok = DexKeyboardImeService.sendCombo(KeyEvent.KEYCODE_PAGE_UP, false, true, false, false);
                AppState.appendLog(this, "MAPPING: ALT+TAB -> ALT+PAGE_UP result=" + ok);
            }
            return true;
        }

        return false;
    }

    private boolean shouldRemapNow() {
        if (!AppState.isWindowsMode(this)) return false;
        String target = AppState.getTarget(this);
        if (target == null || target.trim().isEmpty()) return true;
        return target.equals(AppState.getForeground(this));
    }

    private boolean isWindowsShortcutKey(int code) {
        return code == KeyEvent.KEYCODE_E || code == KeyEvent.KEYCODE_R || code == KeyEvent.KEYCODE_D || code == KeyEvent.KEYCODE_S;
    }

    private void logEvent(KeyEvent e) {
        InputDevice d = InputDevice.getDevice(e.getDeviceId());
        String device = d == null ? "unknown" : d.getName();
        int vendor = d == null ? -1 : d.getVendorId();
        int product = d == null ? -1 : d.getProductId();
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String action = e.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : e.getAction() == KeyEvent.ACTION_UP ? "UP" : String.valueOf(e.getAction());
        String line = time + " INPUT " + action
                + " key=" + KeyEvent.keyCodeToString(e.getKeyCode())
                + " keyCode=" + e.getKeyCode()
                + " scan=" + e.getScanCode()
                + " meta=0x" + Integer.toHexString(e.getMetaState())
                + " [META=" + e.isMetaPressed() + " CTRL=" + e.isCtrlPressed() + " ALT=" + e.isAltPressed() + " SHIFT=" + e.isShiftPressed() + "]"
                + " repeat=" + e.getRepeatCount()
                + " deviceId=" + e.getDeviceId()
                + " device=\"" + device + "\""
                + " vendor=" + vendor
                + " product=" + product
                + " source=0x" + Integer.toHexString(e.getSource());
        AppState.appendLog(this, line);
    }
}
