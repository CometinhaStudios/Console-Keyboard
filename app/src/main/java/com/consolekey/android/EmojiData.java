package com.consolekey.android;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EmojiData {
    private EmojiData() {}

    public static final String[] CATEGORY_ICONS = {
            "☺", "👤", "🐾", "🍴", "⚽", "✈", "💡", "!?", "⚑"
    };

    public static final String[] CATEGORY_NAMES = {
            "Carinhas",
            "Pessoas",
            "Animais",
            "Comida",
            "Atividades",
            "Viagem",
            "Objetos",
            "Símbolos",
            "Bandeiras"
    };

    private static final String[][] CACHE = new String[9][];

    private static final Set<String> SKIN_TONE_SUPPORTED = new HashSet<>(Arrays.asList(
            "👋","🤚","🖐️","✋","🖖","🫱","🫲","🫳","🫴",
            "👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙",
            "👈","👉","👆","👇","☝️","🫵","👍","👎","✊",
            "👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🙏",
            "✍️","💅","🤳","💪","🦵","🦶","👂","👃"
    ));

    public static String[] category(Context context, int index) {
        if (index < 0 || index >= CACHE.length) return new String[0];

        String[] cached = CACHE[index];
        if (cached != null) return cached;

        List<String> out = new ArrayList<>();
        String asset = "emoji/" + index + ".txt";

        try (
                InputStream in = context.getAssets().open(asset);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8),
                        8192
                )
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (!value.isEmpty()) out.add(value);
            }
        } catch (Throwable ignored) {
        }

        String[] loaded = out.toArray(new String[0]);
        CACHE[index] = loaded;
        return loaded;
    }

    public static String[] variants(String emoji) {
        if ("❤️".equals(emoji) || "❤".equals(emoji)) {
            return new String[]{
                    "🧡","💛","💚","💙","🩵","💜","🖤","🩶","🤍","🤎"
            };
        }

        if (SKIN_TONE_SUPPORTED.contains(emoji)) {
            String base = emoji.replace("\uFE0F", "");

            return new String[]{
                    base + "🏻",
                    base + "🏼",
                    base + "🏽",
                    base + "🏾",
                    base + "🏿"
            };
        }

        return new String[0];
    }
}
