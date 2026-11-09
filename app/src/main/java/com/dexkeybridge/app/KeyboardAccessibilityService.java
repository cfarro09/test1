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
    private boolean remoteAltDown = false;
    private int altDeviceId = KeyEvent.KEYCODE_UNKNOWN;
    private int altSource = InputDevice.SOURCE_KEYBOARD;
    private boolean windowsKeyboardSessionActive = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        resetPhysicalState("SERVICE CONNECTED");
        String lastFg = AppState.getForeground(this);
        boolean persisted = AppState.isWindowsSessionActive(this);
        windowsKeyboardSessionActive =
                AppState.WINDOWS_APP_PACKAGE.equals(lastFg) ||
                (persisted && isTransientSystemPackage(lastFg));
        AppState.setWindowsSessionActive(this, windowsKeyboardSessionActive);
        AppState.appendLog(this,
                "ACCESSIBILITY CONNECTED v12 windowsSession=" +
                windowsKeyboardSessionActive);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        if (getPackageName().equals(pkg)) return;

        AppState.setForeground(this, pkg);

        // Any event from Windows App confirms the session is active.
        if (AppState.WINDOWS_APP_PACKAGE.equals(pkg)) {
            setWindowsKeyboardSession(true, "WINDOWS APP ACTIVE");
            return;
        }

        // Samsung DeX briefly reports SystemUI/launcher while Windows App is still
        // the user's working surface. Never drop the keyboard bridge for those.
        if (isTransientSystemPackage(pkg)) {
            if (windowsKeyboardSessionActive) {
                AppState.appendLog(this,
                        "WINDOWS SESSION KEEP through transient pkg=" + pkg);
            }
            return;
        }

        // Only a real foreground-window transition to another app ends the session.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            windowsKeyboardSessionActive) {
            releaseRemoteAltIfNeeded("LEFT WINDOWS TO " + pkg);
            resetPhysicalState("LEFT WINDOWS TO " + pkg);
            setWindowsKeyboardSession(false, "REAL APP " + pkg);
        }
    }

    @Override
    public void onInterrupt() {
        releaseRemoteAltIfNeeded("INTERRUPT");
        resetPhysicalState("INTERRUPT");
        AppState.appendLog(this, "ACCESSIBILITY INTERRUPTED");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;

        if (DexKeyboardImeService.isBridgeInjectedEvent(event)) {
            return false;
        }

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
        boolean forwardTarget = mode == AppState.MODE_FORWARD && targetNow;
        boolean blockMode = mode == AppState.MODE_BLOCK;

        if (isMetaKey(code)) {
            if (down) {
                metaPhysicalDown = true;
                metaComboTriggered = false;
            } else if (up) {
                if (forwardTarget && !metaComboTriggered) {
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

            if (blockMode || forwardTarget) {
                AppState.appendLog(this,
                        "CONSUME " + KeyEvent.keyCodeToString(code) + " mode=" + mode);
                return true;
            }
            return false;
        }

        if (isAltKey(code)) {
            if (down) {
                altPhysicalDown = true;
                altDeviceId = event.getDeviceId();
                altSource = event.getSource();

                if (forwardTarget && !altSequenceOwned) {
                    altSequenceOwned = true;
                    remoteAltDown = DexKeyboardImeService.sendModifierEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ALT_LEFT,
                            56,
                            altDeviceId,
                            altSource,
                            KeyEvent.META_ALT_ON
                    );
                    AppState.appendLog(this,
                            "ALT SEQUENCE OWNED remoteAltDown=" + remoteAltDown);
                }
            }

            if (up) {
                altPhysicalDown = false;
                if (altSequenceOwned || remoteAltDown) {
                    releaseRemoteAltIfNeeded("PHYSICAL ALT UP");
                    AppState.appendLog(this, "ALT SEQUENCE RELEASED");
                }
                altSequenceOwned = false;
            }

            if (blockMode || forwardTarget || altSequenceOwned || remoteAltDown) {
                AppState.appendLog(this, "CONSUME ALT sequence");
                return true;
            }
            return false;
        }

        if ((metaPhysicalDown || event.isMetaPressed()) && !isModifier(code)) {
            metaComboTriggered = true;

            if (blockMode || forwardTarget) {
                if (down && event.getRepeatCount() == 0) {
                    if (blockMode) {
                        AppState.appendLog(this,
                                "BLOCK GLOBAL WIN+" + KeyEvent.keyCodeToString(code));
                    } else {
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
        }

        if ((metaPhysicalDown || event.isMetaPressed()) && isModifier(code)) {
            if (blockMode || forwardTarget) {
                AppState.appendLog(this,
                        "CONSUME modifier during WIN sequence: " +
                        KeyEvent.keyCodeToString(code));
                return true;
            }
        }

        if (altSequenceOwned && !isModifier(code)) {
            if (down && event.getRepeatCount() == 0) {
                int remoteMeta = KeyEvent.META_ALT_ON;
                if (event.isShiftPressed()) remoteMeta |= KeyEvent.META_SHIFT_ON;
                if (event.isCtrlPressed()) remoteMeta |= KeyEvent.META_CTRL_ON;

                boolean ok = DexKeyboardImeService.sendKeyPairWithMeta(
                        code,
                        event.getScanCode(),
                        event.getDeviceId(),
                        event.getSource(),
                        remoteMeta
                );

                if (code == KeyEvent.KEYCODE_TAB) {
                    AppState.appendLog(this,
                            "FORWARD ALT+TAB STEP (held ALT) result=" + ok);
                } else {
                    AppState.appendLog(this,
                            "FORWARD ALT+" + KeyEvent.keyCodeToString(code) +
                            " (held ALT) result=" + ok);
                }
            } else if (up) {
                AppState.appendLog(this,
                        "CONSUME " + KeyEvent.keyCodeToString(code) +
                        " UP inside ALT sequence");
            }
            return true;
        }

        if (blockMode && (event.isAltPressed() || altPhysicalDown)) {
            AppState.appendLog(this,
                    "BLOCK GLOBAL ALT+" + KeyEvent.keyCodeToString(code));
            return true;
        }

        return false;
    }

    private void releaseRemoteAltIfNeeded(String reason) {
        if (!remoteAltDown) return;

        boolean ok = DexKeyboardImeService.sendModifierEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ALT_LEFT,
                56,
                altDeviceId,
                altSource,
                0
        );
        AppState.appendLog(this,
                "REMOTE ALT RELEASE reason=" + reason + " result=" + ok);
        remoteAltDown = false;
    }

    private void resetPhysicalState(String reason) {
        metaPhysicalDown = false;
        metaComboTriggered = false;
        altPhysicalDown = false;
        altSequenceOwned = false;
        remoteAltDown = false;
        altDeviceId = KeyEvent.KEYCODE_UNKNOWN;
        altSource = InputDevice.SOURCE_KEYBOARD;
        AppState.appendLog(this, "STATE RESET: " + reason);
    }

    private boolean targetMatches() {
        if (windowsKeyboardSessionActive) {
            AppState.setTarget(this, AppState.WINDOWS_APP_PACKAGE);
        }
        return windowsKeyboardSessionActive;
    }

    private void setWindowsKeyboardSession(boolean active, String reason) {
        if (windowsKeyboardSessionActive == active) return;
        windowsKeyboardSessionActive = active;
        AppState.setWindowsSessionActive(this, active);
        AppState.appendLog(this,
                "WINDOWS KEYBOARD SESSION " + (active ? "ON" : "OFF") +
                " reason=" + reason);
    }

    private boolean isTransientSystemPackage(String pkg) {
        return "com.android.systemui".equals(pkg) ||
               "com.sec.android.app.launcher".equals(pkg) ||
               "com.samsung.android.app.desktoplauncher".equals(pkg) ||
               "com.android.launcher3".equals(pkg);
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
                c == KeyEvent.KEYCODE_F4 ||
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
                " remoteAlt=" + remoteAltDown +
                " winSession=" + windowsKeyboardSessionActive +
                " device=\"" + device + "\"");
    }
}
