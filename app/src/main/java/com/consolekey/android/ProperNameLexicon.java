package com.consolekey.android;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public class ProperNameLexicon {
    private static class Item {
        final String word;
        final String norm;
        final int order;

        Item(String word, int order) {
            this.word = word;
            this.norm = LocalDictionary.normalize(word);
            this.order = order;
        }
    }

    private static class Ranked {
        final Item item;
        final double score;

        Ranked(Item item, double score) {
            this.item = item;
            this.score = score;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public ProperNameLexicon(Context context) {
        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        context.getAssets().open(
                                                "dictionary/proper_names.txt"
                                        ),
                                        "UTF-8"
                                )
                        )
        ) {
            String line;
            int order = 0;

            while ((line = reader.readLine()) != null) {
                String word = line.trim();

                if (word.isEmpty() ||
                        word.startsWith("#")) {
                    continue;
                }

                String norm =
                        LocalDictionary.normalize(word);

                if (norm.length() < 2) {
                    continue;
                }

                items.add(
                        new Item(
                                word,
                                order++
                        )
                );
            }
        } catch (Throwable ignored) {}
    }

    public String[] suggest(
            String typed,
            int limit
    ) {
        if (typed == null ||
                typed.isEmpty() ||
                limit <= 0) {
            return new String[0];
        }

        String prefix =
                LocalDictionary.normalize(
                        typed
                );

        if (prefix.isEmpty()) {
            return new String[0];
        }

        List<Ranked> ranked =
                new ArrayList<>();

        for (Item item : items) {
            if (item.norm.startsWith(prefix)) {
                double score =
                        item.order * 0.01 +
                        Math.max(
                                0,
                                item.norm.length() -
                                prefix.length()
                        ) * 0.001;

                ranked.add(
                        new Ranked(
                                item,
                                score
                        )
                );
                continue;
            }

            if (prefix.length() >= 3 &&
                    Math.abs(
                            item.norm.length() -
                            prefix.length()
                    ) <= 1) {

                int distance =
                        damerau(
                                prefix,
                                item.norm,
                                1
                        );

                if (distance <= 1) {
                    ranked.add(
                            new Ranked(
                                    item,
                                    100.0 +
                                    item.order * 0.01
                            )
                    );
                }
            }
        }

        Collections.sort(
                ranked,
                Comparator.comparingDouble(
                        value -> value.score
                )
        );

        LinkedHashSet<String> out =
                new LinkedHashSet<>();

        for (Ranked rankedItem : ranked) {
            out.add(
                    rankedItem.item.word
            );

            if (out.size() >= limit) {
                break;
            }
        }

        return out.toArray(new String[0]);
    }

    public String bestCorrection(
            String typed
    ) {
        if (typed == null ||
                typed.length() < 3) {
            return null;
        }

        String norm =
                LocalDictionary.normalize(
                        typed
                );

        Item best = null;
        int bestDistance = 99;
        int bestOrder = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item.norm.equals(norm)) {
                if (!item.word.equalsIgnoreCase(typed)) {
                    return item.word;
                }

                return null;
            }

            if (Math.abs(
                    item.norm.length() -
                    norm.length()
            ) > 1) {
                continue;
            }

            int distance =
                    damerau(
                            norm,
                            item.norm,
                            1
                    );

            if (distance < bestDistance ||
                    (distance == bestDistance &&
                    item.order < bestOrder)) {

                bestDistance = distance;
                bestOrder = item.order;
                best = item;
            }
        }

        return bestDistance == 1 && best != null
                ? best.word
                : null;
    }

    public boolean isStrongPrefix(
            String typed,
            String candidate
    ) {
        String a =
                LocalDictionary.normalize(
                        typed
                );

        String b =
                LocalDictionary.normalize(
                        candidate
                );

        return a.length() >= 3 &&
                b.startsWith(a);
    }

    private int damerau(
            String a,
            String b,
            int max
    ) {
        int n = a.length();
        int m = b.length();

        if (Math.abs(n - m) > max) {
            return max + 1;
        }

        int[][] d =
                new int[n + 1][m + 1];

        for (int i=0; i<=n; i++) {
            d[i][0] = i;
        }

        for (int j=0; j<=m; j++) {
            d[0][j] = j;
        }

        for (int i=1; i<=n; i++) {
            int rowBest = max + 1;

            for (int j=1; j<=m; j++) {
                int cost =
                        a.charAt(i - 1) ==
                        b.charAt(j - 1)
                                ? 0
                                : 1;

                int value =
                        Math.min(
                                Math.min(
                                        d[i - 1][j] + 1,
                                        d[i][j - 1] + 1
                                ),
                                d[i - 1][j - 1] +
                                        cost
                        );

                if (i > 1 &&
                        j > 1 &&
                        a.charAt(i - 1) ==
                                b.charAt(j - 2) &&
                        a.charAt(i - 2) ==
                                b.charAt(j - 1)) {

                    value =
                            Math.min(
                                    value,
                                    d[i - 2][j - 2] + 1
                            );
                }

                d[i][j] = value;
                rowBest =
                        Math.min(
                                rowBest,
                                value
                        );
            }

            if (rowBest > max) {
                return max + 1;
            }
        }

        return d[n][m];
    }
}
