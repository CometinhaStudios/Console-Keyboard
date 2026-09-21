package com.consolekey.android;

import android.content.Context;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        final Entry entry;
        final double rank;

        Ranked(Entry entry, double rank) {
            this.entry = entry;
            this.rank = rank;
        }
    }

    private final Context context;

    private final LruCache<String, List<Entry>> bucketCache =
            new LruCache<String, List<Entry>>(10);

    private final Map<String, List<Entry>> oneLetterTop =
            new HashMap<>();

    private final char[] alphabet =
            "abcdefghijklmnopqrstuvwxyz".toCharArray();

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

        synchronized (bucketCache) {
            List<Entry> found = bucketCache.get(key);
            if (found != null) return found;
        }

        List<Entry> loaded = new ArrayList<>();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        context.getAssets().open(
                                                "dictionary/ptbr/" +
                                                key +
                                                ".txt"
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

                String word = parts[2].trim();

                // Nunca deixa lixo do CSV/score virar sugestão.
                if (!word.matches("[\\p{L}][\\p{L}'’\\-]{0,47}")) {
                    continue;
                }

                loaded.add(
                        new Entry(
                                parts[0],
                                score,
                                word
                        )
                );
            }
        } catch (Throwable ignored) {}

        synchronized (bucketCache) {
            bucketCache.put(key, loaded);
        }

        return loaded;
    }

    private int lowerBound(
            List<Entry> list,
            String value
    ) {
        int lo = 0;
        int hi = list.size();

        while (lo < hi) {
            int mid = (lo + hi) >>> 1;

            if (list.get(mid).norm.compareTo(value) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        return lo;
    }

    private void insertTop(
            List<Entry> top,
            Entry candidate,
            int limit
    ) {
        int pos = 0;

        while (pos < top.size() &&
                top.get(pos).score <= candidate.score) {
            pos++;
        }

        top.add(pos, candidate);

        if (top.size() > limit) {
            top.remove(top.size() - 1);
        }
    }

    private List<Entry> prefixBase(
            String prefix,
            int limit
    ) {
        List<Entry> list = bucket(prefix);

        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        if (prefix.length() == 1) {
            synchronized (oneLetterTop) {
                List<Entry> cached =
                        oneLetterTop.get(prefix);

                if (cached != null) {
                    return cached;
                }
            }

            List<Entry> top = new ArrayList<>();

            for (Entry e : list) {
                // Evita sugerir abreviações muito curtas logo na 1ª letra.
                if (e.word.length() < 2) continue;
                insertTop(top, e, Math.max(limit, 48));
            }

            synchronized (oneLetterTop) {
                oneLetterTop.put(prefix, top);
            }

            return top;
        }

        int start = lowerBound(list, prefix);
        List<Entry> top = new ArrayList<>();

        for (int i=start; i<list.size(); i++) {
            Entry e = list.get(i);

            if (!e.norm.startsWith(prefix)) {
                break;
            }

            if (e.norm.length() >
                    prefix.length() + 16) {
                continue;
            }

            insertTop(top, e, Math.max(limit, 40));
        }

        return top;
    }

    private List<Entry> exactNormalized(
            String norm
    ) {
        List<Entry> list = bucket(norm);

        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        int start = lowerBound(list, norm);
        List<Entry> out = new ArrayList<>();

        for (int i=start; i<list.size(); i++) {
            Entry e = list.get(i);

            if (!e.norm.equals(norm)) {
                break;
            }

            out.add(e);
        }

        return out;
    }

    public boolean contains(
            String word,
            PersonalLanguageModel personal
    ) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        if (personal != null &&
                personal.wordCount(word) >= 3) {
            return true;
        }

        String norm = normalize(word);

        for (Entry e : exactNormalized(norm)) {
            if (e.word.equalsIgnoreCase(word)) {
                return true;
            }
        }

        return false;
    }

    private double personalRank(
            Entry e,
            PersonalLanguageModel personal
    ) {
        int personalCount =
                personal == null
                        ? 0
                        : personal.wordCount(e.word);

        return e.score -
                Math.log1p(personalCount) * 8.0;
    }

    private List<Entry> rerankPersonal(
            List<Entry> base,
            PersonalLanguageModel personal,
            int limit
    ) {
        List<Ranked> ranked = new ArrayList<>();

        int count = Math.min(base.size(), 30);

        for (int i=0; i<count; i++) {
            Entry e = base.get(i);

            ranked.add(
                    new Ranked(
                            e,
                            personalRank(e, personal)
                    )
            );
        }

        Collections.sort(
                ranked,
                Comparator.comparingDouble(a -> a.rank)
        );

        List<Entry> out = new ArrayList<>();

        for (Ranked r : ranked) {
            out.add(r.entry);

            if (out.size() >= limit) {
                break;
            }
        }

        return out;
    }

    public String[] suggest(
            String typed,
            PersonalLanguageModel personal,
            int limit
    ) {
        if (typed == null ||
                typed.isEmpty() ||
                limit <= 0) {
            return new String[0];
        }

        String prefix = normalize(typed);

        if (prefix.isEmpty()) {
            return new String[0];
        }

        LinkedHashSet<String> out =
                new LinkedHashSet<>();

        boolean exact =
                contains(typed, personal);

        // Se parece erro de digitação, a correção aparece primeiro.
        if (!exact && prefix.length() >= 2) {
            Correction correction =
                    bestAutocorrect(
                            typed,
                            personal
                    );

            if (correction != null) {
                out.add(correction.word);
            }
        }

        // A partir da PRIMEIRA letra já tenta completar a palavra.
        List<Entry> base =
                prefixBase(
                        prefix,
                        40
                );

        for (Entry e : rerankPersonal(
                base,
                personal,
                12
        )) {
            out.add(e.word);

            if (out.size() >= limit) {
                break;
            }
        }

        if (exact && out.size() < limit) {
            out.add(typed);
        }

        // Se o prefixo já está errado, completa com candidatos fuzzy.
        if (out.size() < limit &&
                prefix.length() >= 3) {

            for (String value :
                    fuzzyCorrections(
                            typed,
                            personal,
                            limit
                    )) {

                out.add(value);

                if (out.size() >= limit) {
                    break;
                }
            }
        }

        return out.toArray(new String[0]);
    }

    private void addNormalizedCandidate(
            Set<String> candidateNorms,
            String value
    ) {
        if (value == null ||
                value.length() < 2) {
            return;
        }

        candidateNorms.add(value);
    }

    private String keyboardNeighbors(char c) {
        switch (c) {
            case 'q': return "wa";
            case 'w': return "qeas";
            case 'e': return "wrsd";
            case 'r': return "etdf";
            case 't': return "ryfg";
            case 'y': return "tugh";
            case 'u': return "yihj";
            case 'i': return "uojk";
            case 'o': return "ipkl";
            case 'p': return "ol";
            case 'a': return "qwsz";
            case 's': return "awedxz";
            case 'd': return "serfcx";
            case 'f': return "drtgvc";
            case 'g': return "ftyhbv";
            case 'h': return "gyujnb";
            case 'j': return "huikmn";
            case 'k': return "jiolm";
            case 'l': return "kop";
            case 'z': return "asx";
            case 'x': return "zsdc";
            case 'c': return "xdfv";
            case 'v': return "cfgb";
            case 'b': return "vghn";
            case 'n': return "bhjm";
            case 'm': return "njk";
            default: return "";
        }
    }

    private Set<String> editOneCandidates(
            String word
    ) {
        LinkedHashSet<String> out =
                new LinkedHashSet<>();

        int len = word.length();

        // Remoção
        for (int i=0; i<len; i++) {
            addNormalizedCandidate(
                    out,
                    word.substring(0, i) +
                    word.substring(i + 1)
            );
        }

        // Troca de posição
        for (int i=0; i<len - 1; i++) {
            char[] chars = word.toCharArray();
            char tmp = chars[i];
            chars[i] = chars[i + 1];
            chars[i + 1] = tmp;

            addNormalizedCandidate(
                    out,
                    new String(chars)
            );
        }

        // Substituição:
        // primeira letra usa vizinhos do teclado pra evitar carregar 26
        // dicionários; demais posições testam todo o alfabeto.
        for (int i=0; i<len; i++) {
            char original = word.charAt(i);

            if (i == 0) {
                String neighbors =
                        keyboardNeighbors(original);

                for (int n=0; n<neighbors.length(); n++) {
                    char repl = neighbors.charAt(n);

                    addNormalizedCandidate(
                            out,
                            repl + word.substring(1)
                    );
                }
            } else {
                for (char repl : alphabet) {
                    if (repl == original) continue;

                    addNormalizedCandidate(
                            out,
                            word.substring(0, i) +
                            repl +
                            word.substring(i + 1)
                    );
                }
            }
        }

        // Inserção: em qualquer posição depois da primeira letra.
        for (int i=1; i<=len; i++) {
            for (char ins : alphabet) {
                addNormalizedCandidate(
                        out,
                        word.substring(0, i) +
                        ins +
                        word.substring(i)
                );
            }
        }

        return out;
    }

    private List<Ranked> fuzzyRanked(
            String typed,
            PersonalLanguageModel personal,
            int limit
    ) {
        String norm = normalize(typed);

        if (norm.length() < 2) {
            return Collections.emptyList();
        }

        List<Ranked> ranked =
                new ArrayList<>();

        // Primeiro: mesma palavra normalizada, só faltando acento/cedilha.
        for (Entry e : exactNormalized(norm)) {
            if (!e.word.equalsIgnoreCase(typed)) {
                ranked.add(
                        new Ranked(
                                e,
                                -5000.0 +
                                personalRank(e, personal)
                        )
                );
            }
        }

        if (norm.length() >= 3) {
            Set<String> candidates =
                    editOneCandidates(norm);

            for (String candidateNorm : candidates) {
                for (Entry e :
                        exactNormalized(candidateNorm)) {

                    ranked.add(
                            new Ranked(
                                    e,
                                    1000.0 +
                                    personalRank(e, personal)
                            )
                    );
                }
            }
        }

        Collections.sort(
                ranked,
                Comparator.comparingDouble(a -> a.rank)
        );

        if (ranked.size() > limit) {
            return new ArrayList<>(
                    ranked.subList(
                            0,
                            limit
                    )
            );
        }

        return ranked;
    }

    public String[] fuzzyCorrections(
            String typed,
            PersonalLanguageModel personal,
            int limit
    ) {
        LinkedHashSet<String> out =
                new LinkedHashSet<>();

        for (Ranked r :
                fuzzyRanked(
                        typed,
                        personal,
                        Math.max(limit * 6, 24)
                )) {

            out.add(r.entry.word);

            if (out.size() >= limit) {
                break;
            }
        }

        return out.toArray(new String[0]);
    }

    public Correction bestAutocorrect(
            String typed,
            PersonalLanguageModel personal
    ) {
        if (typed == null ||
                typed.length() < 2 ||
                contains(typed, personal)) {
            return null;
        }

        List<Ranked> ranked =
                fuzzyRanked(
                        typed,
                        personal,
                        1
                );

        if (ranked.isEmpty()) {
            return null;
        }

        Entry best =
                ranked.get(0).entry;

        boolean accentOnly =
                normalize(best.word)
                        .equals(
                                normalize(typed)
                        );

        return new Correction(
                best.word,
                accentOnly ? 0 : 1,
                accentOnly
        );
    }
}
