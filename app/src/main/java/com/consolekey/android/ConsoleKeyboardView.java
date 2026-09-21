package com.consolekey.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConsoleKeyboardView extends BaseKeyboardView {
    private ControllerDetector.Family family = ControllerDetector.Family.GENERIC;
    private int selRow = 1;
    private int selCol = 0;
    private boolean upper = false;
    private boolean numericMode = false;
    private int page = 0;

    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controllerBadgeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controllerBadgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controllerBadgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path controllerBadgePath = new Path();
    private float headerH;
    private float suggestionH;

    private String[] wordSuggestions = new String[0];
    private final Paint suggestionBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suggestionText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suggestionDivider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean suggestionTouch = false;
    private int motionDirX = 0;
    private int motionDirY = 0;
    private long lastMotionMoveMs = 0L;

    public ConsoleKeyboardView(Context c, Listener l) {
        this(c, l, false);
    }

    public ConsoleKeyboardView(Context c, Listener l, boolean numericMode) {
        super(c, l);
        this.numericMode = numericMode;

        setPadding((int)dp(8),(int)dp(4),(int)dp(8),(int)dp(4));
        gap = dp(3.5f);
        radius = dp(5f);
        headerH = dp(22);
        suggestionH = 0f;

        suggestionBg.setColor(Color.rgb(10,10,10));
        suggestionText.setColor(Color.WHITE);
        suggestionText.setTextAlign(Paint.Align.CENTER);
        suggestionText.setTextSize(dp(11));
        suggestionDivider.setColor(Color.rgb(42,42,42));
        suggestionDivider.setStrokeWidth(dp(1));

        hint.setColor(Color.LTGRAY);
        hint.setTextSize(dp(9.5f));
        hint.setTextAlign(Paint.Align.LEFT);

        controllerBadgeFill.setStyle(Paint.Style.FILL);

        controllerBadgeStroke.setStyle(Paint.Style.STROKE);
        controllerBadgeStroke.setStrokeWidth(dp(1.35f));
        controllerBadgeStroke.setStrokeCap(Paint.Cap.ROUND);
        controllerBadgeStroke.setStrokeJoin(Paint.Join.ROUND);

        controllerBadgeText.setTypeface(Typeface.DEFAULT_BOLD);
        controllerBadgeText.setTextAlign(Paint.Align.CENTER);
        controllerBadgeText.setTextSize(dp(7.4f));

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

        if (!numericMode && page == 0 && wordSuggestions.length > 0) {
            int count = Math.min(3, wordSuggestions.length);

            for (int i=0; i<count; i++) {
                String value = wordSuggestions[i];

                if (value != null && !value.isEmpty()) {
                    out.add(k(value, value, 1f, ACT_SUGGESTION));
                }
            }

            if (!out.isEmpty()) return out;
        }

        for (String value : new String[]{"1","2","3","4","5","6","7","8","9","0"}) {
            out.add(normalKey(value));
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

        if (numericMode) {
            rows.add(letterRow("1","2","3"));
            rows.add(letterRow("4","5","6"));
            rows.add(letterRow("7","8","9"));

            List<Key> last = new ArrayList<>();
            last.add(k("", null, 1f, ACT_TEXT));
            last.add(normalKey("0"));
            last.add(kRepeat("⌫", null, 1f, ACT_BACKSPACE));
            rows.add(last);

            List<Key> done = new ArrayList<>();
            done.add(k("DONE", null, 3f, ACT_ENTER));
            rows.add(done);

            clamp();
            return;
        }

        if (page == 0) {
            rows.add(numberRow());
            rows.add(letterRow("q","w","e","r","t","y","u","i","o","p"));
            rows.add(letterRow("a","s","d","f","g","h","j","k","l"));
            rows.add(letterRow("z","x","c","v","b","n","m",",",".","?"));

            List<Key> bottom = new ArrayList<>();
            bottom.add(k("⇧", null, 1.0f, ACT_SHIFT));
            bottom.add(k("@#:", null, 1.0f, ACT_SYMBOLS));
            bottom.add(k("CLIP", null, 0.8f, ACT_CLIPBOARD));
            bottom.add(k("SPACE", null, 4.45f, ACT_SPACE));
            bottom.add(kRepeat("⌫", null, 1.2f, ACT_BACKSPACE));
            bottom.add(k("DONE", null, 1.35f, ACT_ENTER));
            rows.add(bottom);
        } else if (page == 1) {
            rows.add(numberRow());
            rows.add(letterRow("+","×","÷","=","/","_","<",">","[","]"));
            rows.add(letterRow("!","@","#","$","%","^","&","*","(",")"));

            List<Key> r4 = new ArrayList<>();
            r4.add(k("2/2", null, 1f, ACT_SYMBOLS));
            for (String value : new String[]{"-","'","\"",":",";",",","?"}) {
                r4.add(normalKey(value));
            }
            r4.add(kRepeat("⌫", null, 1.15f, ACT_BACKSPACE));
            rows.add(r4);

            List<Key> bottom = new ArrayList<>();
            bottom.add(k("ABC", null, 1.0f, ACT_SYMBOLS));
            bottom.add(k("CLIP", null, 0.8f, ACT_CLIPBOARD));
            bottom.add(k("SPACE", null, 4.75f, ACT_SPACE));
            bottom.add(kRepeat("⌫", null, 1.2f, ACT_BACKSPACE));
            bottom.add(k("DONE", null, 1.35f, ACT_ENTER));
            rows.add(bottom);
        } else {
            rows.add(numberRow());
            rows.add(letterRow("`","~","\\","|","{","}","€","£","¥","₩"));
            rows.add(letterRow("°","•","○","●","□","■","♠","♡","◇","♣"));

            List<Key> r4 = new ArrayList<>();
            r4.add(k("1/2", null, 1f, ACT_SYMBOLS));
            for (String value : new String[]{"☆","▪","¤","《","》","¡","¿"}) {
                r4.add(normalKey(value));
            }
            r4.add(kRepeat("⌫", null, 1.15f, ACT_BACKSPACE));
            rows.add(r4);

            List<Key> bottom = new ArrayList<>();
            bottom.add(k("ABC", null, 1.0f, ACT_SYMBOLS));
            bottom.add(k("CLIP", null, 0.8f, ACT_CLIPBOARD));
            bottom.add(k("SPACE", null, 4.75f, ACT_SPACE));
            bottom.add(kRepeat("⌫", null, 1.2f, ACT_BACKSPACE));
            bottom.add(k("DONE", null, 1.35f, ACT_ENTER));
            rows.add(bottom);
        }

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

        if (key.action == ACT_SYMBOLS) {
            feedbackAsync();
            flashKey(key);

            if ("ABC".equals(key.label)) {
                page = 0;
            } else if ("2/2".equals(key.label)) {
                page = 2;
            } else if ("1/2".equals(key.label)) {
                page = 1;
            } else {
                page = 1;
            }

            build();
            return;
        }

        boolean oneShotShift = upper && page == 0 && key.action == ACT_TEXT;
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

    public void setSuggestions(String[] values) {
        wordSuggestions = values == null ? new String[0] : values;

        if (!numericMode && page == 0 && !rows.isEmpty()) {
            rows.set(0, numberRow());
        }

        invalidate();
    }

    private void drawWordSuggestions(Canvas c) {
        float top = headerH;
        float bottom = headerH + suggestionH;
        c.drawRect(0, top, getWidth(), bottom, suggestionBg);

        int count = Math.min(3, wordSuggestions.length);
        if (count <= 0) return;

        float cellW = getWidth() / (float)count;
        Paint.FontMetrics fm = suggestionText.getFontMetrics();
        float cy = (top + bottom) * 0.5f - (fm.ascent + fm.descent) * 0.5f;

        for (int i=0; i<count; i++) {
            String value = wordSuggestions[i] == null ? "" : wordSuggestions[i];
            c.drawText(value, cellW * (i + 0.5f), cy, suggestionText);
            if (i > 0) {
                float x = cellW * i;
                c.drawLine(x, top + dp(5), x, bottom - dp(5), suggestionDivider);
            }
        }
    }

    private boolean handleWordSuggestionTouch(MotionEvent e) {
        float top = headerH;
        float bottom = headerH + suggestionH;
        float y = e.getY();

        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && y >= top && y < bottom) {
            suggestionTouch = true;
            int count = Math.min(3, wordSuggestions.length);
            if (count > 0) {
                float cellW = getWidth() / (float)count;
                int index = Math.max(0, Math.min(count - 1, (int)(e.getX() / cellW)));
                String value = wordSuggestions[index];
                if (value != null && !value.isEmpty()) {
                    listener.onSuggestionSelected(value);
                    feedbackAsync();
                }
            }
            return true;
        }

        if (suggestionTouch) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                suggestionTouch = false;
            }
            return true;
        }
        return false;
    }


    private int xboxColor(String action) {
        switch (action) {
            case "confirm": return Color.rgb(66, 186, 85);
            case "back": return Color.rgb(224, 67, 61);
            case "delete": return Color.rgb(63, 151, 214);
            case "space": return Color.rgb(243, 197, 60);
        }
        return Color.WHITE;
    }

    private int playStationColor(String action) {
        switch (action) {
            case "confirm": return Color.rgb(82, 190, 235);
            case "back": return Color.rgb(245, 91, 94);
            case "delete": return Color.rgb(240, 105, 168);
            case "space": return Color.rgb(80, 222, 166);
        }
        return Color.WHITE;
    }

    private String xboxLetter(String action) {
        switch (action) {
            case "confirm": return "A";
            case "back": return "B";
            case "delete": return "X";
            case "space": return "Y";
        }
        return "";
    }

    private void drawControllerFaceBadge(
            Canvas c,
            String action,
            float cx,
            float cy,
            float size
    ) {
        float r = size * 0.5f;

        if (family == ControllerDetector.Family.XBOX) {
            controllerBadgeFill.setColor(xboxColor(action));
            c.drawCircle(cx, cy, r, controllerBadgeFill);

            controllerBadgeText.setColor(Color.rgb(15,15,15));
            controllerBadgeText.setTextSize(size * 0.62f);

            Paint.FontMetrics fm = controllerBadgeText.getFontMetrics();
            float ty = cy - (fm.ascent + fm.descent) / 2f;

            c.drawText(
                    xboxLetter(action),
                    cx,
                    ty,
                    controllerBadgeText
            );
            return;
        }

        if (family == ControllerDetector.Family.PLAYSTATION) {
            int color = playStationColor(action);

            controllerBadgeStroke.setColor(color);
            controllerBadgeStroke.setStrokeWidth(
                    Math.max(dp(1.15f), size * 0.13f)
            );

            float half = size * 0.38f;

            switch (action) {
                case "confirm":
                    c.drawLine(
                            cx - half, cy - half,
                            cx + half, cy + half,
                            controllerBadgeStroke
                    );
                    c.drawLine(
                            cx + half, cy - half,
                            cx - half, cy + half,
                            controllerBadgeStroke
                    );
                    break;

                case "back":
                    c.drawCircle(
                            cx,
                            cy,
                            half,
                            controllerBadgeStroke
                    );
                    break;

                case "delete":
                    c.drawRect(
                            cx - half,
                            cy - half,
                            cx + half,
                            cy + half,
                            controllerBadgeStroke
                    );
                    break;

                case "space":
                    controllerBadgePath.reset();
                    controllerBadgePath.moveTo(cx, cy - half);
                    controllerBadgePath.lineTo(cx + half, cy + half);
                    controllerBadgePath.lineTo(cx - half, cy + half);
                    controllerBadgePath.close();
                    c.drawPath(
                            controllerBadgePath,
                            controllerBadgeStroke
                    );
                    break;
            }
            return;
        }

        controllerBadgeFill.setColor(Color.rgb(48,48,48));
        c.drawCircle(cx, cy, r, controllerBadgeFill);

        controllerBadgeText.setColor(Color.WHITE);
        controllerBadgeText.setTextSize(size * 0.48f);

        Paint.FontMetrics fm = controllerBadgeText.getFontMetrics();

        c.drawText(
                badge(action),
                cx,
                cy - (fm.ascent + fm.descent) / 2f,
                controllerBadgeText
        );
    }

    private String shoulderLabel(String action) {
        if (family == ControllerDetector.Family.PLAYSTATION) {
            switch (action) {
                case "previous": return "L1";
                case "next": return "R1";
                case "shift": return "L2";
                case "done": return "R2";
            }
        }

        if (family == ControllerDetector.Family.XBOX) {
            switch (action) {
                case "previous": return "LB";
                case "next": return "RB";
                case "shift": return "LT";
                case "done": return "RT";
            }
        }

        switch (action) {
            case "previous": return "L1";
            case "next": return "R1";
            case "shift": return "L2";
            case "done": return "R2";
        }

        return "";
    }

    private void drawShoulderBadge(
            Canvas c,
            String action,
            float cx,
            float cy,
            float height
    ) {
        String label = shoulderLabel(action);
        float width = Math.max(dp(14), height * 1.65f);

        RectF rect = new RectF(
                cx - width / 2f,
                cy - height / 2f,
                cx + width / 2f,
                cy + height / 2f
        );

        controllerBadgeFill.setColor(Color.rgb(24,24,24));
        c.drawRoundRect(
                rect,
                height * 0.34f,
                height * 0.34f,
                controllerBadgeFill
        );

        controllerBadgeStroke.setColor(Color.rgb(205,205,205));
        controllerBadgeStroke.setStrokeWidth(dp(1));

        c.drawRoundRect(
                rect,
                height * 0.34f,
                height * 0.34f,
                controllerBadgeStroke
        );

        controllerBadgeText.setColor(Color.WHITE);
        controllerBadgeText.setTextSize(height * 0.55f);

        Paint.FontMetrics fm = controllerBadgeText.getFontMetrics();

        c.drawText(
                label,
                rect.centerX(),
                rect.centerY() -
                (fm.ascent + fm.descent) / 2f,
                controllerBadgeText
        );
    }

    private void drawActionBadges(
            Canvas c,
            Key key,
            boolean selected
    ) {
        if (key == null ||
                key.rect.width() <= 0f ||
                key.rect.height() <= 0f) {
            return;
        }

        float face = Math.min(
                dp(12),
                key.rect.height() * 0.23f
        );

        float shoulderH = Math.min(
                dp(8.5f),
                key.rect.height() * 0.16f
        );

        float topY = key.rect.top +
                Math.max(dp(6), face * 0.62f);

        float leftX = key.rect.left +
                Math.max(dp(8), face * 0.65f);

        float rightX = key.rect.right -
                Math.max(dp(8), face * 0.65f);

        if (selected) {
            drawControllerFaceBadge(
                    c,
                    "confirm",
                    rightX,
                    topY,
                    face
            );
        }

        switch (key.action) {
            case ACT_BACKSPACE:
                drawControllerFaceBadge(
                        c,
                        "delete",
                        leftX,
                        topY,
                        face
                );
                break;

            case ACT_SPACE:
                drawControllerFaceBadge(
                        c,
                        "space",
                        leftX,
                        topY,
                        face
                );
                break;

            case ACT_ENTER:
                drawShoulderBadge(
                        c,
                        "done",
                        leftX + dp(2),
                        topY,
                        shoulderH
                );
                break;

            case ACT_SHIFT:
                drawShoulderBadge(
                        c,
                        "shift",
                        leftX + dp(2),
                        topY,
                        shoulderH
                );
                break;

            case ACT_SYMBOLS:
                float center = key.rect.centerX();

                drawShoulderBadge(
                        c,
                        "previous",
                        center - dp(7.2f),
                        topY,
                        shoulderH
                );

                drawShoulderBadge(
                        c,
                        "next",
                        center + dp(7.2f),
                        topY,
                        shoulderH
                );
                break;
        }
    }

    private void previousSymbolPage() {
        if (numericMode) return;

        page--;
        if (page < 0) page = 2;

        upper = false;
        build();
    }

    private void nextSymbolPage() {
        if (numericMode) return;

        page++;
        if (page > 2) page = 0;

        upper = false;
        build();
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
                name + "   D-PAD navegar",
                dp(12),
                dp(16),
                hint
        );

        hint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(
                badge("back") + " voltar",
                getWidth() - dp(12),
                dp(16),
                hint
        );

        layoutRows(headerH, getHeight() - dp(7));

        for (int r=0; r<rows.size(); r++) {
            for (int col=0; col<rows.get(r).size(); col++) {
                Key key = rows.get(r).get(col);

                boolean selected =
                        r == selRow &&
                        col == selCol;

                drawKey(
                        c,
                        key,
                        selected
                );

                drawActionBadges(
                        c,
                        key,
                        selected
                );
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

    private void moveControllerSelection(
            int dx,
            int dy
    ) {
        if (dx < 0) {
            if (isLongPressPopupOpen()) {
                movePopupSelection(-1);
                return;
            }

            selCol--;
            clamp();
            return;
        }

        if (dx > 0) {
            if (isLongPressPopupOpen()) {
                movePopupSelection(1);
                return;
            }

            selCol++;
            clamp();
            return;
        }

        if (dy < 0) {
            if (isLongPressPopupOpen()) {
                return;
            }

            selRow--;
            clamp();
            return;
        }

        if (dy > 0) {
            if (isLongPressPopupOpen()) {
                return;
            }

            selRow++;
            clamp();
        }
    }

    public boolean handleGamepadMotion(
            MotionEvent event
    ) {
        if (event == null ||
                event.getActionMasked() !=
                MotionEvent.ACTION_MOVE) {
            return false;
        }

        InputDevice device =
                event.getDevice();

        if (!ControllerDetector.isGamepad(device)) {
            return false;
        }

        float hatX =
                event.getAxisValue(
                        MotionEvent.AXIS_HAT_X
                );

        float hatY =
                event.getAxisValue(
                        MotionEvent.AXIS_HAT_Y
                );

        float axisX =
                event.getAxisValue(
                        MotionEvent.AXIS_X
                );

        float axisY =
                event.getAxisValue(
                        MotionEvent.AXIS_Y
                );

        float x =
                Math.abs(hatX) >= 0.45f
                        ? hatX
                        : axisX;

        float y =
                Math.abs(hatY) >= 0.45f
                        ? hatY
                        : axisY;

        final float deadZone = 0.58f;

        int dirX =
                x > deadZone
                        ? 1
                        : x < -deadZone
                        ? -1
                        : 0;

        int dirY =
                y > deadZone
                        ? 1
                        : y < -deadZone
                        ? -1
                        : 0;

        if (dirX == 0 &&
                dirY == 0) {
            motionDirX = 0;
            motionDirY = 0;
            lastMotionMoveMs = 0L;
            return true;
        }

        if (dirX != 0 &&
                dirY != 0) {
            if (Math.abs(x) >=
                    Math.abs(y)) {
                dirY = 0;
            } else {
                dirX = 0;
            }
        }

        long now =
                SystemClock.uptimeMillis();

        boolean changed =
                dirX != motionDirX ||
                dirY != motionDirY;

        if (changed ||
                now - lastMotionMoveMs >= 135L) {

            motionDirX = dirX;
            motionDirY = dirY;
            lastMotionMoveMs = now;

            moveControllerSelection(
                    dirX,
                    dirY
            );
        }

        return true;
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
                    moveControllerSelection(-1, 0);
                    return true;

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveControllerSelection(1, 0);
                    return true;

                case KeyEvent.KEYCODE_DPAD_UP:
                    moveControllerSelection(0, -1);
                    return true;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveControllerSelection(0, 1);
                    return true;

                case KeyEvent.KEYCODE_BUTTON_L2:
                    if (!numericMode && page == 0) {
                        feedbackAsync();
                        flashAction(ACT_SHIFT);
                        upper = !upper;
                        build();
                    }
                    return true;

                case KeyEvent.KEYCODE_BUTTON_L1:
                    feedbackAsync();
                    previousSymbolPage();
                    return true;

                case KeyEvent.KEYCODE_BUTTON_R1:
                    feedbackAsync();
                    nextSymbolPage();
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
