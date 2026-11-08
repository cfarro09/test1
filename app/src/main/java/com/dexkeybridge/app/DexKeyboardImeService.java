package com.dexkeybridge.app;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

public class DexKeyboardImeService extends InputMethodService {
    private static volatile DexKeyboardImeService instance;
    private static volatile long suppressUntilMs = 0L;

    private static final int SCAN_SHIFT_LEFT = 42;
    private static final int SCAN_CTRL_LEFT = 29;
    private static final int SCAN_ALT_LEFT = 56;
    private static final int SCAN_META_LEFT = 125;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        AppState.appendLog(this, "IME: service created");
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isServiceAlive() {
        return instance != null;
    }

    public static boolean hasInputConnection() {
        DexKeyboardImeService s = instance;
        return s != null && s.getCurrentInputConnection() != null;
    }

    public static boolean shouldSuppressAccessibilityLoop() {
        return SystemClock.uptimeMillis() < suppressUntilMs;
    }

    public static boolean sendScancodeCombo(
            int keyCode,
            int scanCode,
            int deviceId,
            int source,
            boolean shift,
            boolean alt,
            boolean ctrl,
            boolean meta) {

        DexKeyboardImeService s = instance;
        if (s == null) return false;

        InputConnection ic = s.getCurrentInputConnection();
        if (ic == null) {
            AppState.setLastInjection(s, "NO INPUT CONNECTION");
            AppState.appendLog(s, "OUTPUT FAIL: no InputConnection");
            return false;
        }

        suppressUntilMs = SystemClock.uptimeMillis() + 600L;
        long downTime = SystemClock.uptimeMillis();
        boolean ok = true;

        if (ctrl) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.META_CTRL_ON, deviceId, SCAN_CTRL_LEFT, source);
        if (alt) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.META_ALT_ON | (ctrl ? KeyEvent.META_CTRL_ON : 0),
                deviceId, SCAN_ALT_LEFT, source);
        if (shift) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.META_SHIFT_ON |
                (ctrl ? KeyEvent.META_CTRL_ON : 0) |
                (alt ? KeyEvent.META_ALT_ON : 0),
                deviceId, SCAN_SHIFT_LEFT, source);
        if (meta) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.META_META_ON | mods(shift, alt, ctrl, false),
                deviceId, SCAN_META_LEFT, source);

        int fullMeta = mods(shift, alt, ctrl, meta);
        ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, keyCode, fullMeta,
                deviceId, scanCode, source);
        ok &= send(ic, downTime, KeyEvent.ACTION_UP, keyCode, fullMeta,
                deviceId, scanCode, source);

        if (meta) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT,
                mods(shift, alt, ctrl, false), deviceId, SCAN_META_LEFT, source);
        if (shift) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT,
                mods(false, alt, ctrl, false), deviceId, SCAN_SHIFT_LEFT, source);
        if (alt) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT,
                mods(false, false, ctrl, false), deviceId, SCAN_ALT_LEFT, source);
        if (ctrl) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT,
                0, deviceId, SCAN_CTRL_LEFT, source);

        String result = ok ? "SCANCODE TRUE" : "SCANCODE FALSE";
        AppState.setLastInjection(s, result);
        AppState.appendLog(s,
                "OUTPUT SCANCODE key=" + KeyEvent.keyCodeToString(keyCode) +
                " scan=" + scanCode +
                " device=" + deviceId +
                " source=0x" + Integer.toHexString(source) +
                " shift=" + shift +
                " alt=" + alt +
                " ctrl=" + ctrl +
                " meta=" + meta +
                " result=" + ok);
        return ok;
    }

    private static int mods(boolean shift, boolean alt, boolean ctrl, boolean meta) {
        int m = 0;
        if (shift) m |= KeyEvent.META_SHIFT_ON;
        if (alt) m |= KeyEvent.META_ALT_ON;
        if (ctrl) m |= KeyEvent.META_CTRL_ON;
        if (meta) m |= KeyEvent.META_META_ON;
        return m;
    }

    private static boolean send(
            InputConnection ic,
            long downTime,
            int action,
            int keyCode,
            int metaState,
            int deviceId,
            int scanCode,
            int source) {

        long now = SystemClock.uptimeMillis();
        int actualSource = source != 0 ? source : InputDevice.SOURCE_KEYBOARD;

        KeyEvent event = new KeyEvent(
                downTime,
                now,
                action,
                keyCode,
                0,
                metaState,
                deviceId,
                scanCode,
                KeyEvent.FLAG_FROM_SYSTEM,
                actualSource
        );
        return ic.sendKeyEvent(event);
    }
}
