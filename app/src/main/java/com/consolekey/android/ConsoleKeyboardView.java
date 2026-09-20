package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

public class ConsoleKeyboardView extends BaseKeyboardView {
    private ControllerDetector.Family family = ControllerDetector.Family.GENERIC;
    private int selRow=1, selCol=0;
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float headerH;

    public ConsoleKeyboardView(Context c, Listener l) {
        super(c,l);
        setPadding((int)dp(8),(int)dp(6),(int)dp(8),(int)dp(6));
        gap=dp(4); radius=dp(6); headerH=dp(28);
        hint.setColor(Color.LTGRAY); hint.setTextSize(dp(11)); hint.setTextAlign(Paint.Align.LEFT);
        build();
    }

    private List<Key> row(String... a){ List<Key> out=new ArrayList<>(); for(String s:a) out.add(k(s)); return out; }

    private void build() {
        rows.clear();
        rows.add(row("1","2","3","4","5","6","7","8","9","0"));
        rows.add(row("q","w","e","r","t","y","u","i","o","p"));
        rows.add(row("a","s","d","f","g","h","j","k","l","ç"));
        rows.add(row("z","x","c","v","b","n","m",",",".","?"));
        List<Key> bottom=new ArrayList<>();
        bottom.add(k("⇧",null,1.0f,ACT_SHIFT));
        bottom.add(k("@#:",null,1.0f,ACT_SYMBOLS));
        bottom.add(k("SPACE",null,4.8f,ACT_SPACE));
        bottom.add(k("⌫",null,1.2f,ACT_BACKSPACE));
        bottom.add(k("DONE",null,1.5f,ACT_ENTER));
        rows.add(bottom);
    }

    public void setControllerFamily(ControllerDetector.Family f) {
        family=f==null?ControllerDetector.Family.GENERIC:f;
        invalidate();
    }

    private String badge(String action) {
        if (family==ControllerDetector.Family.PLAYSTATION) {
            switch(action){case"confirm":return"✕";case"back":return"○";case"delete":return"□";case"space":return"△";case"left":return"L1";case"right":return"R1";case"shift":return"L2";case"done":return"R2";}
        } else if (family==ControllerDetector.Family.XBOX) {
            switch(action){case"confirm":return"A";case"back":return"B";case"delete":return"X";case"space":return"Y";case"left":return"LB";case"right":return"RB";case"shift":return"LT";case"done":return"RT";}
        }
        switch(action){case"confirm":return"OK";case"back":return"BACK";case"delete":return"X";case"space":return"SP";case"left":return"L";case"right":return"R";case"shift":return"LT";case"done":return"RT";}
        return"";
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.drawColor(Color.rgb(8,8,8));
        String name = family==ControllerDetector.Family.PLAYSTATION ? "PLAYSTATION" : family==ControllerDetector.Family.XBOX ? "XBOX" : "CONTROLE";
        hint.setTextAlign(Paint.Align.LEFT); c.drawText("🎮 "+name+"  •  D-pad: navegar  •  "+badge("confirm")+": selecionar", dp(12), dp(20), hint);
        hint.setTextAlign(Paint.Align.RIGHT); c.drawText(badge("delete")+" apagar   "+badge("space")+" espaço   "+badge("done")+" concluir", getWidth()-dp(12), dp(20), hint);
        layoutRows(headerH, getHeight()-dp(7));
        for(int r=0;r<rows.size();r++) for(int col=0;col<rows.get(r).size();col++) drawKey(c,rows.get(r).get(col),r==selRow&&col==selCol);
    }

    private void clamp() {
        selRow=Math.max(0,Math.min(rows.size()-1,selRow));
        selCol=Math.max(0,Math.min(rows.get(selRow).size()-1,selCol));
        invalidate();
    }

    public boolean handleGamepadKey(int code, KeyEvent event) {
        if(event.getAction()!=KeyEvent.ACTION_DOWN || event.getRepeatCount()>0) return false;
        switch(code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: selCol--; clamp(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: selCol++; clamp(); return true;
            case KeyEvent.KEYCODE_DPAD_UP: selRow--; clamp(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: selRow++; clamp(); return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                perform(rows.get(selRow).get(selCol)); return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                listener.onKeyFeedback(); flashAction(ACT_BACKSPACE); listener.onBackspace(); return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                listener.onKeyFeedback(); flashAction(ACT_SPACE); listener.onSpace(); return true;
            case KeyEvent.KEYCODE_BUTTON_R2:
                listener.onKeyFeedback(); flashAction(ACT_ENTER); listener.onEnter(); return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                listener.onKeyFeedback(); listener.onHide(); return true;
        }
        return false;
    }
}
