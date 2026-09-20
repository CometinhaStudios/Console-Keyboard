package com.consolekey.android;

import android.content.Context;
import android.hardware.input.InputManager;

public final class InputManagerCompat implements InputManager.InputDeviceListener {
    public interface Listener { void onDeviceChanged(); }
    private final InputManager manager;
    private final Listener listener;
    public InputManagerCompat(Context c, Listener l){manager=(InputManager)c.getSystemService(Context.INPUT_SERVICE);listener=l;}
    public void register(){manager.registerInputDeviceListener(this,null);}
    public void unregister(){manager.unregisterInputDeviceListener(this);}
    @Override public void onInputDeviceAdded(int deviceId){listener.onDeviceChanged();}
    @Override public void onInputDeviceRemoved(int deviceId){listener.onDeviceChanged();}
    @Override public void onInputDeviceChanged(int deviceId){listener.onDeviceChanged();}
}
