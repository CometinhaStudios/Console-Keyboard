package com.consolekey.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsoleImeService extends InputMethodService
        implements InputManagerCompat.Listener,
        BaseKeyboardView.Listener,
        SpellCheckerSession.SpellCheckerSessionListener {

    public static final String PREFS = "keyboard_prefs";
    public static final String PREF_PORTRAIT_HEIGHT = "portrait_height";
    public static final String PREF_LANDSCAPE_HEIGHT = "landscape_height";
    public static final String PREF_LONG_PRESS_DELAY = "long_press_delay";

    private InputManagerCompat inputManager;
    private BaseKeyboardView keyboardView;

    private ControllerDetector.Family activeFamily = ControllerDetector.Family.GENERIC;
    private int activeControllerId = -1;

    private final ExecutorService hapticExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpellCheckerSession spellChecker;
    private final HashMap<Integer, String> spellRequests = new HashMap<>();
    private int spellSequence = 1;

    private String suggestionWord = "";
    private String safeAutocorrect = null;
    private String[] visibleSuggestions = new String[0];
    private boolean correctionEnabled = true;

    private final Runnable suggestionRefresh = this::requestSuggestionsNow;

    @Override public void onCreate() {
        super.onCreate();
        inputManager = new InputManagerCompat(this, this);
        inputManager.register();
        refreshController();
        openSpellChecker();
    }

    private void openSpellChecker() {
        try {
            TextServicesManager tsm = (TextServicesManager)getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
            if (tsm != null) {
                spellChecker = tsm.newSpellCheckerSession(null, new Locale("pt", "BR"), this, true);
            }
        } catch (Throwable ignored) {
            spellChecker = null;
        }
    }

    @Override public void onDestroy() {
        if (inputManager != null) inputManager.unregister();
        mainHandler.removeCallbacks(suggestionRefresh);
        try { if (spellChecker != null) spellChecker.close(); } catch (Throwable ignored) {}
        hapticExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int preferredHeightDp() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getInt(landscape() ? PREF_LANDSCAPE_HEIGHT : PREF_PORTRAIT_HEIGHT, landscape() ? 150 : 235);
    }

    private void applyKeyboardHeight() {
        if (keyboardView != null) keyboardView.setFixedHeightDp(preferredHeightDp());
    }

    private void applySuggestionsToView() {
        if (keyboardView instanceof PortraitKeyboardView) {
            ((PortraitKeyboardView)keyboardView).setSuggestions(visibleSuggestions);
        } else if (keyboardView instanceof ConsoleKeyboardView) {
            ((ConsoleKeyboardView)keyboardView).setSuggestions(visibleSuggestions);
        }
    }

    @Override public View onCreateInputView() {
        if (landscape()) {
            ConsoleKeyboardView v = new ConsoleKeyboardView(this, this);
            v.setControllerFamily(activeFamily);
            keyboardView = v;
        } else {
            keyboardView = new PortraitKeyboardView(this, this);
        }
        applyKeyboardHeight();
        applySuggestionsToView();
        return keyboardView;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        correctionEnabled = canUseCorrection(info);
        clearSuggestions();
        applyKeyboardHeight();
        if (correctionEnabled) scheduleSuggestionRefresh();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setInputView(onCreateInputView());
    }

    private void refreshController() {
        ControllerDetector.Family found = ControllerDetector.Family.GENERIC;
        int foundId = -1;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (ControllerDetector.isGamepad(d)) {
                ControllerDetector.Family f = ControllerDetector.detect(d);
                if (foundId == -1) foundId = id;
                if (f != ControllerDetector.Family.GENERIC) {
                    found = f;
                    foundId = id;
                    break;
                }
            }
        }
        activeFamily = found;
        activeControllerId = foundId;
        if (keyboardView instanceof ConsoleKeyboardView) {
            ((ConsoleKeyboardView)keyboardView).setControllerFamily(found);
        }
    }

    private boolean vibrateGamepad() {
        if (activeControllerId < 0) return false;
        InputDevice d = InputDevice.getDevice(activeControllerId);
        if (!ControllerDetector.isGamepad(d)) return false;
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) v = d.getVibratorManager().getDefaultVibrator();
            else v = d.getVibrator();
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(20, 120));
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void vibratePhone() {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(14, 120));
        } catch (Throwable ignored) {}
    }

    @Override public void onKeyFeedback() {
        try {
            hapticExecutor.execute(() -> { if (!vibrateGamepad()) vibratePhone(); });
        } catch (Throwable ignored) {}
    }

    @Override public void onOpenSettings() {
        requestHideSelf(0);
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        InputDevice d = event.getDevice();
        if (ControllerDetector.isGamepad(d)) {
            activeControllerId = d.getId();
            activeFamily = ControllerDetector.detect(d);
            if (keyboardView instanceof ConsoleKeyboardView) {
                ConsoleKeyboardView v = (ConsoleKeyboardView)keyboardView;
                v.setControllerFamily(activeFamily);
                if (v.handleGamepadKey(keyCode, event)) return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        InputDevice d = event.getDevice();
        if (ControllerDetector.isGamepad(d) && keyboardView instanceof ConsoleKeyboardView) {
            if (((ConsoleKeyboardView)keyboardView).handleGamepadKey(keyCode, event)) return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override public void onDeviceChanged() { refreshController(); }

    private InputConnection ic() { return getCurrentInputConnection(); }

    @Override public void onText(String text) {
        InputConnection c = ic();
        if (c != null) c.commitText(text, 1);
        scheduleSuggestionRefresh();
    }

    @Override public void onReplaceLast(String oldText, String newText) {
        InputConnection c = ic();
        if (c == null || newText == null) return;
        int oldLength = oldText == null ? 0 : oldText.length();
        if (oldLength > 0) c.deleteSurroundingText(oldLength, 0);
        c.commitText(newText, 1);
        scheduleSuggestionRefresh();
    }

    @Override public void onBackspace() {
        InputConnection c = ic();
        if (c != null) c.deleteSurroundingText(1, 0);
        scheduleSuggestionRefresh();
    }

    @Override public void onEnter() {
        InputConnection c = ic();
        if (c != null) {
            c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
        clearSuggestions();
    }

    @Override public void onSpace() {
        InputConnection c = ic();
        if (c == null) return;
        String word = currentWord();
        if (correctionEnabled && word != null && !word.isEmpty() && word.equalsIgnoreCase(suggestionWord) &&
                safeAutocorrect != null && !safeAutocorrect.equalsIgnoreCase(word)) {
            replaceCurrentWord(matchCase(word, safeAutocorrect));
        }
        c.commitText(" ", 1);
        clearSuggestions();
    }

    @Override public void onSuggestionSelected(String suggestion) {
        if (suggestion == null || suggestion.isEmpty()) return;
        String word = currentWord();
        if (word != null && !word.isEmpty() && !suggestion.equalsIgnoreCase(word)) {
            replaceCurrentWord(matchCase(word, suggestion));
        }
        scheduleSuggestionRefresh();
    }

    @Override public void onHide() { requestHideSelf(0); }

    private boolean canUseCorrection(EditorInfo info) {
        if (info == null) return true;
        if ((info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
                variation != InputType.TYPE_TEXT_VARIATION_URI;
    }

    private void scheduleSuggestionRefresh() {
        mainHandler.removeCallbacks(suggestionRefresh);
        if (!correctionEnabled) { clearSuggestions(); return; }
        mainHandler.postDelayed(suggestionRefresh, 24);
    }

    private String currentWord() {
        InputConnection c = ic();
        if (c == null) return "";
        CharSequence before = c.getTextBeforeCursor(64, 0);
        if (before == null || before.length() == 0) return "";
        int end = before.length();
        int start = end;
        while (start > 0) {
            char ch = before.charAt(start - 1);
            if (Character.isLetter(ch) || ch == '\'' || ch == '’') start--;
            else break;
        }
        if (start == end) return "";
        return before.subSequence(start, end).toString();
    }

    private void requestSuggestionsNow() {
        if (!correctionEnabled) { clearSuggestions(); return; }
        String word = currentWord();
        if (word == null || word.length() < 2) { clearSuggestions(); return; }

        if (spellChecker == null) {
            visibleSuggestions = localFallbackSuggestions(word);
            suggestionWord = word;
            safeAutocorrect = localSafeAutocorrect(word);
            applySuggestionsToView();
            return;
        }

        int sequence = spellSequence++;
        if (spellRequests.size() > 64) spellRequests.clear();
        spellRequests.put(sequence, word);
        try {
            spellChecker.getSuggestions(new TextInfo[]{ new TextInfo(word, 0x434B, sequence) }, 5, false);
        } catch (Throwable ignored) {
            visibleSuggestions = localFallbackSuggestions(word);
            suggestionWord = word;
            safeAutocorrect = localSafeAutocorrect(word);
            applySuggestionsToView();
        }
    }

    @Override public void onGetSuggestions(SuggestionsInfo[] results) {
        if (results == null || results.length == 0) return;
        SuggestionsInfo info = results[0];
        String requested = spellRequests.remove(info.getSequence());
        if (requested == null) return;
        String now = currentWord();
        if (!requested.equalsIgnoreCase(now)) return;

        int attrs = info.getSuggestionsAttributes();
        boolean inDictionary = (attrs & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0;
        boolean looksTypo = (attrs & SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0;
        boolean recommended = (attrs & SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS) != 0;

        ArrayList<String> display = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addSuggestion(display, seen, requested);

        String firstCorrection = null;
        for (int i=0; i<info.getSuggestionsCount() && display.size()<3; i++) {
            String candidate = info.getSuggestionAt(i);
            if (candidate == null || candidate.isEmpty()) continue;
            candidate = matchCase(requested, candidate);
            if (candidate.equalsIgnoreCase(requested)) continue;
            if (firstCorrection == null) firstCorrection = candidate;
            addSuggestion(display, seen, candidate);
        }

        for (String value : localFallbackSuggestions(requested)) {
            if (display.size() >= 3) break;
            addSuggestion(display, seen, value);
        }

        suggestionWord = requested;
        visibleSuggestions = display.toArray(new String[0]);
        safeAutocorrect = null;

        if (!inDictionary && looksTypo && firstCorrection != null &&
                isSafeAutocorrect(requested, firstCorrection, recommended)) {
            safeAutocorrect = firstCorrection;
        }
        if (safeAutocorrect == null) safeAutocorrect = localSafeAutocorrect(requested);
        applySuggestionsToView();
    }

    @Override public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {}

    private void addSuggestion(ArrayList<String> list, Set<String> seen, String value) {
        if (value == null || value.isEmpty()) return;
        String key = value.toLowerCase(new Locale("pt", "BR"));
        if (seen.add(key)) list.add(value);
    }

    private boolean isSafeAutocorrect(String original, String candidate, boolean recommended) {
        if (original == null || candidate == null || original.length() < 3) return false;
        String a = original.toLowerCase(new Locale("pt", "BR"));
        String b = candidate.toLowerCase(new Locale("pt", "BR"));
        if (stripAccents(a).equals(stripAccents(b))) return true;
        int distance = editDistance(a, b);
        if (recommended && distance <= 2) return true;
        return distance <= 1 && Math.max(a.length(), b.length()) >= 4;
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j=0; j<=b.length(); j++) prev[j] = j;
        for (int i=1; i<=a.length(); i++) {
            cur[0] = i;
            for (int j=1; j<=b.length(); j++) {
                int cost = a.charAt(i-1) == b.charAt(j-1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j-1] + 1, prev[j] + 1), prev[j-1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    private String matchCase(String original, String candidate) {
        if (candidate == null || candidate.isEmpty()) return candidate;
        if (original == null || original.isEmpty()) return candidate;
        Locale pt = new Locale("pt", "BR");
        if (original.equals(original.toUpperCase(pt))) return candidate.toUpperCase(pt);
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
        }
        return candidate;
    }

    private void replaceCurrentWord(String replacement) {
        if (replacement == null || replacement.isEmpty()) return;
        InputConnection c = ic();
        if (c == null) return;
        String word = currentWord();
        if (word == null || word.isEmpty()) return;
        c.deleteSurroundingText(word.length(), 0);
        c.commitText(replacement, 1);
    }

    private void clearSuggestions() {
        suggestionWord = "";
        safeAutocorrect = null;
        visibleSuggestions = new String[0];
        applySuggestionsToView();
    }

    private String[] localFallbackSuggestions(String word) {
        String corrected = localSafeAutocorrect(word);
        if (corrected == null || corrected.equalsIgnoreCase(word)) return new String[]{ word };
        return new String[]{ word, matchCase(word, corrected) };
    }

    private String localSafeAutocorrect(String word) {
        if (word == null) return null;
        switch (word.toLowerCase(new Locale("pt", "BR"))) {
            case "nao": return "não";
            case "voce": return "você";
            case "voces": return "vocês";
            case "tambem": return "também";
            case "ninguem": return "ninguém";
            case "alguem": return "alguém";
            case "facil": return "fácil";
            case "dificil": return "difícil";
            case "possivel": return "possível";
            case "impossivel": return "impossível";
            case "portugues": return "português";
            case "ingles": return "inglês";
            case "coracao": return "coração";
            case "informacao": return "informação";
            case "configuracao": return "configuração";
            case "aplicacao": return "aplicação";
            case "funcao": return "função";
            case "opcao": return "opção";
            default: return null;
        }
    }
}
