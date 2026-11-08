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
    private boolean metaHeld = false;
    private boolean shiftHeld = false;
    private boolean altHeld = false;
    private boolean ctrlHeld = false;
    private boolean metaComboTriggered = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        AppState.appendLog(this, "ACCESSIBILITY CONNECTED v4");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // Preserve the last external foreground package so returning to DexKeyBridge
        // doesn't erase the package we actually want to target.
        if (!getPackageName().equals(pkg)) {
            AppState.setForeground(this, pkg);
        }
    }

    @Override
    public void onInterrupt() {
        AppState.appendLog(this, "ACCESSIBILITY INTERRUPTED");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        if (DexKeyboardImeService.shouldSuppressAccessibilityLoop()) return false;

        updateDetection(event);
        logInteresting(event);

        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;

        if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT) {
            if (down) {
                metaHeld = true;
                metaComboTriggered = false;
            } else if (up) {
                if (AppState.getMode(this) == AppState.MODE_FORWARD &&
                    targetMatches() &&
                    !metaComboTriggered) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            KeyEvent.KEYCODE_ESCAPE, false, false, true, false);
                    AppState.appendLog(this, "FORWARD META alone -> CTRL+ESC result=" + ok);
                }
                metaHeld = false;
                metaComboTriggered = false;
            }

            if (shouldConsumeNow()) {
                AppState.appendLog(this, "CONSUME " + KeyEvent.keyCodeToString(code) +
                        " mode=" + AppState.getMode(this));
                return true;
            }
            return false;
        }

        updateModifierState(code, down, up);

        if (!shouldConsumeNow()) return false;

        int mode = AppState.getMode(this);

        if (metaHeld && isModifier(code)) {
            AppState.appendLog(this, "CONSUME modifier during META: " + KeyEvent.keyCodeToString(code));
            return true;
        }

        if (metaHeld && !isModifier(code)) {
            metaComboTriggered = true;

            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this, "BLOCK GLOBAL META+" + KeyEvent.keyCodeToString(code));
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            code,
                            shiftHeld || event.isShiftPressed(),
                            altHeld || event.isAltPressed(),
                            ctrlHeld || event.isCtrlPressed(),
                            true
                    );
                    AppState.appendLog(this, "FORWARD META+" +
                            KeyEvent.keyCodeToString(code) + " result=" + ok);
                }
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_TAB && (altHeld || event.isAltPressed())) {
            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this, "BLOCK GLOBAL ALT+TAB");
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            KeyEvent.KEYCODE_PAGE_UP, false, true, false, false);
                    AppState.appendLog(this, "FORWARD ALT+TAB -> ALT+PAGE_UP result=" + ok);
                }
            }
            return true;
        }

        // In global block mode, consume ALT while held so DeX cannot see the full sequence.
        if (mode == AppState.MODE_BLOCK &&
            (code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT)) {
            AppState.appendLog(this, "CONSUME ALT GLOBAL");
            return true;
        }

        return false;
    }

    private boolean shouldConsumeNow() {
        int mode = AppState.getMode(this);
        if (mode == AppState.MODE_BLOCK) {
            return true; // v4 diagnostic: global block, independent of app package.
        }
        return mode == AppState.MODE_FORWARD && targetMatches();
    }

    private boolean targetMatches() {
        String foreground = AppState.getForeground(this);
        boolean match = AppState.WINDOWS_APP_PACKAGE.equals(foreground);
        if (match) {
            AppState.setTarget(this, AppState.WINDOWS_APP_PACKAGE);
        }
        return match;
    }

    private void updateDetection(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_META_LEFT ||
            code == KeyEvent.KEYCODE_META_RIGHT ||
            event.isMetaPressed()) {
            AppState.setMetaDetected(this, true);
        }
        if (code == KeyEvent.KEYCODE_TAB && event.isAltPressed()) {
            AppState.setAltTabDetected(this, true);
        }
    }

    private void updateModifierState(int code, boolean down, boolean up) {
        if (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (down) shiftHeld = true;
            if (up) shiftHeld = false;
        } else if (code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (down) altHeld = true;
            if (up) altHeld = false;
        } else if (code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (down) ctrlHeld = true;
            if (up) ctrlHeld = false;
        }
    }

    private boolean isModifier(int code) {
        return code == KeyEvent.KEYCODE_META_LEFT ||
               code == KeyEvent.KEYCODE_META_RIGHT ||
               code == KeyEvent.KEYCODE_SHIFT_LEFT ||
               code == KeyEvent.KEYCODE_SHIFT_RIGHT ||
               code == KeyEvent.KEYCODE_ALT_LEFT ||
               code == KeyEvent.KEYCODE_ALT_RIGHT ||
               code == KeyEvent.KEYCODE_CTRL_LEFT ||
               code == KeyEvent.KEYCODE_CTRL_RIGHT;
    }

    private void logInteresting(KeyEvent e) {
        int c = e.getKeyCode();
        boolean interesting = isModifier(c) ||
                c == KeyEvent.KEYCODE_TAB ||
                e.isMetaPressed() ||
                e.isAltPressed() ||
                e.isCtrlPressed();

        if (!interesting) return;

        InputDevice d = InputDevice.getDevice(e.getDeviceId());
        String device = d == null ? "unknown" : d.getName();

        String action = e.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" :
                e.getAction() == KeyEvent.ACTION_UP ? "UP" :
                String.valueOf(e.getAction());

        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());

        AppState.appendLog(this,
                time + " " + action +
                " key=" + KeyEvent.keyCodeToString(c) +
                " code=" + c +
                " scan=" + e.getScanCode() +
                " meta=0x" + Integer.toHexString(e.getMetaState()) +
                " [META=" + e.isMetaPressed() +
                " CTRL=" + e.isCtrlPressed() +
                " ALT=" + e.isAltPressed() +
                " SHIFT=" + e.isShiftPressed() + "]" +
                " mode=" + AppState.getMode(this) +
                " fg=" + AppState.getForeground(this) +
                " target=" + AppState.getTarget(this) +
                " device=\"" + device + "\"");
    }
}
