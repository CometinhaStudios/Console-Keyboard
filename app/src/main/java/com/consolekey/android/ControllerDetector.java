package com.consolekey.android;

import android.view.InputDevice;

public final class ControllerDetector {
    public enum Family { XBOX, PLAYSTATION, GENERIC }

    private ControllerDetector() {}

    public static Family detect(InputDevice d) {
        if (d == null) return Family.GENERIC;
        String n = d.getName() == null ? "" : d.getName().toLowerCase();
        int vendor = d.getVendorId();

        if (vendor == 0x045E || n.contains("xbox") || n.contains("x-input") || n.contains("xinput")) {
            return Family.XBOX;
        }
        if (vendor == 0x054C || n.contains("dualshock") || n.contains("dualsense") || n.contains("wireless controller")) {
            return Family.PLAYSTATION;
        }
        return Family.GENERIC;
    }

    public static boolean isGamepad(InputDevice d) {
        if (d == null) return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
}
