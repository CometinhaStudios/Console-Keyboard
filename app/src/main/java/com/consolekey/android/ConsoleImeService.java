package com.consolekey.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.InputDevice;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsoleImeService extends InputMethodService implements InputManagerCompat.Listener, BaseKeyboardView.Listener {
    public static final String PREFS = "keyboard_prefs";
    public static final String PREF_PORTRAIT_HEIGHT = "portrait_height";
    public static final String PREF_LANDSCAPE_HEIGHT = "landscape_height";
    public static final String PREF_LONG_PRESS_DELAY = "long_press_delay";

    private InputManagerCompat inputManager;
    private BaseKeyboardView keyboardView;
    private ControllerDetector.Family activeFamily = ControllerDetector.Family.GENERIC;
    private int activeControllerId = -1;
    private final ExecutorService hapticExecutor = Executors.newSingleThreadExecutor();

    @Override public void onCreate() {
        super.onCreate();

        inputManager = new InputManagerCompat(this, this);
        inputManager.register();

        refreshController();
    }

    @Override public void onDestroy() {
        if (inputManager != null) inputManager.unregister();
        hapticExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override public boolean onEvaluateFullscreenMode() {
        return false;
    }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int preferredHeightDp() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

        return p.getInt(
                landscape() ? PREF_LANDSCAPE_HEIGHT : PREF_PORTRAIT_HEIGHT,
                landscape() ? 150 : 235
        );
    }

    private void applyKeyboardHeight() {
        if (keyboardView != null) {
            keyboardView.setFixedHeightDp(preferredHeightDp());
        }
    }

    @Override public View onCreateInputView() {
        if (landscape()) {
            ConsoleKeyboardView v = new ConsoleKeyboardView(this, this);
            v.setControllerFamily(activeFamily);
            keyboardView = v;
        } else {
            keyboardView = new PortraitKeyboardView(this, this);
        }

        applyKeyboardHeight();
        return keyboardView;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        applyKeyboardHeight();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setInputView(onCreateInputView());
    }

    private void refreshController() {
        ControllerDetector.Family found = ControllerDetector.Family.GENERIC;
        int foundId = -1;

        int[] ids = InputDevice.getDeviceIds();

        for (int id : ids) {
            InputDevice d = InputDevice.getDevice(id);

            if (ControllerDetector.isGamepad(d)) {
                ControllerDetector.Family f = ControllerDetector.detect(d);

                if (foundId == -1) foundId = id;

                if (f != ControllerDetector.Family.GENERIC) {
                    found = f;
                    foundId = id;
                    break;
                }
            }
        }

        activeFamily = found;
        activeControllerId = foundId;

        if (keyboardView instanceof ConsoleKeyboardView) {
            ((ConsoleKeyboardView)keyboardView).setControllerFamily(found);
        }
    }

    private boolean vibrateGamepad() {
        if (activeControllerId < 0) return false;

        InputDevice d = InputDevice.getDevice(activeControllerId);
        if (!ControllerDetector.isGamepad(d)) return false;

        try {
            Vibrator v;

            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = d.getVibratorManager();
                v = vm.getDefaultVibrator();
            } else {
                v = d.getVibrator();
            }

            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(24, 115));
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private void vibratePhone() {
        try {
            Vibrator v;

            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(16, 85));
            }
        } catch (Throwable ignored) {}
    }

    @Override public void onKeyFeedback() {
        // Sem controle: usa o caminho de haptic nativo da própria View,
        // que é bem mais leve que consultar VibratorManager a cada tecla.
        if (activeControllerId < 0 && keyboardView != null) {
            try {
                if (keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) {
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // Rumble do gamepad/fallback não bloqueia a thread da digitação.
        try {
            hapticExecutor.execute(() -> {
                if (!vibrateGamepad()) vibratePhone();
            });
        } catch (Throwable ignored) {}
    }

    @Override public void onOpenSettings() {
        requestHideSelf(0);

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(i);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        InputDevice d = event.getDevice();

        if (ControllerDetector.isGamepad(d)) {
            activeControllerId = d.getId();
            activeFamily = ControllerDetector.detect(d);

            if (keyboardView instanceof ConsoleKeyboardView) {
                ConsoleKeyboardView v = (ConsoleKeyboardView)keyboardView;
                v.setControllerFamily(activeFamily);

                if (v.handleGamepadKey(keyCode, event)) return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        InputDevice d = event.getDevice();

        if (ControllerDetector.isGamepad(d) && keyboardView instanceof ConsoleKeyboardView) {
            ConsoleKeyboardView v = (ConsoleKeyboardView)keyboardView;

            if (v.handleGamepadKey(keyCode, event)) return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override public void onDeviceChanged() {
        refreshController();
    }

    private InputConnection ic() {
        return getCurrentInputConnection();
    }

    @Override public void onText(String text) {
        InputConnection c = ic();
        if (c != null) c.commitText(text, 1);
    }

    @Override public void onReplaceLast(String oldText, String newText) {
        InputConnection c = ic();
        if (c == null || newText == null) return;

        int oldLength = oldText == null ? 0 : oldText.length();

        if (oldLength > 0) {
            c.deleteSurroundingText(oldLength, 0);
        }

        c.commitText(newText, 1);
    }

    @Override public void onBackspace() {
        InputConnection c = ic();
        if (c != null) c.deleteSurroundingText(1, 0);
    }

    @Override public void onEnter() {
        InputConnection c = ic();
        if (c != null) {
            c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    @Override public void onSpace() {
        onText(" ");
    }

    @Override public void onHide() {
        requestHideSelf(0);
    }
}
