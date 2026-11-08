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
    private boolean altSequenceOwned = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        resetPhysicalState("SERVICE CONNECTED");
        AppState.appendLog(this, "ACCESSIBILITY CONNECTED v10");
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
        resetPhysicalState("INTERRUPT");
        AppState.appendLog(this, "ACCESSIBILITY INTERRUPTED");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        if (DexKeyboardImeService.shouldSuppressAccessibilityLoop()) return false;

        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        int mode = AppState.getMode(this);

        if (!isMetaKey(code) && !event.isMetaPressed() && metaPhysicalDown) {
            metaPhysicalDown = false;
            metaComboTriggered = false;
            AppState.appendLog(this, "STATE HEAL: META -> false");
        }

        if (!isAltKey(code) && !event.isAltPressed() && altPhysicalDown && !altSequenceOwned) {
            altPhysicalDown = false;
            AppState.appendLog(this, "STATE HEAL: ALT -> false");
        }

        updateDetection(event);
        logInteresting(event);

        boolean targetNow = targetMatches();
        boolean ownsNow = mode == AppState.MODE_BLOCK ||
                (mode == AppState.MODE_FORWARD && targetNow) ||
                altSequenceOwned;

        if (isMetaKey(code)) {
            if (down) {
                metaPhysicalDown = true;
                metaComboTriggered = false;
            } else if (up) {
                if (mode == AppState.MODE_FORWARD &&
                    targetNow &&
                    !metaComboTriggered) {
                    boolean ok = DexKeyboardImeService.sendScancodeCombo(
                            KeyEvent.KEYCODE_ESCAPE,
                            1,
                            event.getDeviceId(),
                            event.getSource(),
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
                AppState.appendLog(this,
                        "CONSUME " + KeyEvent.keyCodeToString(code) + " mode=" + mode);
                return true;
            }
            return false;
        }

        if (isAltKey(code)) {
            if (down) {
                altPhysicalDown = true;
                if (targetNow && mode == AppState.MODE_FORWARD) {
                    altSequenceOwned = true;
                    AppState.appendLog(this, "ALT SEQUENCE OWNED");
                }
            }

            if (up) {
                altPhysicalDown = false;
                if (altSequenceOwned) {
                    AppState.appendLog(this, "ALT SEQUENCE RELEASED");
                }
                altSequenceOwned = false;
            }

            if (ownsNow || altSequenceOwned) {
                AppState.appendLog(this, "CONSUME ALT sequence");
                return true;
            }
            return false;
        }

        if (!ownsNow) {
            return false;
        }

        // Everything started with Win stays unchanged: this path already works.
        if ((metaPhysicalDown || event.isMetaPressed()) && !isModifier(code)) {
            metaComboTriggered = true;

            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this,
                            "BLOCK GLOBAL WIN+" + KeyEvent.keyCodeToString(code));
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendScancodeCombo(
                            code,
                            event.getScanCode(),
                            event.getDeviceId(),
                            event.getSource(),
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

        if ((metaPhysicalDown || event.isMetaPressed()) && isModifier(code)) {
            AppState.appendLog(this,
                    "CONSUME modifier during WIN sequence: " +
                    KeyEvent.keyCodeToString(code));
            return true;
        }

        // Alt+Tab sequence: keep consuming every Tab DOWN and UP until Alt is released.
        // Now that stale META is healed, forward a clean physical Alt+Tab step.
        if ((altPhysicalDown || altSequenceOwned || event.isAltPressed()) &&
            code == KeyEvent.KEYCODE_TAB) {

            if (down && event.getRepeatCount() == 0) {
                if (mode == AppState.MODE_BLOCK) {
                    AppState.appendLog(this, "BLOCK GLOBAL ALT+TAB step");
                } else if (mode == AppState.MODE_FORWARD) {
                    boolean ok = DexKeyboardImeService.sendScancodeCombo(
                            KeyEvent.KEYCODE_TAB,
                            event.getScanCode(),
                            event.getDeviceId(),
                            event.getSource(),
                            false,
                            true,
                            false,
                            false
                    );
                    AppState.appendLog(this,
                            "FORWARD ALT+TAB STEP -> PHYSICAL ALT+TAB result=" + ok);
                }
            } else if (up) {
                AppState.appendLog(this, "CONSUME TAB UP inside ALT sequence");
            }
            return true;
        }

        return false;
    }

    private void resetPhysicalState(String reason) {
        metaPhysicalDown = false;
        metaComboTriggered = false;
        altPhysicalDown = false;
        altSequenceOwned = false;
        AppState.appendLog(this, "STATE RESET: " + reason);
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

        if (isMetaKey(code) || event.isMetaPressed()) {
            AppState.setMetaDetected(this, true);
        }

        if (code == KeyEvent.KEYCODE_TAB && event.isAltPressed()) {
            AppState.setAltTabDetected(this, true);
        }
    }

    private boolean isMetaKey(int code) {
        return code == KeyEvent.KEYCODE_META_LEFT ||
               code == KeyEvent.KEYCODE_META_RIGHT;
    }

    private boolean isAltKey(int code) {
        return code == KeyEvent.KEYCODE_ALT_LEFT ||
               code == KeyEvent.KEYCODE_ALT_RIGHT;
    }

    private boolean isModifier(int code) {
        return isMetaKey(code) ||
               isAltKey(code) ||
               code == KeyEvent.KEYCODE_SHIFT_LEFT ||
               code == KeyEvent.KEYCODE_SHIFT_RIGHT ||
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
                " altOwned=" + altSequenceOwned +
                " device=\"" + device + "\"");
    }
}
