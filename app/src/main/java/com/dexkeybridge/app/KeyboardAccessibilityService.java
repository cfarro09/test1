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
    private boolean altPhysicalDown = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        AppState.appendLog(this, "ACCESSIBILITY CONNECTED v7");
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

        boolean ownsNow = shouldConsumeNow();

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

            if (ownsNow) {
                AppState.appendLog(this, "CONSUME " + KeyEvent.keyCodeToString(code) +
                        " mode=" + mode);
                return true;
            }
            return false;
        }

        if (code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (down) altPhysicalDown = true;
            if (up) altPhysicalDown = false;

            if (ownsNow && (mode == AppState.MODE_BLOCK || altPhysicalDown || up)) {
                AppState.appendLog(this, "CONSUME ALT sequence");
                return true;
            }
            return false;
        }

        if (!ownsNow) {
            return false;
        }

        // Any sequence started with Win/Meta belongs to the Windows remote.
        // Forward every non-modifier key while Win is physically held.
        if (metaPhysicalDown && !isModifier(code)) {
            metaComboTriggered = true;

            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this,
                            "BLOCK GLOBAL WIN+" + KeyEvent.keyCodeToString(code));
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            code,
                            event.isShiftPressed(),
                            event.isAltPressed(),
                            event.isCtrlPressed(),
                            true
                    );
                    AppState.appendLog(this,
                            "FORWARD WIN+" + KeyEvent.keyCodeToString(code) +
                            " shift=" + event.isShiftPressed() +
                            " alt=" + event.isAltPressed() +
                            " ctrl=" + event.isCtrlPressed() +
                            " result=" + ok);
                }
            }
            return true;
        }

        // Keep all modifier transitions owned while a Win sequence is active.
        if (metaPhysicalDown && isModifier(code)) {
            AppState.appendLog(this,
                    "CONSUME modifier during WIN sequence: " +
                    KeyEvent.keyCodeToString(code));
            return true;
        }

        // Alt+Tab, Alt+Tab+Tab, Alt+Tab+Tab+Tab...
        // Keep the entire Alt sequence inside the remote session.
        if (altPhysicalDown && code == KeyEvent.KEYCODE_TAB) {
            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this, "BLOCK GLOBAL ALT+TAB step");
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendCombo(
                            KeyEvent.KEYCODE_PAGE_UP,
                            false,
                            true,
                            false,
                            false
                    );
                    AppState.appendLog(this,
                            "FORWARD ALT+TAB STEP -> ALT+PAGE_UP result=" + ok);
                }
            }
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
                " winPhysical=" + metaPhysicalDown +
                " altPhysical=" + altPhysicalDown +
                " device=\"" + device + "\"");
    }
}
