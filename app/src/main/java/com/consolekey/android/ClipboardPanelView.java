package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

public class ClipboardPanelView extends View {
    public interface Listener {
        void onPaste(String text);
        void onClose();
        void onClear();
    }

    private final Listener listener;

    private final Paint headerPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint titlePaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint actionPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint cardPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint recentCardPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint selectedPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint textPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint metaPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF closeRect =
            new RectF();

    private final RectF clearRect =
            new RectF();

    private List<String> items =
            new ArrayList<>();

    private float scrollY = 0f;
    private float maxScroll = 0f;
    private float downX = 0f;
    private float downY = 0f;
    private float lastY = 0f;
    private boolean dragging = false;

    private final int touchSlop;
    private int selectedIndex = 0;
    private int forcedHeightPx = 0;

    public ClipboardPanelView(
            Context context,
            Listener listener
    ) {
        super(context);

        this.listener = listener;

        touchSlop =
                ViewConfiguration
                        .get(context)
                        .getScaledTouchSlop();

        headerPaint.setColor(
                Color.rgb(10,10,10)
        );

        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextAlign(
                Paint.Align.CENTER
        );
        titlePaint.setTextSize(dp(15));

        actionPaint.setColor(
                Color.rgb(215,215,215)
        );
        actionPaint.setTextSize(dp(13));

        cardPaint.setColor(
                Color.rgb(34,34,34)
        );

        recentCardPaint.setColor(
                Color.rgb(20,20,20)
        );

        selectedPaint.setColor(
                Color.WHITE
        );
        selectedPaint.setStyle(
                Paint.Style.STROKE
        );
        selectedPaint.setStrokeWidth(
                dp(2)
        );

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(
                Paint.Align.CENTER
        );
        textPaint.setTextSize(dp(14));

        metaPaint.setColor(
                Color.rgb(160,160,160)
        );
        metaPaint.setTextAlign(
                Paint.Align.CENTER
        );
        metaPaint.setTextSize(dp(10));

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private float dp(float value) {
        return value *
                getResources()
                        .getDisplayMetrics()
                        .density;
    }

    public void setFixedHeightDp(
            float heightDp
    ) {
        forcedHeightPx =
                Math.round(
                        dp(heightDp)
                );

        requestLayout();
    }

    @Override protected void onMeasure(
            int widthMeasureSpec,
            int heightMeasureSpec
    ) {
        int width =
                MeasureSpec.getSize(
                        widthMeasureSpec
                );

        int height =
                forcedHeightPx > 0
                        ? forcedHeightPx
                        : MeasureSpec.getSize(
                                heightMeasureSpec
                        );

        int maxHeight =
                MeasureSpec.getSize(
                        heightMeasureSpec
                );

        int mode =
                MeasureSpec.getMode(
                        heightMeasureSpec
                );

        if ((mode == MeasureSpec.EXACTLY ||
                mode == MeasureSpec.AT_MOST) &&
                maxHeight > 0) {

            height =
                    Math.min(
                            height,
                            maxHeight
                    );
        }

        setMeasuredDimension(
                width,
                height
        );
    }

    public void setItems(
            List<String> values
    ) {
        items =
                values == null
                        ? new ArrayList<>()
                        : new ArrayList<>(values);

        selectedIndex =
                Math.max(
                        0,
                        Math.min(
                                selectedIndex,
                                Math.max(
                                        0,
                                        items.size() - 1
                                )
                        )
                );

        scrollY = 0f;
        invalidate();
    }

    private RectF cardRect(
            int index
    ) {
        float headerH = dp(38);
        float gap = dp(8);
        float left = dp(10);
        float right =
                getWidth() - dp(10);

        if (index == 0) {
            float top =
                    headerH +
                    dp(10) -
                    scrollY;

            return new RectF(
                    left,
                    top,
                    right,
                    top + dp(76)
            );
        }

        int local =
                index - 1;

        int row =
                local / 2;

        int col =
                local % 2;

        float gridTop =
                headerH +
                dp(96) -
                scrollY;

        float width =
                (right - left - gap) /
                2f;

        float x =
                left +
                col *
                (width + gap);

        float y =
                gridTop +
                row *
                (dp(62) + gap);

        return new RectF(
                x,
                y,
                x + width,
                y + dp(62)
        );
    }

    private void updateMaxScroll() {
        if (items.isEmpty()) {
            maxScroll = 0f;
            scrollY = 0f;
            return;
        }

        int older =
                Math.max(
                        0,
                        items.size() - 1
                );

        int rows =
                (older + 1) / 2;

        float contentBottom =
                dp(38) +
                dp(96) +
                rows *
                (dp(62) + dp(8));

        maxScroll =
                Math.max(
                        0f,
                        contentBottom -
                        getHeight() +
                        dp(8)
                );

        scrollY =
                Math.max(
                        0f,
                        Math.min(
                                scrollY,
                                maxScroll
                        )
                );
    }

    private void drawCenteredText(
            Canvas canvas,
            String value,
            RectF rect,
            int maxLines
    ) {
        if (value == null) {
            return;
        }

        String cleaned =
                value.replace(
                        '\n',
                        ' '
                );

        float maxWidth =
                rect.width() - dp(20);

        List<String> lines =
                new ArrayList<>();

        int start = 0;

        while (start < cleaned.length() &&
                lines.size() < maxLines) {

            int count =
                    textPaint.breakText(
                            cleaned,
                            start,
                            cleaned.length(),
                            true,
                            maxWidth,
                            null
                    );

            if (count <= 0) {
                break;
            }

            int end =
                    Math.min(
                            cleaned.length(),
                            start + count
                    );

            if (end < cleaned.length()) {
                int space =
                        cleaned.lastIndexOf(
                                ' ',
                                end
                        );

                if (space > start) {
                    end = space;
                }
            }

            String line =
                    cleaned.substring(
                            start,
                            end
                    ).trim();

            lines.add(line);

            start = end;

            while (start < cleaned.length() &&
                    cleaned.charAt(start) == ' ') {
                start++;
            }
        }

        if (start < cleaned.length() &&
                !lines.isEmpty()) {

            int last =
                    lines.size() - 1;

            String valueLast =
                    lines.get(last);

            while (valueLast.length() > 1 &&
                    textPaint.measureText(
                            valueLast + "…"
                    ) > maxWidth) {

                valueLast =
                        valueLast.substring(
                                0,
                                valueLast.length() - 1
                        );
            }

            lines.set(
                    last,
                    valueLast + "…"
            );
        }

        Paint.FontMetrics fm =
                textPaint.getFontMetrics();

        float lineH =
                (fm.descent - fm.ascent) *
                1.1f;

        float total =
                lines.size() * lineH;

        float y =
                rect.centerY() -
                total / 2f -
                fm.ascent;

        for (String line : lines) {
            canvas.drawText(
                    line,
                    rect.centerX(),
                    y,
                    textPaint
            );

            y += lineH;
        }
    }

    @Override protected void onDraw(
            Canvas canvas
    ) {
        super.onDraw(canvas);

        canvas.drawColor(
                Color.rgb(8,8,8)
        );

        float headerH = dp(38);

        canvas.drawRect(
                0,
                0,
                getWidth(),
                headerH,
                headerPaint
        );

        closeRect.set(
                0,
                0,
                dp(54),
                headerH
        );

        clearRect.set(
                getWidth() - dp(78),
                0,
                getWidth(),
                headerH
        );

        actionPaint.setTextAlign(
                Paint.Align.CENTER
        );

        canvas.drawText(
                "⌨",
                closeRect.centerX(),
                dp(25),
                actionPaint
        );

        canvas.drawText(
                "Área de transferência",
                getWidth() / 2f,
                dp(25),
                titlePaint
        );

        canvas.drawText(
                "Limpar",
                clearRect.centerX(),
                dp(25),
                actionPaint
        );

        if (items.isEmpty()) {
            metaPaint.setTextSize(dp(13));

            Paint.FontMetrics fm =
                    metaPaint.getFontMetrics();

            float y =
                    headerH +
                    (getHeight() - headerH) / 2f -
                    (fm.ascent + fm.descent) / 2f;

            canvas.drawText(
                    "Copie um texto para ele aparecer aqui",
                    getWidth() / 2f,
                    y,
                    metaPaint
            );

            return;
        }

        updateMaxScroll();

        for (int i=0; i<items.size(); i++) {
            RectF rect =
                    cardRect(i);

            if (rect.bottom < headerH ||
                    rect.top > getHeight()) {
                continue;
            }

            canvas.drawRoundRect(
                    rect,
                    dp(12),
                    dp(12),
                    i == 0
                            ? cardPaint
                            : recentCardPaint
            );

            if (i == selectedIndex) {
                canvas.drawRoundRect(
                        rect,
                        dp(12),
                        dp(12),
                        selectedPaint
                );
            }

            if (i == 0) {
                metaPaint.setTextSize(dp(9));

                canvas.drawText(
                        "Mais recente",
                        rect.centerX(),
                        rect.top + dp(13),
                        metaPaint
                );

                RectF textRect =
                        new RectF(
                                rect.left,
                                rect.top + dp(10),
                                rect.right,
                                rect.bottom
                        );

                textPaint.setTextSize(dp(15));

                drawCenteredText(
                        canvas,
                        items.get(i),
                        textRect,
                        3
                );
            } else {
                textPaint.setTextSize(dp(12.5f));

                drawCenteredText(
                        canvas,
                        items.get(i),
                        rect,
                        3
                );
            }
        }
    }

    private int hitItem(
            float x,
            float y
    ) {
        for (int i=0; i<items.size(); i++) {
            if (cardRect(i).contains(x, y)) {
                return i;
            }
        }

        return -1;
    }

    @Override public boolean onTouchEvent(
            MotionEvent event
    ) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastY = y;
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (Math.abs(y - downY) >
                        touchSlop) {
                    dragging = true;
                }

                if (dragging) {
                    float dy =
                            lastY - y;

                    scrollY =
                            Math.max(
                                    0f,
                                    Math.min(
                                            maxScroll,
                                            scrollY + dy
                                    )
                            );

                    invalidate();
                }

                lastY = y;
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    if (closeRect.contains(x, y)) {
                        listener.onClose();
                        return true;
                    }

                    if (clearRect.contains(x, y)) {
                        listener.onClear();
                        return true;
                    }

                    int index =
                            hitItem(
                                    x,
                                    y
                            );

                    if (index >= 0 &&
                            index < items.size()) {

                        selectedIndex = index;

                        listener.onPaste(
                                items.get(index)
                        );

                        invalidate();
                    }
                }

                return true;

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }

        return true;
    }

    public boolean handleGamepadKey(
            int code,
            KeyEvent event
    ) {
        if (event == null) {
            return false;
        }

        if (event.getAction() ==
                KeyEvent.ACTION_UP) {

            switch (code) {
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_BUTTON_X:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
            }

            return false;
        }

        if (event.getAction() !=
                KeyEvent.ACTION_DOWN ||
                event.getRepeatCount() > 0) {
            return false;
        }

        if (items.isEmpty()) {
            if (code ==
                    KeyEvent.KEYCODE_BUTTON_B) {
                listener.onClose();
                return true;
            }

            return false;
        }

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                selectedIndex--;
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                selectedIndex++;
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                selectedIndex -=
                        selectedIndex <= 0
                                ? 0
                                : 2;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                selectedIndex += 2;
                break;

            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                listener.onPaste(
                        items.get(
                                Math.max(
                                        0,
                                        Math.min(
                                                selectedIndex,
                                                items.size() - 1
                                        )
                                )
                        )
                );
                return true;

            case KeyEvent.KEYCODE_BUTTON_B:
                listener.onClose();
                return true;

            case KeyEvent.KEYCODE_BUTTON_X:
                listener.onClear();
                return true;

            default:
                return false;
        }

        selectedIndex =
                Math.max(
                        0,
                        Math.min(
                                selectedIndex,
                                items.size() - 1
                        )
                );

        RectF selected =
                cardRect(
                        selectedIndex
                );

        if (selected.top < dp(38)) {
            scrollY =
                    Math.max(
                            0f,
                            scrollY -
                            (dp(38) - selected.top)
                    );
        } else if (selected.bottom >
                getHeight()) {

            scrollY =
                    Math.min(
                            maxScroll,
                            scrollY +
                            (selected.bottom -
                            getHeight())
                    );
        }

        invalidate();
        return true;
    }
}
