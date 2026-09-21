package com.consolekey.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PortraitKeyboardView extends BaseKeyboardView {
    private static final String PREF_EMOJI_RECENTS = "emoji_recents";
    private static final String RECENT_SEPARATOR = "\u001F";

    private boolean upper = false;
    private int page = 0;
    private boolean emojiMode = false;

    // 0 = busca visual, 1 = recentes, 2..10 = categorias
    private int emojiCategory = 2;

    private final Paint toolbar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolbarActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiActiveIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float toolbarH;
    private float emojiModeBarH;
    private float emojiCategoryBarH;
    private float suggestionH;

    private String[] wordSuggestions = new String[0];
    private final Paint suggestionBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suggestionText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suggestionDivider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean suggestionTouch = false;

    private final OverScroller emojiScroller;
    private VelocityTracker emojiVelocity;
    private final int emojiTouchSlop;
    private float emojiScrollY = 0f;
    private float emojiMaxScroll = 0f;
    private float emojiLastY = 0f;
    private float emojiDownY = 0f;
    private boolean emojiScrolling = false;

    private final Paint emojiScrollBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Cache da geometria visível: não recalculamos centenas de emojis a cada frame.
    private int emojiFirstVisibleRow = 0;
    private int emojiLastVisibleRow = -1;
    private float emojiGridTop = 0f;
    private float emojiGridBottom = 0f;
    private float emojiGridLeft = 0f;
    private float emojiGridCellW = 0f;
    private float emojiGridRowH = 0f;

    private final String[] emojiModeIcons = {"⌨","🙂",":-)","GIF","▣","◉","+"};
    private final String[] emojiCategoryIcons = {"⌕","◷","☺","👤","🐾","🍴","⚽","✈","💡","!?","⚑"};

    public PortraitKeyboardView(Context c, Listener l) {
        super(c, l);

        setPadding((int)dp(6),(int)dp(6),(int)dp(6),(int)dp(6));

        toolbarH = dp(40);
        emojiModeBarH = dp(46);
        emojiCategoryBarH = dp(42);
        suggestionH = dp(30);

        suggestionBg.setColor(Color.rgb(10,10,10));
        suggestionText.setColor(Color.WHITE);
        suggestionText.setTextAlign(Paint.Align.CENTER);
        suggestionText.setTextSize(dp(14));
        suggestionDivider.setColor(Color.rgb(42,42,42));
        suggestionDivider.setStrokeWidth(dp(1));

        toolbar.setColor(Color.WHITE);
        toolbar.setTextAlign(Paint.Align.CENTER);
        toolbar.setTextSize(dp(18));

        toolbarActive.setColor(Color.rgb(70,70,70));

        emojiBarPaint.setColor(Color.rgb(12,12,12));

        emojiIconPaint.setColor(Color.WHITE);
        emojiIconPaint.setTextAlign(Paint.Align.CENTER);
        emojiIconPaint.setTextSize(dp(20));

        emojiActiveIconPaint.setColor(Color.BLACK);
        emojiActiveIconPaint.setTextAlign(Paint.Align.CENTER);
        emojiActiveIconPaint.setTextSize(dp(20));

        emojiHintPaint.setColor(Color.rgb(165,165,165));
        emojiHintPaint.setTextAlign(Paint.Align.CENTER);
        emojiHintPaint.setTextSize(dp(14));

        dividerPaint.setColor(Color.rgb(38,38,38));
        dividerPaint.setStrokeWidth(dp(1));

        emojiScrollBarPaint.setColor(Color.rgb(115,115,115));
        emojiSelectedPaint.setColor(Color.rgb(247,184,35));

        emojiScroller = new OverScroller(c);
        emojiTouchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        rebuild();
    }

    public void setSuggestions(String[] values) {
        wordSuggestions = values == null ? new String[0] : values;
        invalidate();
    }

    private void drawWordSuggestions(Canvas c) {
        float top = toolbarH;
        float bottom = toolbarH + suggestionH;
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
                c.drawLine(x, top + dp(7), x, bottom - dp(7), suggestionDivider);
            }
        }
    }

    private boolean handleWordSuggestionTouch(MotionEvent e) {
        if (emojiMode) return false;

        float top = toolbarH;
        float bottom = toolbarH + suggestionH;
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

    @Override protected boolean fastTouchMode() {
        return !emojiMode;
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

    private Key emojiKey(String emoji) {
        return kAlt(
                emoji,
                emoji,
                1f,
                ACT_TEXT,
                EmojiData.variants(emoji)
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

        for (String s : values) {
            out.add(letterKey(s));
        }

        return out;
    }

    private List<Key> row(String... values) {
        List<Key> out = new ArrayList<>();

        for (String s : values) {
            out.add(normalKey(s));
        }

        return out;
    }

    private SharedPreferences emojiPrefs() {
        return getContext().getSharedPreferences(ConsoleImeService.PREFS, Context.MODE_PRIVATE);
    }

    private List<String> loadRecents() {
        List<String> out = new ArrayList<>();

        String packed = emojiPrefs().getString(PREF_EMOJI_RECENTS, "");
        if (packed == null || packed.isEmpty()) return out;

        String[] parts = packed.split(RECENT_SEPARATOR, -1);

        for (String s : parts) {
            if (s != null && !s.isEmpty()) out.add(s);
        }

        return out;
    }

    private void saveRecent(String emoji) {
        if (emoji == null || emoji.isEmpty()) return;

        List<String> recents = loadRecents();
        recents.remove(emoji);
        recents.add(0, emoji);

        while (recents.size() > 48) {
            recents.remove(recents.size() - 1);
        }

        StringBuilder packed = new StringBuilder();

        for (String item : recents) {
            if (packed.length() > 0) packed.append(RECENT_SEPARATOR);
            packed.append(item);
        }

        emojiPrefs().edit()
                .putString(PREF_EMOJI_RECENTS, packed.toString())
                .apply();
    }

    private String[] currentEmojiItems() {
        if (emojiCategory == 1) {
            List<String> recent = loadRecents();
            return recent.toArray(new String[0]);
        }

        if (emojiCategory >= 2 && emojiCategory <= 10) {
            return EmojiData.category(getContext(), emojiCategory - 2);
        }

        return EmojiData.category(getContext(), 0);
    }

    private void rebuildEmoji() {
        rows.clear();

        String[] items = currentEmojiItems();

        int columns = 8;

        for (int start = 0; start < items.length; start += columns) {
            List<Key> r = new ArrayList<>();

            for (int i=start; i<Math.min(start + columns, items.length); i++) {
                r.add(emojiKey(items[i]));
            }

            rows.add(r);
        }

        invalidate();
    }

    private void rebuild() {
        if (emojiMode) {
            rebuildEmoji();
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

        if (emojiMode) {
            if (key.action == ACT_TEXT && key.value != null) {
                super.perform(key);
                saveRecent(key.value);

                if (emojiCategory == 1) {
                    rebuildEmoji();
                }

                return;
            }

            super.perform(key);
            return;
        }

        if (key.action == ACT_SHIFT) {
            feedbackAsync();
            flashKey(key);
            upper = !upper;
            rebuild();
            return;
        }

        if (key.action == ACT_SYMBOLS) {
            feedbackAsync();
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

        if (emojiMode) {
            saveRecent(value);

            if (emojiCategory == 1) {
                rebuildEmoji();
            }

            return;
        }

        if (upper && page == 0) {
            upper = false;
            rebuild();
        }
    }

    private void drawNormalKeyboard(Canvas c) {
        String[] icons = {"☺","A↔","▣","⚙","•••"};
        float segment = getWidth() / (float)icons.length;

        for (int i=0; i<icons.length; i++) {
            c.drawText(icons[i], segment * (i + .5f), dp(27), toolbar);
        }

        drawWordSuggestions(c);
        layoutRows(toolbarH + suggestionH + dp(5), getHeight() - dp(6));

        for (List<Key> row : rows) {
            for (Key key : row) {
                drawKey(c, key, false);
            }
        }

        drawLongPressPopup(c);
    }

    private void layoutEmojiGrid(float top, float bottom) {
        float viewportH = Math.max(1f, bottom - top);
        float rowH = dp(48);

        emojiGridTop = top;
        emojiGridBottom = bottom;
        emojiGridLeft = dp(12);
        emojiGridRowH = rowH;

        float right = getWidth() - dp(12);
        emojiGridCellW = Math.max(1f, (right - emojiGridLeft) / 8f);

        emojiMaxScroll = Math.max(0f, rows.size() * rowH - viewportH);
        emojiScrollY = Math.max(0f, Math.min(emojiScrollY, emojiMaxScroll));

        if (rows.isEmpty()) {
            emojiFirstVisibleRow = 0;
            emojiLastVisibleRow = -1;
            return;
        }

        emojiFirstVisibleRow = Math.max(
                0,
                Math.min(rows.size() - 1, (int)Math.floor(emojiScrollY / rowH))
        );

        emojiLastVisibleRow = Math.max(
                emojiFirstVisibleRow,
                Math.min(
                        rows.size() - 1,
                        (int)Math.ceil((emojiScrollY + viewportH) / rowH)
                )
        );

        // Só cria retângulos para as linhas que realmente estão na tela
        // (+ a última parcial). Antes isso era feito para a categoria inteira
        // em cada frame da rolagem.
        for (int r = emojiFirstVisibleRow; r <= emojiLastVisibleRow; r++) {
            List<Key> row = rows.get(r);
            float t = top + r * rowH - emojiScrollY;

            for (int col = 0; col < row.size(); col++) {
                float l = emojiGridLeft + col * emojiGridCellW;

                row.get(col).rect.set(
                        l,
                        t,
                        l + emojiGridCellW,
                        t + rowH
                );
            }
        }
    }

    @Override protected Key hit(float x, float y) {
        if (!emojiMode) return super.hit(x, y);

        if (rows.isEmpty()) return null;

        float top = emojiModeBarH + emojiCategoryBarH + dp(4);
        float bottom = getHeight() - dp(4);

        if (y < top || y > bottom) return null;

        float left = dp(12);
        float right = getWidth() - dp(12);

        if (x < left || x > right) return null;

        float rowH = dp(48);
        float cellW = Math.max(1f, (right - left) / 8f);

        int rowIndex = (int)((y - top + emojiScrollY) / rowH);
        int colIndex = (int)((x - left) / cellW);

        if (rowIndex < 0 || rowIndex >= rows.size()) return null;

        List<Key> row = rows.get(rowIndex);
        if (colIndex < 0 || colIndex >= row.size()) return null;

        Key key = row.get(colIndex);

        // Mantém o rect da tecla correto para animação e popup de long press.
        float keyTop = top + rowIndex * rowH - emojiScrollY;
        float keyLeft = left + colIndex * cellW;
        key.rect.set(keyLeft, keyTop, keyLeft + cellW, keyTop + rowH);

        return key;
    }

    private void stopEmojiFling() {
        if (!emojiScroller.isFinished()) {
            emojiScroller.forceFinished(true);
        }
    }

    private void resetEmojiScroll() {
        stopEmojiFling();
        emojiScrollY = 0f;
        emojiMaxScroll = 0f;
        invalidate();
    }

    private void scrollEmojiBy(float deltaY) {
        emojiScrollY = Math.max(0f, Math.min(emojiMaxScroll, emojiScrollY + deltaY));
        invalidate();
    }

    private void flingEmoji(float velocityY) {
        if (emojiMaxScroll <= 0f) return;

        emojiScroller.fling(
                0,
                Math.round(emojiScrollY),
                0,
                Math.round(-velocityY),
                0,
                0,
                0,
                Math.round(emojiMaxScroll)
        );

        postInvalidateOnAnimation();
    }

    private void recycleEmojiVelocity() {
        if (emojiVelocity != null) {
            emojiVelocity.recycle();
            emojiVelocity = null;
        }
    }

    private void cancelBasePress(MotionEvent source) {
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(cancel);
        cancel.recycle();
    }

    private void drawEmojiModeBar(Canvas c) {
        float width = getWidth();
        float segment = width / emojiModeIcons.length;

        c.drawRect(0, 0, width, emojiModeBarH, emojiBarPaint);
        c.drawLine(0, emojiModeBarH, width, emojiModeBarH, dividerPaint);

        for (int i=0; i<emojiModeIcons.length; i++) {
            float cx = segment * (i + 0.5f);
            float cy = emojiModeBarH * 0.5f;

            if (i == 1) {
                c.drawCircle(cx, cy, dp(19), emojiSelectedPaint);

                Paint.FontMetrics fm = emojiActiveIconPaint.getFontMetrics();
                float ty = cy - (fm.ascent + fm.descent) / 2f;
                c.drawText(emojiModeIcons[i], cx, ty, emojiActiveIconPaint);
            } else {
                emojiIconPaint.setAlpha(i == 0 ? 255 : 150);

                Paint.FontMetrics fm = emojiIconPaint.getFontMetrics();
                float ty = cy - (fm.ascent + fm.descent) / 2f;
                c.drawText(emojiModeIcons[i], cx, ty, emojiIconPaint);
            }
        }

        emojiIconPaint.setAlpha(255);
    }

    private void drawEmojiCategoryBar(Canvas c) {
        float top = emojiModeBarH;
        float bottom = top + emojiCategoryBarH;
        float width = getWidth();
        float segment = width / emojiCategoryIcons.length;

        c.drawRect(0, top, width, bottom, emojiBarPaint);
        c.drawLine(0, bottom, width, bottom, dividerPaint);

        for (int i=0; i<emojiCategoryIcons.length; i++) {
            float cx = segment * (i + 0.5f);
            float cy = (top + bottom) * 0.5f;

            boolean selected = i == emojiCategory;

            if (selected) {
                c.drawRoundRect(
                        cx - dp(15),
                        cy - dp(15),
                        cx + dp(15),
                        cy + dp(15),
                        dp(15),
                        dp(15),
                        emojiSelectedPaint
                );
            }

            Paint p = selected ? emojiActiveIconPaint : emojiIconPaint;
            p.setTextSize(i == 0 ? dp(25) : dp(18));

            Paint.FontMetrics fm = p.getFontMetrics();
            float ty = cy - (fm.ascent + fm.descent) / 2f;

            c.drawText(emojiCategoryIcons[i], cx, ty, p);
        }

        emojiIconPaint.setTextSize(dp(20));
        emojiActiveIconPaint.setTextSize(dp(20));
    }

    private void drawEmojiGrid(Canvas c) {
        float top = emojiModeBarH + emojiCategoryBarH + dp(4);
        float bottom = getHeight() - dp(4);

        layoutEmojiGrid(top, bottom);

        if (emojiCategory == 1 && rows.isEmpty()) {
            float cy = (top + bottom) * 0.5f;
            c.drawText(
                    "Seus emojis recentes aparecem aqui",
                    getWidth() * 0.5f,
                    cy,
                    emojiHintPaint
            );
            return;
        }

        int save = c.save();
        c.clipRect(0, top, getWidth(), bottom);

        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Color.WHITE);

        // Todas as células de emoji têm a mesma altura, então tamanho da fonte
        // e FontMetrics são calculados uma vez por frame.
        float size = Math.min(dp(30), dp(48) * 0.62f);
        text.setTextSize(size);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baselineOffset = - (fm.ascent + fm.descent) / 2f;

        if (emojiLastVisibleRow >= emojiFirstVisibleRow) {
            for (int r = emojiFirstVisibleRow; r <= emojiLastVisibleRow; r++) {
                List<Key> row = rows.get(r);

                for (Key key : row) {
                    c.drawText(
                            key.label,
                            key.rect.centerX(),
                            key.rect.centerY() + baselineOffset,
                            text
                    );
                }
            }
        }

        drawLongPressPopup(c);
        c.restoreToCount(save);

        if (emojiMaxScroll > 0f) {
            float viewportH = bottom - top;
            float contentH = viewportH + emojiMaxScroll;
            float thumbH = Math.max(dp(24), viewportH * viewportH / contentH);
            float track = viewportH - thumbH;
            float fraction = emojiScrollY / emojiMaxScroll;
            float thumbTop = top + track * fraction;

            c.drawRoundRect(
                    getWidth() - dp(3.5f),
                    thumbTop,
                    getWidth() - dp(1.5f),
                    thumbTop + thumbH,
                    dp(2),
                    dp(2),
                    emojiScrollBarPaint
            );
        }
    }

    private void drawEmojiPanel(Canvas c) {
        drawEmojiModeBar(c);
        drawEmojiCategoryBar(c);
        drawEmojiGrid(c);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.BLACK);

        if (emojiMode) {
            drawEmojiPanel(c);
        } else {
            drawNormalKeyboard(c);
        }
    }

    private boolean handleEmojiBars(MotionEvent e) {
        if (!emojiMode || e.getAction() != MotionEvent.ACTION_UP) return false;

        float y = e.getY();
        float x = e.getX();

        if (y < emojiModeBarH) {
            int index = Math.min(
                    emojiModeIcons.length - 1,
                    Math.max(0, (int)(x / (getWidth() / emojiModeIcons.length)))
            );

            feedbackAsync();

            if (index == 0) {
                emojiMode = false;
                page = 0;
                rebuild();
            }

            return true;
        }

        if (y < emojiModeBarH + emojiCategoryBarH) {
            int index = Math.min(
                    emojiCategoryIcons.length - 1,
                    Math.max(0, (int)(x / (getWidth() / emojiCategoryIcons.length)))
            );

            feedbackAsync();

            // A lupa fica visualmente igual ao Samsung. Enquanto não há busca textual,
            // ela leva às carinhas em vez de quebrar a experiência.
            if (index == 0) {
                emojiCategory = 2;
            } else {
                emojiCategory = index;
            }

            resetEmojiScroll();
            rebuildEmoji();
            return true;
        }

        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (handleWordSuggestionTouch(e)) return true;
        if (!emojiMode && e.getY() < toolbarH) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float seg = getWidth() / 5f;
                int i = Math.min(4, (int)(e.getX() / seg));

                feedbackAsync();

                if (i == 0) {
                    emojiMode = true;
                    emojiCategory = 2;
                    resetEmojiScroll();
                    rebuildEmoji();
                } else if (i == 3) {
                    listener.onOpenSettings();
                }
            }

            return true;
        }

        if (!emojiMode) {
            return super.onTouchEvent(e);
        }

        float barsBottom = emojiModeBarH + emojiCategoryBarH;

        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && e.getY() < barsBottom) {
            return true;
        }

        if (e.getActionMasked() == MotionEvent.ACTION_UP &&
                emojiVelocity == null &&
                e.getY() < barsBottom) {
            if (handleEmojiBars(e)) return true;
            return true;
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopEmojiFling();
                recycleEmojiVelocity();
                emojiVelocity = VelocityTracker.obtain();
                emojiVelocity.addMovement(e);
                emojiLastY = e.getY();
                emojiDownY = e.getY();
                emojiScrolling = false;
                return super.onTouchEvent(e);

            case MotionEvent.ACTION_MOVE:
                if (emojiVelocity != null) emojiVelocity.addMovement(e);

                if (isLongPressPopupOpen()) {
                    return super.onTouchEvent(e);
                }

                if (!emojiScrolling && Math.abs(e.getY() - emojiDownY) > emojiTouchSlop) {
                    emojiScrolling = true;
                    cancelBasePress(e);
                }

                if (emojiScrolling) {
                    float delta = emojiLastY - e.getY();
                    emojiLastY = e.getY();
                    scrollEmojiBy(delta);
                    return true;
                }

                emojiLastY = e.getY();
                return super.onTouchEvent(e);

            case MotionEvent.ACTION_UP:
                if (emojiVelocity != null) emojiVelocity.addMovement(e);

                if (emojiScrolling) {
                    if (emojiVelocity != null) {
                        emojiVelocity.computeCurrentVelocity(1000);
                        flingEmoji(emojiVelocity.getYVelocity());
                    }

                    recycleEmojiVelocity();
                    emojiScrolling = false;
                    return true;
                }

                recycleEmojiVelocity();
                return super.onTouchEvent(e);

            case MotionEvent.ACTION_CANCEL:
                recycleEmojiVelocity();
                emojiScrolling = false;
                return super.onTouchEvent(e);
        }

        return true;
    }

    @Override public void computeScroll() {
        super.computeScroll();

        if (emojiMode && emojiScroller.computeScrollOffset()) {
            emojiScrollY = Math.max(
                    0f,
                    Math.min(emojiMaxScroll, emojiScroller.getCurrY())
            );

            postInvalidateOnAnimation();
        }
    }
}
