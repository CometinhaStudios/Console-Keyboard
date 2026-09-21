package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
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
        void onReplaceLast(String oldText, String newText);
        void onSuggestionSelected(String suggestion);
    }

    protected static class Key {
        String label;
        String value;
        float weight;
        RectF rect = new RectF();
        int action;
        String[] longPress;
        boolean repeatable;

        Key(String label, String value, float weight, int action, String[] longPress, boolean repeatable) {
            this.label = label;
            this.value = value;
            this.weight = weight;
            this.action = action;
            this.longPress = longPress == null ? new String[0] : longPress;
            this.repeatable = repeatable;
        }
    }

    private static class FastTouchState {
        final int pointerId;
        final Key key;
        final float downX;
        final float downY;
        Runnable holdRunnable;
        Runnable repeatRunnable;
        boolean longPressOpened = false;

        FastTouchState(int pointerId, Key key, float downX, float downY) {
            this.pointerId = pointerId;
            this.key = key;
            this.downX = downX;
            this.downY = downY;
        }
    }


    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint secondary = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint popupPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint popupSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected final List<List<Key>> rows = new ArrayList<>();
    protected Listener listener;
    protected float gap;
    protected float radius;

    private int forcedHeightPx = 0;
    private Key pressedKey;
    private Key downKey;
    private boolean longPressTriggered = false;
    private boolean repeating = false;

    private boolean popupVisible = false;
    private Key popupKey;
    private String[] popupOptions = new String[0];
    private int popupIndex = 0;
    private final RectF popupRect = new RectF();
    private float popupCellWidth = 0f;

    private Runnable longPressRunnable;
    private Runnable repeatRunnable;

    private final SparseArray<FastTouchState> fastTouches = new SparseArray<>();
    private int fastPopupPointerId = -1;

    public static final int ACT_TEXT=0, ACT_BACKSPACE=1, ACT_ENTER=2, ACT_SPACE=3, ACT_SHIFT=4, ACT_SYMBOLS=5, ACT_HIDE=6, ACT_EMOJI=7, ACT_SUGGESTION=8;

    public BaseKeyboardView(Context c, Listener l) {
        super(c);
        listener = l;
        gap = dp(6);
        radius = dp(10);

        fill.setColor(Color.rgb(16,16,16));

        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);

        secondary.setColor(Color.rgb(175,175,175));
        secondary.setTextAlign(Paint.Align.RIGHT);
        secondary.setTextSize(dp(9));

        accent.setStyle(Paint.Style.STROKE);
        accent.setStrokeWidth(dp(2));
        accent.setColor(Color.WHITE);

        popupPaint.setColor(Color.rgb(42,42,42));
        popupSelectedPaint.setColor(Color.rgb(90,90,90));
        popupTextPaint.setColor(Color.WHITE);
        popupTextPaint.setTextAlign(Paint.Align.CENTER);
        popupTextPaint.setTextSize(dp(21));

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    protected float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

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

    protected Key k(String label) {
        return new Key(label, label, 1f, ACT_TEXT, new String[0], false);
    }

    protected Key k(String label, String value, float weight, int action) {
        return new Key(label, value, weight, action, new String[0], false);
    }

    protected Key kAlt(String label, String value, float weight, int action, String[] longPress) {
        return new Key(label, value, weight, action, longPress, false);
    }

    protected Key kRepeat(String label, String value, float weight, int action) {
        return new Key(label, value, weight, action, new String[0], true);
    }

    protected boolean fastTouchMode() {
        return true;
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
        }, 52);
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

    protected void feedbackAsync() {
        if (listener == null) return;

        // Não segura o commitText esperando vibração.
        // O haptic roda no próximo ciclo da UI.
        post(() -> {
            if (listener != null) listener.onKeyFeedback();
        });
    }

    protected void perform(Key key) {
        if (key == null || listener == null) return;

        // Primeiro envia a tecla para o app. Visual/haptic vêm depois.
        switch (key.action) {
            case ACT_BACKSPACE: listener.onBackspace(); break;
            case ACT_ENTER: listener.onEnter(); break;
            case ACT_SPACE: listener.onSpace(); break;
            case ACT_HIDE: listener.onHide(); break;
            case ACT_SUGGESTION:
                if (key.value != null) listener.onSuggestionSelected(key.value);
                break;
            default:
                if (key.value != null) listener.onText(key.value);
                break;
        }

        flashKey(key);
        feedbackAsync();
    }

    protected void commitAlternate(Key key, String value) {
        if (listener == null || value == null) return;
        listener.onText(value);
        feedbackAsync();
    }

    protected void layoutRows(float top, float bottom) {
        if (rows.isEmpty()) return;

        float h = (bottom - top - gap * (rows.size() - 1)) / rows.size();

        for (int r=0; r<rows.size(); r++) {
            List<Key> row = rows.get(r);
            float total = 0f;
            for (Key key : row) total += key.weight;

            float usable = getWidth() - getPaddingLeft() - getPaddingRight() - gap * (row.size() - 1);
            float x = getPaddingLeft();
            float y = top + r * (h + gap);

            for (Key key : row) {
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

        if (selected && !pressed) {
            c.drawRoundRect(key.rect, radius, radius, accent);
        }

        float size = Math.min(dp(28), key.rect.height() * 0.40f);
        text.setTextSize(size);

        Paint.FontMetrics fm = text.getFontMetrics();
        float cy = key.rect.centerY() - (fm.ascent + fm.descent) / 2f;
        c.drawText(key.label, key.rect.centerX(), cy, text);

        if (key.longPress.length > 0 && key.rect.width() > dp(28)) {
            String hint = key.longPress[0];
            c.drawText(hint, key.rect.right - dp(5), key.rect.top + dp(11), secondary);
        }
    }

    protected void drawLongPressPopup(Canvas c) {
        if (!popupVisible || popupKey == null || popupOptions.length == 0) return;

        float cell = dp(42);
        float total = cell * popupOptions.length;
        float left = popupKey.rect.centerX() - total / 2f;

        left = Math.max(dp(4), Math.min(left, getWidth() - total - dp(4)));

        float bottom = popupKey.rect.top - dp(5);
        float top = bottom - dp(46);

        if (top < dp(2)) {
            top = popupKey.rect.bottom + dp(5);
            bottom = top + dp(46);
        }

        popupRect.set(left, top, left + total, bottom);
        popupCellWidth = cell;

        c.drawRoundRect(popupRect, dp(12), dp(12), popupPaint);

        for (int i=0; i<popupOptions.length; i++) {
            float l = left + i * cell;
            RectF cellRect = new RectF(l, top, l + cell, bottom);

            if (i == popupIndex) {
                c.drawRoundRect(cellRect, dp(10), dp(10), popupSelectedPaint);
            }

            Paint.FontMetrics fm = popupTextPaint.getFontMetrics();
            float cy = cellRect.centerY() - (fm.ascent + fm.descent) / 2f;
            c.drawText(popupOptions[i], cellRect.centerX(), cy, popupTextPaint);
        }
    }

    protected Key hit(float x, float y) {
        for (List<Key> row : rows) {
            for (Key key : row) {
                if (key.rect.contains(x, y)) return key;
            }
        }
        return null;
    }


    private Key hitForgiving(float x, float y) {
        Key exact = hit(x, y);
        if (exact != null) return exact;
        if (rows.isEmpty()) return null;

        float minTop = Float.MAX_VALUE;
        float maxBottom = -Float.MAX_VALUE;

        for (List<Key> row : rows) {
            for (Key key : row) {
                minTop = Math.min(minTop, key.rect.top);
                maxBottom = Math.max(maxBottom, key.rect.bottom);
            }
        }

        if (y < minTop - dp(3) || y > maxBottom + dp(3)) return null;

        Key best = null;
        float bestDistance = Float.MAX_VALUE;
        float maxDistance = dp(16);
        float maxDistanceSq = maxDistance * maxDistance;

        for (List<Key> row : rows) {
            for (Key key : row) {
                float dx =
                        x < key.rect.left ? key.rect.left - x :
                        x > key.rect.right ? x - key.rect.right :
                        0f;

                float dy =
                        y < key.rect.top ? key.rect.top - y :
                        y > key.rect.bottom ? y - key.rect.bottom :
                        0f;

                float d = dx * dx + dy * dy;

                if (d < bestDistance && d <= maxDistanceSq) {
                    bestDistance = d;
                    best = key;
                }
            }
        }

        return best;
    }

    private int longPressDelayMs() {
        return getContext()
                .getSharedPreferences(ConsoleImeService.PREFS, Context.MODE_PRIVATE)
                .getInt(ConsoleImeService.PREF_LONG_PRESS_DELAY, 420);
    }

    private void cancelTimers() {
        if (longPressRunnable != null) removeCallbacks(longPressRunnable);
        if (repeatRunnable != null) removeCallbacks(repeatRunnable);
        longPressRunnable = null;
        repeatRunnable = null;
    }

    private void clearPressState() {
        cancelTimers();
        pressedKey = null;
        downKey = null;
        longPressTriggered = false;
        repeating = false;
        popupVisible = false;
        popupKey = null;
        popupOptions = new String[0];
        popupIndex = 0;
        invalidate();
    }

    private void startDeleteRepeat(Key key) {
        if (key == null || key.action != ACT_BACKSPACE || listener == null) return;

        longPressTriggered = true;
        repeating = true;
        listener.onBackspace();
        feedbackAsync();

        repeatRunnable = new Runnable() {
            @Override public void run() {
                if (!repeating || downKey != key) return;
                listener.onBackspace();
                postDelayed(this, 55);
            }
        };

        postDelayed(repeatRunnable, 90);
    }

    private void openPopup(Key key) {
        if (key == null || key.longPress.length == 0) return;

        longPressTriggered = true;
        popupVisible = true;
        popupKey = key;
        popupOptions = key.longPress;
        popupIndex = 0;
        feedbackAsync();
        invalidate();
    }

    private void scheduleLongPress(Key key) {
        cancelTimers();
        longPressTriggered = false;
        repeating = false;
        popupVisible = false;
        popupKey = null;

        if (key == null) return;
        if (!key.repeatable && key.longPress.length == 0) return;

        longPressRunnable = () -> {
            if (downKey != key) return;

            if (key.repeatable && key.action == ACT_BACKSPACE) {
                startDeleteRepeat(key);
            } else if (key.longPress.length > 0) {
                openPopup(key);
            }
        };

        postDelayed(longPressRunnable, longPressDelayMs());
    }

    private void updatePopupSelection(float x) {
        if (!popupVisible || popupOptions.length == 0) return;

        float cell = popupCellWidth > 0 ? popupCellWidth : dp(42);
        float total = cell * popupOptions.length;
        float left = popupKey.rect.centerX() - total / 2f;
        left = Math.max(dp(4), Math.min(left, getWidth() - total - dp(4)));

        int index = (int)((x - left) / cell);
        index = Math.max(0, Math.min(popupOptions.length - 1, index));

        if (index != popupIndex) {
            popupIndex = index;
            feedbackAsync();
            invalidate();
        }
    }

    protected boolean isLongPressPopupOpen() {
        return popupVisible;
    }

    protected boolean movePopupSelection(int delta) {
        if (!popupVisible || popupOptions.length == 0) return false;

        int old = popupIndex;
        popupIndex = Math.max(0, Math.min(popupOptions.length - 1, popupIndex + delta));

        if (popupIndex != old) {
            feedbackAsync();
            invalidate();
        }

        return true;
    }

    protected void beginGamepadPress(Key key) {
        if (key == null) return;
        downKey = key;
        pressedKey = key;
        scheduleLongPress(key);
        invalidate();
    }

    protected void endGamepadPress(Key key) {
        if (downKey == null) return;

        cancelTimers();

        if (popupVisible && popupOptions.length > 0) {
            String value = popupOptions[popupIndex];
            Key origin = popupKey;
            clearPressState();
            commitAlternate(origin, value);
            return;
        }

        Key original = downKey;
        boolean wasLong = longPressTriggered;
        clearPressState();

        if (!wasLong && original == key) {
            perform(original);
        }
    }


    private void cancelFastTimers(FastTouchState state) {
        if (state == null) return;

        if (state.holdRunnable != null) {
            removeCallbacks(state.holdRunnable);
            state.holdRunnable = null;
        }

        if (state.repeatRunnable != null) {
            removeCallbacks(state.repeatRunnable);
            state.repeatRunnable = null;
        }
    }

    private void cancelFastLongPressCandidates() {
        for (int i = 0; i < fastTouches.size(); i++) {
            FastTouchState state = fastTouches.valueAt(i);

            if (state.holdRunnable != null && !state.longPressOpened) {
                removeCallbacks(state.holdRunnable);
                state.holdRunnable = null;
            }
        }
    }

    private void closeFastPopup() {
        popupVisible = false;
        popupKey = null;
        popupOptions = new String[0];
        popupIndex = 0;
        popupCellWidth = 0f;
        fastPopupPointerId = -1;
    }

    private void scheduleFastHold(FastTouchState state) {
        if (state == null || state.key == null) return;

        Key key = state.key;

        if (key.repeatable && key.action == ACT_BACKSPACE) {
            state.holdRunnable = () -> {
                if (fastTouches.get(state.pointerId) != state) return;

                state.repeatRunnable = new Runnable() {
                    @Override public void run() {
                        if (fastTouches.get(state.pointerId) != state) return;
                        if (listener != null) listener.onBackspace();
                        postDelayed(this, 48);
                    }
                };

                post(state.repeatRunnable);
            };

            postDelayed(state.holdRunnable, Math.max(250, longPressDelayMs()));
            return;
        }

        if (key.action == ACT_TEXT && key.longPress.length > 0) {
            state.holdRunnable = () -> {
                if (fastTouches.get(state.pointerId) != state) return;
                if (fastTouches.size() != 1) return;
                if (popupVisible) return;

                state.longPressOpened = true;
                fastPopupPointerId = state.pointerId;

                popupVisible = true;
                popupKey = key;
                popupOptions = key.longPress;
                popupIndex = 0;

                feedbackAsync();
                invalidate();
            };

            postDelayed(state.holdRunnable, longPressDelayMs());
        }
    }

    private void fastPointerDown(MotionEvent e, int index) {
        int pointerId = e.getPointerId(index);

        if (popupVisible && fastPopupPointerId >= 0) return;

        float x = e.getX(index);
        float y = e.getY(index);

        Key key = hitForgiving(x, y);
        if (key == null) return;

        if (fastTouches.size() > 0) {
            cancelFastLongPressCandidates();
        }

        FastTouchState state = new FastTouchState(pointerId, key, x, y);
        fastTouches.put(pointerId, state);

        // A tecla entra imediatamente no DOWN / POINTER_DOWN.
        perform(key);

        scheduleFastHold(state);
    }

    private void fastPointerMove(MotionEvent e) {
        if (popupVisible && fastPopupPointerId >= 0) {
            int index = e.findPointerIndex(fastPopupPointerId);
            if (index >= 0) updatePopupSelection(e.getX(index));
            return;
        }

        float cancelDistance = dp(22);
        float cancelDistanceSq = cancelDistance * cancelDistance;

        for (int i = 0; i < fastTouches.size(); i++) {
            FastTouchState state = fastTouches.valueAt(i);
            int index = e.findPointerIndex(state.pointerId);
            if (index < 0) continue;

            float dx = e.getX(index) - state.downX;
            float dy = e.getY(index) - state.downY;

            if (dx * dx + dy * dy > cancelDistanceSq &&
                    state.holdRunnable != null &&
                    !state.longPressOpened) {

                removeCallbacks(state.holdRunnable);
                state.holdRunnable = null;
            }
        }
    }

    private void fastPointerUp(MotionEvent e, int index) {
        int pointerId = e.getPointerId(index);
        FastTouchState state = fastTouches.get(pointerId);

        if (state == null) return;

        cancelFastTimers(state);

        if (state.longPressOpened &&
                fastPopupPointerId == pointerId &&
                popupVisible &&
                popupOptions.length > 0) {

            String selected = popupOptions[popupIndex];
            String original = state.key.value;

            closeFastPopup();

            if (listener != null) {
                listener.onReplaceLast(original, selected);
            }

            feedbackAsync();
        }

        fastTouches.remove(pointerId);
        invalidate();
    }

    private void clearFastTouches() {
        for (int i = 0; i < fastTouches.size(); i++) {
            cancelFastTimers(fastTouches.valueAt(i));
        }

        fastTouches.clear();
        closeFastPopup();
        pressedKey = null;
        invalidate();
    }

    private boolean fastTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                fastPointerDown(e, e.getActionIndex());
                return true;

            case MotionEvent.ACTION_MOVE:
                fastPointerMove(e);
                return true;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                fastPointerUp(e, e.getActionIndex());
                return true;

            case MotionEvent.ACTION_CANCEL:
                clearFastTouches();
                return true;
        }

        return true;
    }

    private boolean legacyTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downKey = hit(e.getX(), e.getY());
                pressedKey = downKey;
                scheduleLongPress(downKey);
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (popupVisible) {
                    updatePopupSelection(e.getX());
                    return true;
                }

                if (downKey != null) {
                    RectF expanded = new RectF(
                            downKey.rect.left - dp(10),
                            downKey.rect.top - dp(10),
                            downKey.rect.right + dp(10),
                            downKey.rect.bottom + dp(10)
                    );

                    if (!expanded.contains(e.getX(), e.getY())) {
                        cancelTimers();
                        pressedKey = null;
                        invalidate();
                    } else {
                        pressedKey = downKey;
                    }
                }

                return true;
            }

            case MotionEvent.ACTION_UP: {
                Key released = hit(e.getX(), e.getY());
                cancelTimers();

                if (popupVisible && popupOptions.length > 0) {
                    String value = popupOptions[popupIndex];
                    Key origin = popupKey;
                    clearPressState();
                    commitAlternate(origin, value);
                    return true;
                }

                Key original = downKey;
                boolean wasLong = longPressTriggered;
                clearPressState();

                if (!wasLong && original != null && released == original) {
                    perform(original);
                }

                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                clearPressState();
                return true;
        }

        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (fastTouchMode()) {
            return fastTouchEvent(e);
        }

        return legacyTouchEvent(e);
    }

}
