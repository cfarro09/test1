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
    private boolean metaPhysicalDown = false;
    private boolean metaComboTriggered = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        AppState.appendLog(this, "ACCESSIBILITY CONNECTED v6");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
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
        int mode = AppState.getMode(this);

        // Handle physical Win/Meta itself.
        if (code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT) {
            if (down) {
                metaPhysicalDown = true;
                metaComboTriggered = false;
            } else if (up) {
                if (mode == AppState.MODE_FORWARD &&
                    targetMatches() &&
                    !metaComboTriggered) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            KeyEvent.KEYCODE_ESCAPE,
                            false,
                            false,
                            true,
                            false
                    );
                    AppState.appendLog(this, "FORWARD META alone -> CTRL+ESC result=" + ok);
                }
                metaPhysicalDown = false;
                metaComboTriggered = false;
            }

            if (shouldConsumeNow()) {
                AppState.appendLog(this, "CONSUME " + KeyEvent.keyCodeToString(code) +
                        " mode=" + mode);
                return true;
            }
            return false;
        }

        if (!shouldConsumeNow()) {
            return false;
        }

        // Alt+Tab must be evaluated BEFORE generic Meta combos.
        if (code == KeyEvent.KEYCODE_TAB &&
            event.isAltPressed() &&
            !event.isMetaPressed()) {

            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this, "BLOCK GLOBAL ALT+TAB");
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            KeyEvent.KEYCODE_PAGE_UP,
                            false,
                            true,
                            false,
                            false
                    );
                    AppState.appendLog(this, "FORWARD ALT+TAB -> ALT+PAGE_UP result=" + ok);
                }
            }
            return true;
        }

        // Consume physical Alt in block mode so DeX never sees Alt+Tab.
        if (mode == AppState.MODE_BLOCK &&
            (code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT)) {
            AppState.appendLog(this, "CONSUME ALT GLOBAL");
            return true;
        }

        // Generic Win shortcuts use ONLY the modifier state on this exact event.
        // This prevents stale ALT/META state from previous shortcuts.
        boolean eventHasMeta = event.isMetaPressed();

        if (eventHasMeta && !isModifier(code)) {
            metaComboTriggered = true;

            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this,
                            "BLOCK GLOBAL META+" + KeyEvent.keyCodeToString(code));
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            code,
                            event.isShiftPressed(),
                            event.isAltPressed(),
                            event.isCtrlPressed(),
                            true
                    );
                    AppState.appendLog(this,
                            "FORWARD META+" + KeyEvent.keyCodeToString(code) +
                            " shift=" + event.isShiftPressed() +
                            " alt=" + event.isAltPressed() +
                            " ctrl=" + event.isCtrlPressed() +
                            " result=" + ok);
                }
            }
            return true;
        }

        // While Meta is physically down, consume modifier transitions too.
        if (metaPhysicalDown && isModifier(code)) {
            AppState.appendLog(this,
                    "CONSUME modifier during physical META: " +
                    KeyEvent.keyCodeToString(code));
            return true;
        }

        return false;
    }

    private boolean shouldConsumeNow() {
        int mode = AppState.getMode(this);
        if (mode == AppState.MODE_BLOCK) return true;
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
        boolean interesting =
                isModifier(c) ||
                c == KeyEvent.KEYCODE_TAB ||
                e.isMetaPressed() ||
                e.isAltPressed() ||
                e.isCtrlPressed();

        if (!interesting) return;

        InputDevice d = InputDevice.getDevice(e.getDeviceId());
        String device = d == null ? "unknown" : d.getName();

        String action =
                e.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" :
                e.getAction() == KeyEvent.ACTION_UP ? "UP" :
                String.valueOf(e.getAction());

        String time = new SimpleDateFormat(
                "HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());

        AppState.appendLog(this,
                time +
                " " + action +
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
