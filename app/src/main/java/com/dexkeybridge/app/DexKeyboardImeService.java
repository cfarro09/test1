package com.dexkeybridge.app;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

public class DexKeyboardImeService extends InputMethodService {
    private static volatile DexKeyboardImeService instance;
    private static volatile long suppressUntilMs = 0L;

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

    public static boolean isAvailable() {
        return instance != null && instance.getCurrentInputConnection() != null;
    }

    public static boolean shouldSuppressAccessibilityLoop() {
        return SystemClock.uptimeMillis() < suppressUntilMs;
    }

    public static boolean sendCombo(int keyCode, boolean shift, boolean alt, boolean ctrl, boolean meta) {
        DexKeyboardImeService s = instance;
        if (s == null) return false;
        InputConnection ic = s.getCurrentInputConnection();
        if (ic == null) return false;

        suppressUntilMs = SystemClock.uptimeMillis() + 300L;
        long downTime = SystemClock.uptimeMillis();
        boolean ok = true;

        if (ctrl) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON);
        if (alt) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | (ctrl ? KeyEvent.META_CTRL_ON : 0));
        if (shift) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | (ctrl ? KeyEvent.META_CTRL_ON : 0) | (alt ? KeyEvent.META_ALT_ON : 0));
        if (meta) ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, KeyEvent.META_META_ON | mods(shift, alt, ctrl, false));

        int fullMeta = mods(shift, alt, ctrl, meta);
        ok &= send(ic, downTime, KeyEvent.ACTION_DOWN, keyCode, fullMeta);
        ok &= send(ic, downTime, KeyEvent.ACTION_UP, keyCode, fullMeta);

        if (meta) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, mods(shift, alt, ctrl, false));
        if (shift) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, mods(false, alt, ctrl, false));
        if (alt) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, mods(false, false, ctrl, false));
        if (ctrl) ok &= send(ic, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0);

        AppState.setLastInjection(s, ok ? "SENDKEYEVENT returned true" : "SENDKEYEVENT returned false");
        AppState.appendLog(s, "OUTPUT: combo key=" + KeyEvent.keyCodeToString(keyCode) + " shift=" + shift + " alt=" + alt + " ctrl=" + ctrl + " meta=" + meta + " result=" + ok);
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

    private static boolean send(InputConnection ic, long downTime, int action, int keyCode, int metaState) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(downTime, now, action, keyCode, 0, metaState,
                KeyEvent.KEYCODE_UNKNOWN, 0, KeyEvent.FLAG_FROM_SYSTEM, 0);
        return ic.sendKeyEvent(event);
    }
}
