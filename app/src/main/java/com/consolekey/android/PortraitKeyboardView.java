package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PortraitKeyboardView extends BaseKeyboardView {
    private boolean upper = false;
    private int page = 0; // 0 letters, 1 symbols 1/2, 2 symbols 2/2
    private boolean emojiMode = false;
    private final Paint toolbar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolbarActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float toolbarH;

    public PortraitKeyboardView(Context c, Listener l) {
        super(c,l);
        setPadding((int)dp(6),(int)dp(6),(int)dp(6),(int)dp(6));
        toolbarH = dp(40);
        toolbar.setColor(Color.WHITE);
        toolbar.setTextAlign(Paint.Align.CENTER);
        toolbar.setTextSize(dp(18));
        toolbarActive.setColor(Color.rgb(70,70,70));
        rebuild();
    }

    private List<Key> row(String... a) {
        List<Key> out = new ArrayList<>();
        for (String s:a) out.add(k(s));
        return out;
    }

    private void rebuildEmoji() {
        rows.clear();
        rows.add(row("😀","😂","🥹","😍","😎","😭","😡","🤔"));
        rows.add(row("😊","😉","🥰","😘","😴","🙄","😅","🤣"));
        rows.add(row("👍","👎","👏","🙏","💪","👌","✌","🤝"));
        rows.add(row("❤️","💙","🔥","✨","🎉","💀","👀","✅"));
        List<Key> bottom = new ArrayList<>();
        bottom.add(k("ABC", null, 1.4f, ACT_SYMBOLS));
        bottom.add(k("🙂", "🙂", 1f, ACT_TEXT));
        bottom.add(k("SPACE", " ", 4.4f, ACT_SPACE));
        bottom.add(k("⌫", null, 1.2f, ACT_BACKSPACE));
        rows.add(bottom);
    }

    private void rebuild() {
        if (emojiMode) {
            rebuildEmoji();
            invalidate();
            return;
        }
        rows.clear();
        if (page == 0) {
            rows.add(row("1","2","3","4","5","6","7","8","9","0"));
            rows.add(row("q","w","e","r","t","y","u","i","o","p"));
            rows.add(row("a","s","d","f","g","h","j","k","l","ç"));
            List<Key> r4 = new ArrayList<>();
            r4.add(k("⇧", null, 1.25f, ACT_SHIFT));
            for (String s: Arrays.asList("z","x","c","v","b","n","m")) r4.add(k(s));
            r4.add(k("⌫", null, 1.25f, ACT_BACKSPACE));
            rows.add(r4);
            List<Key> r5 = new ArrayList<>();
            r5.add(k("!#1", null, 1.45f, ACT_SYMBOLS));
            r5.add(k("Português (BR)", " ", 5.6f, ACT_SPACE));
            r5.add(k(".", ".", 1f, ACT_TEXT));
            r5.add(k("↵", null, 1.45f, ACT_ENTER));
            rows.add(r5);
        } else if (page == 1) {
            rows.add(row("1","2","3","4","5","6","7","8","9","0"));
            rows.add(row("+","×","÷","=","/","_","<",">","[","]"));
            rows.add(row("!","@","#","$","%","^","&","*","(",")"));
            List<Key> r4=row("-","'","\"",":",";",",","?");
            r4.add(k("⌫", null, 1.25f, ACT_BACKSPACE)); rows.add(r4);
            List<Key> r5=new ArrayList<>();
            r5.add(k("2/2", null, 1.45f, ACT_SYMBOLS));
            r5.add(k("Português (BR)", " ", 5.6f, ACT_SPACE));
            r5.add(k(".", ".",1f,ACT_TEXT));
            r5.add(k("↵",null,1.45f,ACT_ENTER)); rows.add(r5);
        } else {
            rows.add(row("1","2","3","4","5","6","7","8","9","0"));
            rows.add(row("`","~","\\","|","{","}","€","£","¥","₩"));
            rows.add(row("°","•","○","●","□","■","♠","♡","◇","♣"));
            List<Key> r4=row("☆","▪","¤","《","》","¡","¿");
            r4.add(k("⌫",null,1.25f,ACT_BACKSPACE)); rows.add(r4);
            List<Key> r5=new ArrayList<>();
            r5.add(k("ABC",null,1.45f,ACT_SYMBOLS));
            r5.add(k("Português (BR)"," ",5.6f,ACT_SPACE));
            r5.add(k(".",".",1f,ACT_TEXT));
            r5.add(k("↵",null,1.45f,ACT_ENTER)); rows.add(r5);
        }
        invalidate();
    }

    @Override protected void perform(Key key) {
        if (emojiMode && key.action == ACT_SYMBOLS) {
            listener.onKeyFeedback();
            emojiMode = false;
            page = 0;
            rebuild();
            return;
        }
        if (key.action == ACT_SHIFT) {
            listener.onKeyFeedback();
            upper=!upper;
            flashKey(key);
            invalidate();
            return;
        }
        if (key.action == ACT_SYMBOLS) {
            listener.onKeyFeedback();
            flashKey(key);
            if (page==0) page=1; else if (page==1) page=2; else page=0;
            rebuild(); return;
        }
        if (key.action == ACT_TEXT && page==0 && !emojiMode && key.value != null && key.value.length()==1 && Character.isLetter(key.value.charAt(0))) {
            Key output = new Key(key.label, upper ? key.value.toUpperCase() : key.value, key.weight, ACT_TEXT);
            listener.onKeyFeedback();
            flashKey(key);
            listener.onText(output.value);
            if (upper) { upper=false; invalidate(); }
            return;
        }
        super.perform(key);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.BLACK);
        String[] icons={"☺","A↔","▣","⚙","•••"};
        float segment=getWidth()/(float)icons.length;
        if (emojiMode) {
            float left = segment * 0.5f - dp(24);
            float top = dp(4);
            float right = segment * 0.5f + dp(24);
            float bottom = toolbarH - dp(4);
            c.drawRoundRect(left, top, right, bottom, dp(15), dp(15), toolbarActive);
        }
        for (int i=0;i<icons.length;i++) c.drawText(icons[i], segment*(i+.5f), dp(27), toolbar);
        layoutRows(toolbarH+dp(6), getHeight()-dp(6));
        for (List<Key> row: rows) for (Key key: row) {
            String old=key.label;
            if (!emojiMode && page==0 && upper && old.length()==1 && Character.isLetter(old.charAt(0))) key.label=old.toUpperCase();
            drawKey(c,key,false);
            key.label=old;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction()==MotionEvent.ACTION_UP && e.getY() < toolbarH) {
            float seg=getWidth()/5f;
            int i=Math.min(4,(int)(e.getX()/seg));
            listener.onKeyFeedback();
            if (i==0) {
                emojiMode = !emojiMode;
                page = 0;
                rebuild();
            } else if (i==3) {
                listener.onOpenSettings();
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
}
