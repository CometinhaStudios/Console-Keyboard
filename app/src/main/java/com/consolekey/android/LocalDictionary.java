package com.consolekey.android;

import android.content.Context;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LocalDictionary {
    public static class Correction {
        public final String word;
        public final int distance;
        public final boolean accentOnly;

        Correction(String word, int distance, boolean accentOnly) {
            this.word = word;
            this.distance = distance;
            this.accentOnly = accentOnly;
        }
    }

    private static class Entry {
        final String norm;
        final double score;
        final String word;

        Entry(String norm, double score, String word) {
            this.norm = norm;
            this.score = score;
            this.word = word;
        }
    }

    private static class Ranked {
        final String word;
        final double rank;

        Ranked(String word, double rank) {
            this.word = word;
            this.rank = rank;
        }
    }

    private final Context context;

    private final LruCache<String, List<Entry>> cache =
            new LruCache<String, List<Entry>>(8);

    public LocalDictionary(Context context) {
        this.context = context.getApplicationContext();
    }

    public static String normalize(String value) {
        if (value == null) return "";

        String lower =
                value.toLowerCase(new Locale("pt", "BR"))
                        .replace('’', '\'');

        String n =
                Normalizer.normalize(
                        lower,
                        Normalizer.Form.NFD
                );

        StringBuilder out =
                new StringBuilder(n.length());

        for (int i=0; i<n.length(); i++) {
            char ch = n.charAt(i);

            if (Character.getType(ch) ==
                    Character.NON_SPACING_MARK) {
                continue;
            }

            if ((ch >= 'a' && ch <= 'z') ||
                    ch == '\'' ||
                    ch == '-') {
                out.append(ch);
            }
        }

        return out.toString();
    }

    private List<Entry> bucket(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Collections.emptyList();
        }

        char first = normalized.charAt(0);
        if (first < 'a' || first > 'z') {
            return Collections.emptyList();
        }

        String key = String.valueOf(first);

        synchronized (cache) {
            List<Entry> found = cache.get(key);
            if (found != null) return found;
        }

        List<Entry> loaded = new ArrayList<>();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        context.getAssets().open(
                                                "dictionary/ptbr/" + key + ".txt"
                                        ),
                                        "UTF-8"
                                )
                        )
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\t", 3);
                if (parts.length < 3) continue;

                double score;
                try {
                    score = Double.parseDouble(parts[1]);
                } catch (Throwable ignored) {
                    score = 50.0;
                }

                loaded.add(
                        new Entry(
                                parts[0],
                                score,
                                parts[2]
                        )
                );
            }
        } catch (Throwable ignored) {}

        synchronized (cache) {
            cache.put(key, loaded);
        }

        return loaded;
    }

    private int lowerBound(List<Entry> list, String prefix) {
        int lo = 0;
        int hi = list.size();

        while (lo < hi) {
            int mid = (lo + hi) >>> 1;

            if (list.get(mid).norm.compareTo(prefix) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        return lo;
    }

    public boolean contains(String word, PersonalLanguageModel personal) {
        if (word == null || word.isEmpty()) return false;

        if (personal != null && personal.wordCount(word) >= 3) {
            return true;
        }

        String norm = normalize(word);
        List<Entry> list = bucket(norm);
        if (list.isEmpty()) return false;

        int start = lowerBound(list, norm);

        for (int i=start; i<list.size(); i++) {
            Entry e = list.get(i);

            if (!e.norm.equals(norm)) break;

            if (e.word.equalsIgnoreCase(word)) {
                return true;
            }
        }

        return false;
    }

    public String[] suggest(
            String typed,
            PersonalLanguageModel personal,
            int limit
    ) {
        if (typed == null || typed.length() < 2 || limit <= 0) {
            return new String[0];
        }

        String prefix = normalize(typed);
        if (prefix.length() < 2) return new String[0];

        List<Entry> list = bucket(prefix);
        if (list.isEmpty()) return new String[0];

        List<Ranked> ranked = new ArrayList<>();
        int start = lowerBound(list, prefix);

        for (int i=start; i<list.size(); i++) {
            Entry e = list.get(i);

            if (!e.norm.startsWith(prefix)) break;
            if (e.norm.length() > prefix.length() + 12) continue;

            int personalCount =
                    personal == null ? 0 : personal.wordCount(e.word);

            double rank =
                    e.score -
                    Math.log1p(personalCount) * 3.0;

            ranked.add(new Ranked(e.word, rank));

            if (ranked.size() >= 400) break;
        }

        Collections.sort(
                ranked,
                Comparator.comparingDouble(a -> a.rank)
        );

        Set<String> unique = new LinkedHashSet<>();

        if (contains(typed, personal)) {
            unique.add(typed);
        }

        for (Ranked r : ranked) {
            unique.add(r.word);
            if (unique.size() >= limit) break;
        }

        if (unique.size() < limit) {
            for (String c : correctionSuggestions(
                    typed,
                    personal,
                    limit
            )) {
                unique.add(c);
                if (unique.size() >= limit) break;
            }
        }

        return unique.toArray(new String[0]);
    }

    public String[] correctionSuggestions(
            String typed,
            PersonalLanguageModel personal,
            int limit
    ) {
        if (typed == null || typed.length() < 2) {
            return new String[0];
        }

        String norm = normalize(typed);
        if (norm.isEmpty()) return new String[0];

        List<Entry> list = bucket(norm);
        if (list.isEmpty()) return new String[0];

        List<Ranked> ranked = new ArrayList<>();

        int maxDistance =
                norm.length() >= 6 ? 2 : 1;

        for (Entry e : list) {
            if (Math.abs(e.norm.length() - norm.length()) > maxDistance) {
                continue;
            }

            int distance =
                    damerauDistance(
                            norm,
                            e.norm,
                            maxDistance
                    );

            if (distance > maxDistance) continue;

            int personalCount =
                    personal == null ? 0 : personal.wordCount(e.word);

            double rank =
                    distance * 1000.0 +
                    e.score -
                    Math.log1p(personalCount) * 10.0;

            ranked.add(
                    new Ranked(
                            e.word,
                            rank
                    )
            );
        }

        Collections.sort(
                ranked,
                Comparator.comparingDouble(a -> a.rank)
        );

        Set<String> unique = new LinkedHashSet<>();

        for (Ranked r : ranked) {
            unique.add(r.word);
            if (unique.size() >= limit) break;
        }

        return unique.toArray(new String[0]);
    }

    public Correction bestAutocorrect(
            String typed,
            PersonalLanguageModel personal
    ) {
        if (typed == null || typed.length() < 2) return null;

        if (contains(typed, personal)) return null;

        String norm = normalize(typed);
        List<Entry> list = bucket(norm);
        if (list.isEmpty()) return null;

        Entry best = null;
        int bestDistance = 99;
        double bestRank = Double.MAX_VALUE;
        boolean accentOnly = false;

        for (Entry e : list) {
            if (Math.abs(e.norm.length() - norm.length()) > 1) {
                continue;
            }

            int distance =
                    damerauDistance(
                            norm,
                            e.norm,
                            1
                    );

            if (distance > 1) continue;

            boolean sameNormalized =
                    distance == 0;

            int personalCount =
                    personal == null ? 0 : personal.wordCount(e.word);

            double rank =
                    distance * 1000.0 +
                    e.score -
                    Math.log1p(personalCount) * 10.0;

            if (sameNormalized) rank -= 5000.0;

            if (rank < bestRank) {
                best = e;
                bestRank = rank;
                bestDistance = distance;
                accentOnly = sameNormalized;
            }
        }

        if (best == null) return null;

        if (!accentOnly && bestDistance != 1) {
            return null;
        }

        return new Correction(
                best.word,
                bestDistance,
                accentOnly
        );
    }

    private int damerauDistance(
            String a,
            String b,
            int max
    ) {
        int n = a.length();
        int m = b.length();

        if (Math.abs(n - m) > max) {
            return max + 1;
        }

        int[][] d = new int[n + 1][m + 1];

        for (int i=0; i<=n; i++) d[i][0] = i;
        for (int j=0; j<=m; j++) d[0][j] = j;

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
                                d[i - 1][j - 1] + cost
                        );

                if (i > 1 &&
                        j > 1 &&
                        a.charAt(i - 1) == b.charAt(j - 2) &&
                        a.charAt(i - 2) == b.charAt(j - 1)) {

                    value =
                            Math.min(
                                    value,
                                    d[i - 2][j - 2] + 1
                            );
                }

                d[i][j] = value;
                rowBest = Math.min(rowBest, value);
            }

            if (rowBest > max) {
                return max + 1;
            }
        }

        return d[n][m];
    }
}
