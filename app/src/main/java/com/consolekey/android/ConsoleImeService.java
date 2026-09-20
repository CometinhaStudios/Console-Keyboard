package com.consolekey.android;

import android.content.Context;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

public class ConsoleImeService extends InputMethodService implements InputManagerCompat.Listener, BaseKeyboardView.Listener {
    private InputManagerCompat inputManager;
    private BaseKeyboardView keyboardView;
    private ControllerDetector.Family activeFamily=ControllerDetector.Family.GENERIC;

    @Override public void onCreate() {
        super.onCreate();
        inputManager = new InputManagerCompat(this, this);
        inputManager.register();
        refreshController();
    }

    @Override public void onDestroy() {
        if(inputManager!=null) inputManager.unregister();
        super.onDestroy();
    }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override public View onCreateInputView() {
        if(landscape()) {
            ConsoleKeyboardView v=new ConsoleKeyboardView(this,this);
            v.setControllerFamily(activeFamily);
            keyboardView=v;
        } else {
            keyboardView=new PortraitKeyboardView(this,this);
        }
        keyboardView.setMinimumHeight(landscape()?dp(260):dp(420));
        return keyboardView;
    }

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setInputView(onCreateInputView());
    }

    private void refreshController() {
        ControllerDetector.Family found=ControllerDetector.Family.GENERIC;
        int[] ids=InputDevice.getDeviceIds();
        for(int id:ids){
            InputDevice d=InputDevice.getDevice(id);
            if(ControllerDetector.isGamepad(d)) {
                ControllerDetector.Family f=ControllerDetector.detect(d);
                if(f!=ControllerDetector.Family.GENERIC){found=f;break;}
            }
        }
        activeFamily=found;
        if(keyboardView instanceof ConsoleKeyboardView) ((ConsoleKeyboardView)keyboardView).setControllerFamily(found);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        InputDevice d=event.getDevice();
        if(ControllerDetector.isGamepad(d)) {
            activeFamily=ControllerDetector.detect(d);
            if(keyboardView instanceof ConsoleKeyboardView) {
                ConsoleKeyboardView v=(ConsoleKeyboardView)keyboardView;
                v.setControllerFamily(activeFamily);
                if(v.handleGamepadKey(keyCode,event)) return true;
            }
        }
        return super.onKeyDown(keyCode,event);
    }

    @Override public void onDeviceChanged() { refreshController(); }

    private InputConnection ic(){return getCurrentInputConnection();}
    @Override public void onText(String text){ if(ic()!=null) ic().commitText(text,1); }
    @Override public void onBackspace(){ if(ic()!=null) ic().deleteSurroundingText(1,0); }
    @Override public void onEnter(){ if(ic()!=null) ic().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); if(ic()!=null) ic().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER)); }
    @Override public void onSpace(){ onText(" "); }
    @Override public void onHide(){ requestHideSelf(0); }
}
