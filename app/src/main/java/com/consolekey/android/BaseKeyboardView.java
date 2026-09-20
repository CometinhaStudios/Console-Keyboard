package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseKeyboardView extends View {
    public interface Listener {
        void onText(String text);
        void onBackspace();
        void onEnter();
        void onSpace();
        void onHide();
        void onKeyFeedback();
        void onOpenSettings();
    }

    protected static class Key {
        String label;
        String value;
        float weight;
        RectF rect = new RectF();
        int action;
        Key(String label, String value, float weight, int action) {
            this.label = label;
            this.value = value;
            this.weight = weight;
            this.action = action;
        }
    }

    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint altHint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupSelectedFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupText = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final List<List<Key>> rows = new ArrayList<>();
    protected Listener listener;
    protected float gap;
    protected float radius;
    private int forcedHeightPx = 0;

    private Key pressedKey;
    private Key downKey;
    private Key popupKey;
    private String[] popupOptions;
    private int popupSelected = 0;
    private boolean longPressActive = false;
    private boolean repeatingBackspace = false;
    private final RectF popupRect = new RectF();

    private static final long LONG_PRESS_MS = 360;
    private static final long BACKSPACE_REPEAT_START_MS = 430;
    private static final long BACKSPACE_REPEAT_MS = 68;

    public static final int ACT_TEXT=0, ACT_BACKSPACE=1, ACT_ENTER=2, ACT_SPACE=3, ACT_SHIFT=4, ACT_SYMBOLS=5, ACT_HIDE=6;

    public BaseKeyboardView(Context c, Listener l) {
        super(c);
        listener = l;
        gap = dp(6);
        radius = dp(10);
        fill.setColor(Color.rgb(16,16,16));
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        accent.setStyle(Paint.Style.STROKE);
        accent.setStrokeWidth(dp(2));
        accent.setColor(Color.WHITE);

        altHint.setColor(Color.rgb(150,150,150));
        altHint.setTextAlign(Paint.Align.RIGHT);
        altHint.setTextSize(dp(9));

        popupFill.setColor(Color.rgb(42,42,42));
        popupSelectedFill.setColor(Color.rgb(92,92,92));
        popupText.setColor(Color.WHITE);
        popupText.setTextAlign(Paint.Align.CENTER);
        popupText.setTextSize(dp(20));

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    protected float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    public void setFixedHeightDp(float heightDp) {
        forcedHeightPx = Math.round(dp(heightDp));
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = forcedHeightPx > 0 ? forcedHeightPx : getSuggestedMinimumHeight();
        if (height <= 0) height = View.MeasureSpec.getSize(heightMeasureSpec);
        int maxHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        int mode = View.MeasureSpec.getMode(heightMeasureSpec);
        if ((mode == View.MeasureSpec.AT_MOST || mode == View.MeasureSpec.EXACTLY) && maxHeight > 0) {
            height = Math.min(height, maxHeight);
        }
        setMeasuredDimension(width, height);
    }

    protected Key k(String label) { return new Key(label, label, 1f, ACT_TEXT); }
    protected Key k(String label, String value, float weight, int action) { return new Key(label, value, weight, action); }

    protected String[] getLongPressAlternatives(Key key) {
        if (key == null || key.action != ACT_TEXT || key.value == null) return null;
        String s = key.value.toLowerCase();
        switch (s) {
            case "a": return new String[]{"á","à","â","ã","ä"};
            case "e": return new String[]{"é","ê","è","ë"};
            case "i": return new String[]{"í","î","ì","ï"};
            case "o": return new String[]{"ó","ô","õ","ò","ö"};
            case "u": return new String[]{"ú","ü","û","ù"};
            case "c": return new String[]{"ç"};
            case "n": return new String[]{"ñ"};
            case "y": return new String[]{"ý","ÿ"};
            case "1": return new String[]{"!","¹"};
            case "2": return new String[]{"@","²"};
            case "3": return new String[]{"#","³"};
            case "4": return new String[]{"$","€","£"};
            case "5": return new String[]{"%"};
            case "6": return new String[]{"^"};
            case "7": return new String[]{"&"};
            case "8": return new String[]{"*"};
            case "9": return new String[]{"("};
            case "0": return new String[]{")"};
            case ".": return new String[]{"…","?","!",":",";"};
            case ",": return new String[]{";",":"};
            case "?": return new String[]{"¿"};
            case "!": return new String[]{"¡"};
            case "-": return new String[]{"—","–","_"};
            default: return null;
        }
    }

    protected String transformLongPressOutput(Key key, String option) {
        return option;
    }

    protected void onLongPressCommitted(Key key, String output) {
    }

    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (downKey == null || pressedKey != downKey) return;
            String[] options = getLongPressAlternatives(downKey);
            if (options == null || options.length == 0) return;
            popupKey = downKey;
            popupOptions = options;
            popupSelected = 0;
            longPressActive = true;
            if (listener != null) listener.onKeyFeedback();
            invalidate();
        }
    };

    private final Runnable repeatBackspaceRunnable = new Runnable() {
        @Override public void run() {
            if (downKey == null || downKey.action != ACT_BACKSPACE || listener == null) return;
            repeatingBackspace = true;
            listener.onKeyFeedback();
            listener.onBackspace();
            pressedKey = downKey;
            invalidate();
            postDelayed(this, BACKSPACE_REPEAT_MS);
        }
    };

    private void scheduleHoldAction(Key key) {
        removeCallbacks(longPressRunnable);
        removeCallbacks(repeatBackspaceRunnable);
        if (key == null) return;
        if (key.action == ACT_BACKSPACE) {
            postDelayed(repeatBackspaceRunnable, BACKSPACE_REPEAT_START_MS);
            return;
        }
        String[] alternatives = getLongPressAlternatives(key);
        if (alternatives != null && alternatives.length > 0) {
            postDelayed(longPressRunnable, LONG_PRESS_MS);
        }
    }

    private void stopHoldActions() {
        removeCallbacks(longPressRunnable);
        removeCallbacks(repeatBackspaceRunnable);
    }

    protected void flashKey(Key key) {
        if (key == null) return;
        pressedKey = key;
        invalidate();
        postDelayed(() -> {
            if (pressedKey == key && downKey == null) {
                pressedKey = null;
                invalidate();
            }
        }, 95);
    }

    protected void flashAction(int action) {
        for (List<Key> row : rows) {
            for (Key key : row) {
                if (key.action == action) {
                    flashKey(key);
                    return;
                }
            }
        }
    }

    protected void perform(Key key) {
        if (key == null || listener == null) return;
        listener.onKeyFeedback();
        flashKey(key);
        switch (key.action) {
            case ACT_BACKSPACE: listener.onBackspace(); break;
            case ACT_ENTER: listener.onEnter(); break;
            case ACT_SPACE: listener.onSpace(); break;
            case ACT_HIDE: listener.onHide(); break;
            default: if (key.value != null) listener.onText(key.value); break;
        }
    }

    protected void layoutRows(float top, float bottom) {
        if (rows.isEmpty()) return;
        float h = (bottom - top - gap * (rows.size() - 1)) / rows.size();
        for (int r=0; r<rows.size(); r++) {
            List<Key> row = rows.get(r);
            float total = 0;
            for (Key key: row) total += key.weight;
            float usable = getWidth() - getPaddingLeft() - getPaddingRight() - gap * (row.size() - 1);
            float x = getPaddingLeft();
            float y = top + r * (h + gap);
            for (Key key: row) {
                float w = usable * key.weight / total;
                key.rect.set(x, y, x+w, y+h);
                x += w + gap;
            }
        }
    }

    protected void drawKey(Canvas c, Key key, boolean selected) {
        boolean pressed = key == pressedKey;
        int bg = pressed ? Color.rgb(78,78,78) : selected ? Color.rgb(52,52,52) : Color.rgb(16,16,16);
        fill.setColor(bg);
        c.drawRoundRect(key.rect, radius, radius, fill);
        if (selected && !pressed) c.drawRoundRect(key.rect, radius, radius, accent);
        float size = Math.min(dp(28), key.rect.height() * 0.40f);
        text.setTextSize(size);
        Paint.FontMetrics fm = text.getFontMetrics();
        float cy = key.rect.centerY() - (fm.ascent + fm.descent) / 2f;
        c.drawText(key.label, key.rect.centerX(), cy, text);

        String[] alts = getLongPressAlternatives(key);
        if (alts != null && alts.length > 0 && key.rect.width() > dp(28) && key.rect.height() > dp(28)) {
            String hint = transformLongPressOutput(key, alts[0]);
            c.drawText(hint, key.rect.right - dp(5), key.rect.top + dp(11), altHint);
        }
    }

    protected Key hit(float x, float y) {
        for (List<Key> row: rows) for (Key key: row) if (key.rect.contains(x,y)) return key;
        return null;
    }

    private void updatePopupRect() {
        if (!longPressActive || popupKey == null || popupOptions == null || popupOptions.length == 0) return;
        float itemW = dp(42);
        float h = dp(48);
        float totalW = itemW * popupOptions.length;
        float left = popupKey.rect.centerX() - totalW / 2f;
        float minLeft = dp(4);
        float maxRight = getWidth() - dp(4);
        if (left < minLeft) left = minLeft;
        if (left + totalW > maxRight) left = maxRight - totalW;
        float top = popupKey.rect.top - h - dp(7);
        if (top < dp(3)) top = popupKey.rect.bottom + dp(6);
        popupRect.set(left, top, left + totalW, top + h);
    }

    private void updatePopupSelection(float x) {
        if (!longPressActive || popupOptions == null || popupOptions.length == 0) return;
        updatePopupRect();
        float itemW = popupRect.width() / popupOptions.length;
        int idx = (int)((x - popupRect.left) / itemW);
        if (idx < 0) idx = 0;
        if (idx >= popupOptions.length) idx = popupOptions.length - 1;
        if (popupSelected != idx) {
            popupSelected = idx;
            if (listener != null) listener.onKeyFeedback();
            invalidate();
        }
    }

    private void commitPopup() {
        if (!longPressActive || popupKey == null || popupOptions == null || popupOptions.length == 0 || listener == null) return;
        int idx = Math.max(0, Math.min(popupOptions.length - 1, popupSelected));
        String output = transformLongPressOutput(popupKey, popupOptions[idx]);
        listener.onKeyFeedback();
        listener.onText(output);
        Key committedKey = popupKey;
        clearTouchState();
        flashKey(committedKey);
        onLongPressCommitted(committedKey, output);
    }

    private void clearTouchState() {
        stopHoldActions();
        downKey = null;
        pressedKey = null;
        popupKey = null;
        popupOptions = null;
        popupSelected = 0;
        longPressActive = false;
        repeatingBackspace = false;
        invalidate();
    }

    @Override public void onDrawForeground(Canvas c) {
        super.onDrawForeground(c);
        if (!longPressActive || popupKey == null || popupOptions == null || popupOptions.length == 0) return;
        updatePopupRect();
        c.drawRoundRect(popupRect, dp(12), dp(12), popupFill);
        float itemW = popupRect.width() / popupOptions.length;
        for (int i=0; i<popupOptions.length; i++) {
            float l = popupRect.left + i * itemW;
            RectF item = new RectF(l, popupRect.top, l + itemW, popupRect.bottom);
            if (i == popupSelected) c.drawRoundRect(item, dp(10), dp(10), popupSelectedFill);
            String shown = transformLongPressOutput(popupKey, popupOptions[i]);
            Paint.FontMetrics fm = popupText.getFontMetrics();
            float cy = item.centerY() - (fm.ascent + fm.descent) / 2f;
            c.drawText(shown, item.centerX(), cy, popupText);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopHoldActions();
                longPressActive = false;
                popupKey = null;
                popupOptions = null;
                repeatingBackspace = false;
                downKey = hit(e.getX(), e.getY());
                pressedKey = downKey;
                scheduleHoldAction(downKey);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (longPressActive) {
                    updatePopupSelection(e.getX());
                    return true;
                }
                if (repeatingBackspace) return true;
                Key moved = hit(e.getX(), e.getY());
                if (moved != downKey) {
                    stopHoldActions();
                    downKey = moved;
                    pressedKey = moved;
                    scheduleHoldAction(moved);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                stopHoldActions();
                if (longPressActive) {
                    updatePopupSelection(e.getX());
                    commitPopup();
                    return true;
                }
                if (repeatingBackspace) {
                    clearTouchState();
                    return true;
                }
                Key key = hit(e.getX(), e.getY());
                Key expected = downKey;
                downKey = null;
                pressedKey = null;
                if (key != null && key == expected) perform(key);
                else invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                clearTouchState();
                return true;
        }
        return true;
    }
}
