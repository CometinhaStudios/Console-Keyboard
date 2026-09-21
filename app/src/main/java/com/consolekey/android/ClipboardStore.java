package com.consolekey.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class ClipboardStore {
    private static final String PREFS =
            "clipboard_history";

    private static final String KEY_ITEMS =
            "items";

    private static final int MAX_ITEMS = 30;
    private static final int MAX_CHARS = 8000;

    private final SharedPreferences prefs;

    public ClipboardStore(Context context) {
        prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );
    }

    public synchronized List<String> getAll() {
        List<String> out =
                new ArrayList<>();

        String raw =
                prefs.getString(
                        KEY_ITEMS,
                        "[]"
                );

        try {
            JSONArray array =
                    new JSONArray(raw);

            for (int i=0;
                 i<array.length();
                 i++) {

                String value =
                        array.optString(
                                i,
                                ""
                        );

                if (!value.isEmpty()) {
                    out.add(value);
                }
            }
        } catch (Throwable ignored) {}

        return out;
    }

    public synchronized void add(
            String text
    ) {
        if (text == null) {
            return;
        }

        String value =
                text.trim();

        if (value.isEmpty()) {
            return;
        }

        if (value.length() > MAX_CHARS) {
            value =
                    value.substring(
                            0,
                            MAX_CHARS
                    );
        }

        List<String> items =
                getAll();

        items.remove(value);
        items.add(0, value);

        while (items.size() > MAX_ITEMS) {
            items.remove(
                    items.size() - 1
            );
        }

        JSONArray array =
                new JSONArray();

        for (String item : items) {
            array.put(item);
        }

        prefs.edit()
                .putString(
                        KEY_ITEMS,
                        array.toString()
                )
                .apply();
    }

    public synchronized void clear() {
        prefs.edit()
                .remove(KEY_ITEMS)
                .apply();
    }
}
