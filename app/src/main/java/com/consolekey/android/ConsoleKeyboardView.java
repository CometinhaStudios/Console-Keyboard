package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConsoleKeyboardView extends BaseKeyboardView {
    private ControllerDetector.Family family = ControllerDetector.Family.GENERIC;
    private int selRow = 1;
    private int selCol = 0;
    private boolean upper = false;

    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float headerH;

    public ConsoleKeyboardView(Context c, Listener l) {
        super(c, l);

        setPadding((int)dp(8),(int)dp(6),(int)dp(8),(int)dp(6));
        gap = dp(4);
        radius = dp(6);
        headerH = dp(28);

        hint.setColor(Color.LTGRAY);
        hint.setTextSize(dp(11));
        hint.setTextAlign(Paint.Align.LEFT);

        build();
    }

    private Key letterKey(String value) {
        String label = upper ? value.toUpperCase(Locale.ROOT) : value;
        return kAlt(label, label, 1f, ACT_TEXT, KeyAlternates.forKey(value, upper));
    }

    private Key normalKey(String value) {
        return kAlt(value, value, 1f, ACT_TEXT, KeyAlternates.forKey(value, false));
    }

    private List<Key> numberRow() {
        List<Key> out = new ArrayList<>();
        for (String s : new String[]{"1","2","3","4","5","6","7","8","9","0"}) {
            out.add(normalKey(s));
        }
        return out;
    }

    private List<Key> letterRow(String... values) {
        List<Key> out = new ArrayList<>();
        for (String s : values) out.add(letterKey(s));
        return out;
    }

    private void build() {
        rows.clear();

        rows.add(numberRow());
        rows.add(letterRow("q","w","e","r","t","y","u","i","o","p"));
        rows.add(letterRow("a","s","d","f","g","h","j","k","l"));
        rows.add(letterRow("z","x","c","v","b","n","m",",",".","?"));

        List<Key> bottom = new ArrayList<>();
        bottom.add(k("⇧", null, 1.0f, ACT_SHIFT));
        bottom.add(k("@#:", null, 1.0f, ACT_SYMBOLS));
        bottom.add(k("SPACE", null, 4.8f, ACT_SPACE));
        bottom.add(kRepeat("⌫", null, 1.2f, ACT_BACKSPACE));
        bottom.add(k("DONE", null, 1.5f, ACT_ENTER));
        rows.add(bottom);

        clamp();
    }

    @Override protected void perform(Key key) {
        if (key == null) return;

        if (key.action == ACT_SHIFT) {
            feedbackAsync();
            flashKey(key);
            upper = !upper;
            build();
            return;
        }

        boolean oneShotShift = upper && key.action == ACT_TEXT;
        super.perform(key);

        if (oneShotShift) {
            upper = false;
            build();
        }
    }

    @Override protected void commitAlternate(Key key, String value) {
        super.commitAlternate(key, value);

        if (upper) {
            upper = false;
            build();
        }
    }

    public void setControllerFamily(ControllerDetector.Family f) {
        family = f == null ? ControllerDetector.Family.GENERIC : f;
        invalidate();
    }

    private String badge(String action) {
        if (family == ControllerDetector.Family.PLAYSTATION) {
            switch (action) {
                case "confirm": return "✕";
                case "back": return "○";
                case "delete": return "□";
                case "space": return "△";
                case "left": return "L1";
                case "right": return "R1";
                case "shift": return "L2";
                case "done": return "R2";
            }
        } else if (family == ControllerDetector.Family.XBOX) {
            switch (action) {
                case "confirm": return "A";
                case "back": return "B";
                case "delete": return "X";
                case "space": return "Y";
                case "left": return "LB";
                case "right": return "RB";
                case "shift": return "LT";
                case "done": return "RT";
            }
        }

        switch (action) {
            case "confirm": return "OK";
            case "back": return "BACK";
            case "delete": return "X";
            case "space": return "SP";
            case "left": return "L";
            case "right": return "R";
            case "shift": return "LT";
            case "done": return "RT";
        }

        return "";
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.rgb(8,8,8));

        String name =
                family == ControllerDetector.Family.PLAYSTATION ? "PLAYSTATION" :
                family == ControllerDetector.Family.XBOX ? "XBOX" :
                "CONTROLE";

        hint.setTextAlign(Paint.Align.LEFT);
        c.drawText(
                "🎮 " + name + "  •  D-pad: navegar  •  " + badge("confirm") + ": selecionar",
                dp(12),
                dp(20),
                hint
        );

        hint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(
                badge("delete") + " apagar   " + badge("space") + " espaço   " + badge("done") + " concluir",
                getWidth() - dp(12),
                dp(20),
                hint
        );

        layoutRows(headerH, getHeight() - dp(7));

        for (int r=0; r<rows.size(); r++) {
            for (int col=0; col<rows.get(r).size(); col++) {
                drawKey(c, rows.get(r).get(col), r == selRow && col == selCol);
            }
        }

        drawLongPressPopup(c);
    }

    private Key selectedKey() {
        clamp();
        return rows.get(selRow).get(selCol);
    }

    private void clamp() {
        if (rows.isEmpty()) return;

        selRow = Math.max(0, Math.min(rows.size() - 1, selRow));
        selCol = Math.max(0, Math.min(rows.get(selRow).size() - 1, selCol));
        invalidate();
    }

    private boolean confirmCode(int code) {
        return code == KeyEvent.KEYCODE_BUTTON_A ||
               code == KeyEvent.KEYCODE_ENTER ||
               code == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    public boolean handleGamepadKey(int code, KeyEvent event) {
        if (event == null) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (confirmCode(code)) {
                if (event.getRepeatCount() == 0) beginGamepadPress(selectedKey());
                return true;
            }

            if (event.getRepeatCount() > 0) return true;

            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (isLongPressPopupOpen()) return movePopupSelection(-1);
                    selCol--;
                    clamp();
                    return true;

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (isLongPressPopupOpen()) return movePopupSelection(1);
                    selCol++;
                    clamp();
                    return true;

                case KeyEvent.KEYCODE_DPAD_UP:
                    if (isLongPressPopupOpen()) return true;
                    selRow--;
                    clamp();
                    return true;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (isLongPressPopupOpen()) return true;
                    selRow++;
                    clamp();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_X:
                    feedbackAsync();
                    flashAction(ACT_BACKSPACE);
                    listener.onBackspace();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_Y:
                    feedbackAsync();
                    flashAction(ACT_SPACE);
                    listener.onSpace();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_R2:
                    feedbackAsync();
                    flashAction(ACT_ENTER);
                    listener.onEnter();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_B:
                    feedbackAsync();
                    listener.onHide();
                    return true;
            }
        }

        if (event.getAction() == KeyEvent.ACTION_UP && confirmCode(code)) {
            endGamepadPress(selectedKey());
            return true;
        }

        return false;
    }
}
