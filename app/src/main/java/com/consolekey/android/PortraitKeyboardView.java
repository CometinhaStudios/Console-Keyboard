package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PortraitKeyboardView extends BaseKeyboardView {
    private boolean upper = false;
    private int page = 0;
    private boolean emojiMode = false;

    private final Paint toolbar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolbarActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float toolbarH;

    public PortraitKeyboardView(Context c, Listener l) {
        super(c, l);

        setPadding((int)dp(6),(int)dp(6),(int)dp(6),(int)dp(6));
        toolbarH = dp(40);

        toolbar.setColor(Color.WHITE);
        toolbar.setTextAlign(Paint.Align.CENTER);
        toolbar.setTextSize(dp(18));

        toolbarActive.setColor(Color.rgb(70,70,70));

        rebuild();
    }

    private Key letterKey(String value) {
        String label = upper ? value.toUpperCase(Locale.ROOT) : value;
        return kAlt(
                label,
                label,
                1f,
                ACT_TEXT,
                KeyAlternates.forKey(value, upper)
        );
    }

    private Key normalKey(String value) {
        return kAlt(
                value,
                value,
                1f,
                ACT_TEXT,
                KeyAlternates.forKey(value, false)
        );
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

    private List<Key> row(String... values) {
        List<Key> out = new ArrayList<>();
        for (String s : values) out.add(normalKey(s));
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
        bottom.add(kRepeat("⌫", null, 1.2f, ACT_BACKSPACE));
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
            rows.add(numberRow());
            rows.add(letterRow("q","w","e","r","t","y","u","i","o","p"));
            rows.add(letterRow("a","s","d","f","g","h","j","k","l"));

            List<Key> r4 = new ArrayList<>();
            r4.add(k("⇧", null, 1.25f, ACT_SHIFT));
            for (String s : new String[]{"z","x","c","v","b","n","m"}) {
                r4.add(letterKey(s));
            }
            r4.add(kRepeat("⌫", null, 1.25f, ACT_BACKSPACE));
            rows.add(r4);

            List<Key> r5 = new ArrayList<>();
            r5.add(k("!#1", null, 1.45f, ACT_SYMBOLS));
            r5.add(normalKey(","));
            r5.add(k("Português (BR)", " ", 5.1f, ACT_SPACE));
            r5.add(normalKey("."));
            r5.add(k("↵", null, 1.45f, ACT_ENTER));
            rows.add(r5);

        } else if (page == 1) {
            rows.add(numberRow());
            rows.add(row("+","×","÷","=","/","_","<",">","[","]"));
            rows.add(row("!","@","#","$","%","^","&","*","(",")"));

            List<Key> r4 = new ArrayList<>();
            for (String s : new String[]{"-","'","\"",":",";",",","?"}) {
                r4.add(normalKey(s));
            }
            r4.add(kRepeat("⌫", null, 1.25f, ACT_BACKSPACE));
            rows.add(r4);

            List<Key> r5 = new ArrayList<>();
            r5.add(k("2/2", null, 1.45f, ACT_SYMBOLS));
            r5.add(k("Português (BR)", " ", 5.6f, ACT_SPACE));
            r5.add(normalKey("."));
            r5.add(k("↵", null, 1.45f, ACT_ENTER));
            rows.add(r5);

        } else {
            rows.add(numberRow());
            rows.add(row("`","~","\\","|","{","}","€","£","¥","₩"));
            rows.add(row("°","•","○","●","□","■","♠","♡","◇","♣"));

            List<Key> r4 = row("☆","▪","¤","《","》","¡","¿");
            r4.add(kRepeat("⌫", null, 1.25f, ACT_BACKSPACE));
            rows.add(r4);

            List<Key> r5 = new ArrayList<>();
            r5.add(k("ABC", null, 1.45f, ACT_SYMBOLS));
            r5.add(k("Português (BR)", " ", 5.6f, ACT_SPACE));
            r5.add(normalKey("."));
            r5.add(k("↵", null, 1.45f, ACT_ENTER));
            rows.add(r5);
        }

        invalidate();
    }

    @Override protected void perform(Key key) {
        if (key == null) return;

        if (emojiMode && key.action == ACT_SYMBOLS) {
            listener.onKeyFeedback();
            emojiMode = false;
            page = 0;
            rebuild();
            return;
        }

        if (key.action == ACT_SHIFT) {
            listener.onKeyFeedback();
            flashKey(key);
            upper = !upper;
            rebuild();
            return;
        }

        if (key.action == ACT_SYMBOLS) {
            listener.onKeyFeedback();
            flashKey(key);

            if (page == 0) page = 1;
            else if (page == 1) page = 2;
            else page = 0;

            rebuild();
            return;
        }

        boolean oneShotShift = upper && page == 0 && key.action == ACT_TEXT;
        super.perform(key);

        if (oneShotShift) {
            upper = false;
            rebuild();
        }
    }

    @Override protected void commitAlternate(Key key, String value) {
        super.commitAlternate(key, value);

        if (upper && page == 0) {
            upper = false;
            rebuild();
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.BLACK);

        String[] icons = {"☺","A↔","▣","⚙","•••"};
        float segment = getWidth() / (float)icons.length;

        if (emojiMode) {
            float left = segment * 0.5f - dp(24);
            float top = dp(4);
            float right = segment * 0.5f + dp(24);
            float bottom = toolbarH - dp(4);

            c.drawRoundRect(left, top, right, bottom, dp(15), dp(15), toolbarActive);
        }

        for (int i=0; i<icons.length; i++) {
            c.drawText(icons[i], segment * (i + .5f), dp(27), toolbar);
        }

        layoutRows(toolbarH + dp(6), getHeight() - dp(6));

        for (List<Key> row : rows) {
            for (Key key : row) {
                drawKey(c, key, false);
            }
        }

        drawLongPressPopup(c);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && e.getY() < toolbarH) {
            float seg = getWidth() / 5f;
            int i = Math.min(4, (int)(e.getX() / seg));

            listener.onKeyFeedback();

            if (i == 0) {
                emojiMode = !emojiMode;
                page = 0;
                rebuild();
            } else if (i == 3) {
                listener.onOpenSettings();
            }

            return true;
        }

        return super.onTouchEvent(e);
    }
}
