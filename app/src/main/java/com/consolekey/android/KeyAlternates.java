package com.consolekey.android;

import java.util.Locale;

public final class KeyAlternates {
    private KeyAlternates() {}

    public static String[] forKey(String base, boolean upper) {
        if (base == null || base.isEmpty()) return new String[0];

        String lower = base.toLowerCase(Locale.ROOT);
        String[] values;

        switch (lower) {
            case "a": values = new String[]{"á","à","â","ã","ä"}; break;
            case "e": values = new String[]{"é","è","ê","ë"}; break;
            case "i": values = new String[]{"í","ì","î","ï"}; break;
            case "o": values = new String[]{"ó","ò","ô","õ","ö"}; break;
            case "u": values = new String[]{"ú","ù","û","ü"}; break;
            case "c": values = new String[]{"ç","ć","č"}; break;
            case "n": values = new String[]{"ñ","ń"}; break;
            case "y": values = new String[]{"ý","ÿ"}; break;
            case "s": values = new String[]{"š","ś"}; break;
            case "z": values = new String[]{"ž","ź","ż"}; break;

            case "1": values = new String[]{"!","¹"}; break;
            case "2": values = new String[]{"@","²"}; break;
            case "3": values = new String[]{"#","³"}; break;
            case "4": values = new String[]{"$","€","£"}; break;
            case "5": values = new String[]{"%","‰"}; break;
            case "6": values = new String[]{"^"}; break;
            case "7": values = new String[]{"&"}; break;
            case "8": values = new String[]{"*"}; break;
            case "9": values = new String[]{"("}; break;
            case "0": values = new String[]{")","°"}; break;

            case ".": values = new String[]{"…","?","!",":",";"}; break;
            case ",": values = new String[]{";","<"}; break;
            case "?": values = new String[]{"¿"}; break;
            case "!": values = new String[]{"¡"}; break;
            case "-": values = new String[]{"—","–","_"}; break;
            case "'": values = new String[]{"’","‘","`"}; break;
            case "\"": values = new String[]{"“","”","«","»"}; break;
            default: values = new String[0];
        }

        if (!upper) return values;

        String[] upperValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            upperValues[i] = values[i].toUpperCase(Locale.ROOT);
        }
        return upperValues;
    }
}
