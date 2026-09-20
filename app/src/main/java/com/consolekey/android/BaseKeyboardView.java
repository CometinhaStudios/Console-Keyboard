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
    protected final List<List<Key>> rows = new ArrayList<>();
    protected Listener listener;
    protected float gap;
    protected float radius;
    private int forcedHeightPx = 0;
    private Key pressedKey;

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

    protected void flashKey(Key key) {
        if (key == null) return;
        pressedKey = key;
        invalidate();
        postDelayed(() -> {
            if (pressedKey == key) {
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
    }

    protected Key hit(float x, float y) {
        for (List<Key> row: rows) for (Key key: row) if (key.rect.contains(x,y)) return key;
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedKey = hit(e.getX(), e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                Key moved = hit(e.getX(), e.getY());
                if (moved != pressedKey) {
                    pressedKey = moved;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                Key key = hit(e.getX(), e.getY());
                Key wasPressed = pressedKey;
                if (key != null && key == wasPressed) {
                    perform(key);
                } else {
                    pressedKey = null;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedKey = null;
                invalidate();
                return true;
        }
        return true;
    }
}
