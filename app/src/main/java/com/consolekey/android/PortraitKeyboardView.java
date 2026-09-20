package com.consolekey.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

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

    private final String[] emojiModeIcons = {"⌨","🙂",":-)","GIF","▣","◉","+"};
    private final String[] emojiCategoryIcons = {"⌕","◷","☺","👤","🐾","🍴","⚽","✈","💡","!?","⚑"};

    public PortraitKeyboardView(Context c, Listener l) {
        super(c, l);

        setPadding((int)dp(6),(int)dp(6),(int)dp(6),(int)dp(6));

        toolbarH = dp(40);
        emojiModeBarH = dp(46);
        emojiCategoryBarH = dp(42);

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

        while (recents.size() > 32) {
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
            return EmojiData.category(emojiCategory - 2);
        }

        return EmojiData.category(0);
    }

    private void rebuildEmoji() {
        rows.clear();

        String[] items = currentEmojiItems();

        int columns = 8;
        int limit = Math.min(items.length, 32);

        for (int start = 0; start < limit; start += columns) {
            List<Key> r = new ArrayList<>();

            for (int i=start; i<Math.min(start + columns, limit); i++) {
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

        layoutRows(toolbarH + dp(6), getHeight() - dp(6));

        for (List<Key> row : rows) {
            for (Key key : row) {
                drawKey(c, key, false);
            }
        }

        drawLongPressPopup(c);
    }

    private void layoutEmojiGrid(float top, float bottom) {
        if (rows.isEmpty()) return;

        int maxRows = Math.max(1, rows.size());
        float rowH = (bottom - top) / maxRows;
        float left = dp(12);
        float right = getWidth() - dp(12);
        float totalW = right - left;
        float cellW = totalW / 8f;

        for (int r=0; r<rows.size(); r++) {
            List<Key> row = rows.get(r);

            for (int col=0; col<row.size(); col++) {
                float l = left + col * cellW;
                float t = top + r * rowH;

                row.get(col).rect.set(
                        l,
                        t,
                        l + cellW,
                        t + rowH
                );
            }
        }
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
                Paint selected = new Paint(Paint.ANTI_ALIAS_FLAG);
                selected.setColor(Color.rgb(247,184,35));

                c.drawCircle(cx, cy, dp(19), selected);

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
                Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
                active.setColor(Color.rgb(247,184,35));

                c.drawRoundRect(
                        cx - dp(15),
                        cy - dp(15),
                        cx + dp(15),
                        cy + dp(15),
                        dp(15),
                        dp(15),
                        active
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
            c.drawText("Seus emojis recentes aparecem aqui", getWidth() * 0.5f, cy, emojiHintPaint);
            return;
        }

        text.setTextAlign(Paint.Align.CENTER);

        for (List<Key> row : rows) {
            for (Key key : row) {
                float size = Math.min(dp(30), key.rect.height() * 0.62f);
                text.setTextSize(size);
                text.setColor(Color.WHITE);

                Paint.FontMetrics fm = text.getFontMetrics();
                float cy = key.rect.centerY() - (fm.ascent + fm.descent) / 2f;

                c.drawText(key.label, key.rect.centerX(), cy, text);
            }
        }

        drawLongPressPopup(c);
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

            listener.onKeyFeedback();

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

            listener.onKeyFeedback();

            // A lupa fica visualmente igual ao Samsung. Enquanto não há busca textual,
            // ela leva às carinhas em vez de quebrar a experiência.
            if (index == 0) {
                emojiCategory = 2;
            } else {
                emojiCategory = index;
            }

            rebuildEmoji();
            return true;
        }

        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!emojiMode && e.getAction() == MotionEvent.ACTION_UP && e.getY() < toolbarH) {
            float seg = getWidth() / 5f;
            int i = Math.min(4, (int)(e.getX() / seg));

            listener.onKeyFeedback();

            if (i == 0) {
                emojiMode = true;
                emojiCategory = 2;
                rebuildEmoji();
            } else if (i == 3) {
                listener.onOpenSettings();
            }

            return true;
        }

        if (handleEmojiBars(e)) {
            return true;
        }

        return super.onTouchEvent(e);
    }
}
