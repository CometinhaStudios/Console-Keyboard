package com.consolekey.android;

import android.content.ClipData;
import android.content.ClipboardManager;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsoleImeService extends InputMethodService
        implements InputManagerCompat.Listener,
        BaseKeyboardView.Listener {

    public static final String PREFS =
            "keyboard_prefs";

    public static final String PREF_PORTRAIT_HEIGHT =
            "portrait_height";

    public static final String PREF_LANDSCAPE_HEIGHT =
            "landscape_height";

    public static final String PREF_LONG_PRESS_DELAY =
            "long_press_delay";

    private InputManagerCompat inputManager;
    private BaseKeyboardView keyboardView;

    private ControllerDetector.Family activeFamily =
            ControllerDetector.Family.GENERIC;

    private int activeControllerId = -1;

    private boolean numericInput = false;
    private boolean correctionEnabled = false;

    private final ExecutorService hapticExecutor =
            Executors.newSingleThreadExecutor();

    private final ExecutorService languageExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final AtomicInteger suggestionGeneration =
            new AtomicInteger();

    private LocalDictionary dictionary;
    private PersonalLanguageModel personal;
    private ProperNameLexicon properNames;

    private ClipboardStore clipboardStore;
    private ClipboardManager clipboardManager;
    private ClipboardPanelView clipboardPanelView;
    private boolean sensitiveInput = false;

    private final ClipboardManager.OnPrimaryClipChangedListener clipboardListener =
            this::captureClipboard;

    private String pendingWordNorm = "";
    private LocalDictionary.Correction pendingCorrection;

    private final Runnable suggestionRefresh =
            this::requestSuggestionsNow;

    @Override public void onCreate() {
        super.onCreate();

        inputManager =
                new InputManagerCompat(
                        this,
                        this
                );

        inputManager.register();

        dictionary =
                new LocalDictionary(this);

        personal =
                new PersonalLanguageModel(this);

        properNames =
                new ProperNameLexicon(this);

        clipboardStore =
                new ClipboardStore(this);

        clipboardManager =
                (ClipboardManager)getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        if (clipboardManager != null) {
            try {
                clipboardManager.addPrimaryClipChangedListener(
                        clipboardListener
                );
            } catch (Throwable ignored) {}
        }

        captureClipboard();
        refreshController();
    }

    @Override public void onDestroy() {
        if (inputManager != null) {
            inputManager.unregister();
        }

        mainHandler.removeCallbacks(
                suggestionRefresh
        );

        if (clipboardManager != null) {
            try {
                clipboardManager.removePrimaryClipChangedListener(
                        clipboardListener
                );
            } catch (Throwable ignored) {}
        }

        languageExecutor.shutdownNow();
        hapticExecutor.shutdownNow();

        try {
            if (personal != null) {
                personal.close();
            }
        } catch (Throwable ignored) {}

        super.onDestroy();
    }

    @Override public boolean onEvaluateFullscreenMode() {
        return false;
    }

    private boolean landscape() {
        return getResources()
                .getConfiguration()
                .orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
    }

    private int preferredHeightDp() {
        SharedPreferences p =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        return p.getInt(
                landscape()
                        ? PREF_LANDSCAPE_HEIGHT
                        : PREF_PORTRAIT_HEIGHT,
                landscape() ? 150 : 235
        );
    }

    private void applyKeyboardHeight() {
        if (keyboardView != null) {
            keyboardView.setFixedHeightDp(
                    preferredHeightDp()
            );
        }
    }

    private void setSuggestions(
            String[] values
    ) {
        if (keyboardView instanceof PortraitKeyboardView) {
            ((PortraitKeyboardView)keyboardView)
                    .setSuggestions(values);
        } else if (keyboardView instanceof ConsoleKeyboardView) {
            ((ConsoleKeyboardView)keyboardView)
                    .setSuggestions(values);
        }
    }

    @Override public View onCreateInputView() {
        if (landscape()) {
            ConsoleKeyboardView v =
                    new ConsoleKeyboardView(
                            this,
                            this,
                            numericInput
                    );

            v.setControllerFamily(
                    activeFamily
            );

            keyboardView = v;
        } else {
            keyboardView =
                    new PortraitKeyboardView(
                            this,
                            this,
                            numericInput
                    );
        }

        applyKeyboardHeight();
        return keyboardView;
    }

    @Override public void onStartInputView(
            EditorInfo info,
            boolean restarting
    ) {
        super.onStartInputView(
                info,
                restarting
        );

        int inputClass =
                info == null
                        ? 0
                        : info.inputType &
                        InputType.TYPE_MASK_CLASS;

        numericInput =
                inputClass ==
                        InputType.TYPE_CLASS_NUMBER ||
                inputClass ==
                        InputType.TYPE_CLASS_PHONE;

        correctionEnabled =
                canUseCorrection(info);

        sensitiveInput =
                isSensitiveInput(info);

        clipboardPanelView = null;

        pendingWordNorm = "";
        pendingCorrection = null;

        suggestionGeneration.incrementAndGet();

        setInputView(
                onCreateInputView()
        );

        if (correctionEnabled) {
            scheduleSuggestionRefresh();
        } else {
            setSuggestions(
                    new String[0]
            );
        }
    }

    @Override public void onFinishInput() {
        suggestionGeneration.incrementAndGet();

        mainHandler.removeCallbacks(
                suggestionRefresh
        );

        pendingWordNorm = "";
        pendingCorrection = null;

        super.onFinishInput();
    }

    @Override public void onConfigurationChanged(
            Configuration newConfig
    ) {
        super.onConfigurationChanged(
                newConfig
        );

        setInputView(
                onCreateInputView()
        );
    }

    private boolean isSensitiveInput(
            EditorInfo info
    ) {
        if (info == null) {
            return false;
        }

        if ((info.inputType &
                InputType.TYPE_MASK_CLASS) !=
                InputType.TYPE_CLASS_TEXT) {
            return false;
        }

        int variation =
                info.inputType &
                InputType.TYPE_MASK_VARIATION;

        return variation ==
                InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation ==
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation ==
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private boolean canUseCorrection(
            EditorInfo info
    ) {
        if (info == null) {
            return true;
        }

        if ((info.inputType &
                InputType.TYPE_MASK_CLASS) !=
                InputType.TYPE_CLASS_TEXT) {

            return false;
        }

        int variation =
                info.inputType &
                InputType.TYPE_MASK_VARIATION;

        return variation !=
                InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                variation !=
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                variation !=
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
                variation !=
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
                variation !=
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
                variation !=
                InputType.TYPE_TEXT_VARIATION_URI;
    }

    private void refreshController() {
        ControllerDetector.Family found =
                ControllerDetector.Family.GENERIC;

        int foundId = -1;

        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d =
                    InputDevice.getDevice(id);

            if (ControllerDetector.isGamepad(d)) {
                ControllerDetector.Family f =
                        ControllerDetector.detect(d);

                if (foundId == -1) {
                    foundId = id;
                }

                if (f !=
                        ControllerDetector.Family.GENERIC) {

                    found = f;
                    foundId = id;
                    break;
                }
            }
        }

        activeFamily = found;
        activeControllerId = foundId;

        if (keyboardView instanceof ConsoleKeyboardView) {
            ((ConsoleKeyboardView)keyboardView)
                    .setControllerFamily(found);
        }
    }

    private boolean vibrateGamepad() {
        if (activeControllerId < 0) {
            return false;
        }

        InputDevice d =
                InputDevice.getDevice(
                        activeControllerId
                );

        if (!ControllerDetector.isGamepad(d)) {
            return false;
        }

        try {
            Vibrator v;

            if (Build.VERSION.SDK_INT >= 31) {
                v = d.getVibratorManager()
                        .getDefaultVibrator();
            } else {
                v = d.getVibrator();
            }

            if (v != null &&
                    v.hasVibrator()) {

                v.vibrate(
                        VibrationEffect.createOneShot(
                                20,
                                120
                        )
                );

                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private void vibratePhone() {
        try {
            Vibrator v;

            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm =
                        (VibratorManager)getSystemService(
                                Context.VIBRATOR_MANAGER_SERVICE
                        );

                v = vm == null
                        ? null
                        : vm.getDefaultVibrator();
            } else {
                v = (Vibrator)getSystemService(
                        Context.VIBRATOR_SERVICE
                );
            }

            if (v != null &&
                    v.hasVibrator()) {

                v.vibrate(
                        VibrationEffect.createOneShot(
                                14,
                                120
                        )
                );
            }
        } catch (Throwable ignored) {}
    }

    @Override public void onKeyFeedback() {
        try {
            hapticExecutor.execute(
                    () -> {
                        if (!vibrateGamepad()) {
                            vibratePhone();
                        }
                    }
            );
        } catch (Throwable ignored) {}
    }

    @Override public void onOpenSettings() {
        requestHideSelf(0);

        Intent i =
                new Intent(
                        this,
                        MainActivity.class
                );

        i.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
        );

        startActivity(i);
    }

    @Override public boolean onKeyDown(
            int keyCode,
            KeyEvent event
    ) {
        InputDevice d =
                event.getDevice();

        if (clipboardPanelView != null &&
                clipboardPanelView.handleGamepadKey(
                        keyCode,
                        event
                )) {
            return true;
        }

        if (ControllerDetector.isGamepad(d)) {
            activeControllerId =
                    d.getId();

            activeFamily =
                    ControllerDetector.detect(d);

            if (keyboardView instanceof ConsoleKeyboardView) {
                ConsoleKeyboardView v =
                        (ConsoleKeyboardView)keyboardView;

                v.setControllerFamily(
                        activeFamily
                );

                if (v.handleGamepadKey(
                        keyCode,
                        event
                )) {
                    return true;
                }
            }
        }

        return super.onKeyDown(
                keyCode,
                event
        );
    }

    @Override public boolean onKeyUp(
            int keyCode,
            KeyEvent event
    ) {
        InputDevice d =
                event.getDevice();

        if (clipboardPanelView != null &&
                clipboardPanelView.handleGamepadKey(
                        keyCode,
                        event
                )) {
            return true;
        }

        if (ControllerDetector.isGamepad(d) &&
                keyboardView instanceof ConsoleKeyboardView) {

            if (((ConsoleKeyboardView)keyboardView)
                    .handleGamepadKey(
                            keyCode,
                            event
                    )) {

                return true;
            }
        }

        return super.onKeyUp(
                keyCode,
                event
        );
    }

    @Override public void onDeviceChanged() {
        refreshController();
    }

    private InputConnection ic() {
        return getCurrentInputConnection();
    }

    private String beforeCursor(
            int amount
    ) {
        InputConnection c = ic();

        if (c == null) {
            return "";
        }

        CharSequence value =
                c.getTextBeforeCursor(
                        amount,
                        0
                );

        return value == null
                ? ""
                : value.toString();
    }

    private boolean isWordChar(char ch) {
        return Character.isLetter(ch) ||
                ch == '\'' ||
                ch == '’' ||
                ch == '-';
    }

    private String currentWord(
            String before
    ) {
        if (before == null ||
                before.isEmpty()) {

            return "";
        }

        int end = before.length();
        int start = end;

        while (start > 0 &&
                isWordChar(
                        before.charAt(
                                start - 1
                        )
                )) {

            start--;
        }

        if (start == end) {
            return "";
        }

        return before.substring(
                start,
                end
        );
    }

    private List<String> words(
            String text
    ) {
        List<String> out =
                new ArrayList<>();

        if (text == null ||
                text.isEmpty()) {

            return out;
        }

        StringBuilder current =
                new StringBuilder();

        for (int i=0; i<text.length(); i++) {
            char ch = text.charAt(i);

            if (isWordChar(ch)) {
                current.append(ch);
            } else if (current.length() > 0) {
                out.add(
                        current.toString()
                );

                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            out.add(
                    current.toString()
            );
        }

        return out;
    }

    private String[] previousWordsBeforeCurrent(
            String before,
            String current
    ) {
        String prefix = before;

        if (current != null &&
                !current.isEmpty() &&
                before.endsWith(current)) {

            prefix =
                    before.substring(
                            0,
                            before.length() -
                                    current.length()
                    );
        }

        List<String> all =
                words(prefix);

        String prev1 =
                all.size() >= 1
                        ? all.get(
                                all.size() - 1
                        )
                        : "";

        String prev2 =
                all.size() >= 2
                        ? all.get(
                                all.size() - 2
                        )
                        : "";

        return new String[]{
                prev2,
                prev1
        };
    }

    private String[] mergeSuggestionSources(
            String current,
            String[] dictionaryValues,
            String[] properValues,
            int limit
    ) {
        LinkedHashSet<String> out =
                new LinkedHashSet<>();

        boolean strongProper =
                properValues != null &&
                properValues.length > 0 &&
                properNames != null &&
                properNames.isStrongPrefix(
                        current,
                        properValues[0]
                );

        if (strongProper) {
            out.add(properValues[0]);
        }

        if (dictionaryValues != null) {
            for (String value : dictionaryValues) {
                if (value == null ||
                        value.isEmpty()) {
                    continue;
                }

                out.add(value);

                if (out.size() >= limit) {
                    break;
                }
            }
        }

        if (properValues != null) {
            for (String value : properValues) {
                if (value == null ||
                        value.isEmpty()) {
                    continue;
                }

                out.add(value);

                if (out.size() >= limit) {
                    break;
                }
            }
        }

        List<String> result =
                new ArrayList<>(out);

        if (result.size() > limit) {
            result =
                    result.subList(
                            0,
                            limit
                    );
        }

        return result.toArray(
                new String[0]
        );
    }

    private String matchCase(
            String source,
            String candidate
    ) {
        if (candidate == null ||
                candidate.isEmpty() ||
                source == null ||
                source.isEmpty()) {

            return candidate;
        }

        Locale pt =
                new Locale(
                        "pt",
                        "BR"
                );

        if (source.equals(
                source.toUpperCase(pt))) {

            return candidate.toUpperCase(pt);
        }

        if (Character.isUpperCase(
                source.charAt(0))) {

            return Character.toUpperCase(
                    candidate.charAt(0)
            ) +
                    candidate.substring(1);
        }

        return candidate;
    }

    private void replaceCurrentWord(
            String oldWord,
            String newWord
    ) {
        InputConnection c = ic();

        if (c == null ||
                oldWord == null ||
                oldWord.isEmpty() ||
                newWord == null ||
                newWord.isEmpty()) {

            return;
        }

        c.deleteSurroundingText(
                oldWord.length(),
                0
        );

        c.commitText(
                newWord,
                1
        );
    }

    private boolean replacePreviousWordAfterSpace(
            String oldWord,
            String newWord
    ) {
        InputConnection c = ic();

        if (c == null ||
                oldWord == null ||
                newWord == null) {

            return false;
        }

        String tail =
                beforeCursor(
                        oldWord.length() +
                                2
                );

        String expected =
                oldWord + " ";

        if (!tail.endsWith(expected)) {
            return false;
        }

        c.deleteSurroundingText(
                expected.length(),
                0
        );

        c.commitText(
                newWord + " ",
                1
        );

        return true;
    }

    @Override public void onText(
            String text
    ) {
        InputConnection c = ic();

        if (c != null) {
            c.commitText(
                    text,
                    1
            );
        }

        if (correctionEnabled) {
            scheduleSuggestionRefresh();
        }
    }

    @Override public void onReplaceLast(
            String oldText,
            String newText
    ) {
        InputConnection c = ic();

        if (c == null ||
                newText == null) {

            return;
        }

        int length =
                oldText == null
                        ? 0
                        : oldText.length();

        if (length > 0) {
            c.deleteSurroundingText(
                    length,
                    0
            );
        }

        c.commitText(
                newText,
                1
        );

        if (correctionEnabled) {
            scheduleSuggestionRefresh();
        }
    }

    @Override public void onBackspace() {
        InputConnection c = ic();

        if (c != null) {
            c.deleteSurroundingText(
                    1,
                    0
            );
        }

        if (correctionEnabled) {
            scheduleSuggestionRefresh();
        }
    }

    @Override public void onEnter() {
        String before =
                beforeCursor(160);

        String current =
                currentWord(before);

        if (correctionEnabled &&
                !current.isEmpty()) {

            String[] context =
                    previousWordsBeforeCurrent(
                            before,
                            current
                    );

            languageExecutor.execute(
                    () -> personal.learn(
                            context[0],
                            context[1],
                            current
                    )
            );
        }

        InputConnection c = ic();

        if (c != null) {
            c.sendKeyEvent(
                    new KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ENTER
                    )
            );

            c.sendKeyEvent(
                    new KeyEvent(
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_ENTER
                    )
            );
        }

        setSuggestions(
                new String[0]
        );
    }

    @Override public void onSpace() {
        InputConnection c = ic();

        if (c == null) {
            return;
        }

        if (!correctionEnabled) {
            c.commitText(
                    " ",
                    1
            );

            return;
        }

        String before =
                beforeCursor(160);

        String current =
                currentWord(before);

        String[] context =
                previousWordsBeforeCurrent(
                        before,
                        current
                );

        c.commitText(
                " ",
                1
        );

        if (current.isEmpty()) {
            scheduleSuggestionRefresh();
            return;
        }

        String currentNorm =
                LocalDictionary.normalize(
                        current
                );

        LocalDictionary.Correction ready =
                currentNorm.equals(
                        pendingWordNorm
                )
                        ? pendingCorrection
                        : null;

        if (ready != null) {
            String corrected =
                    matchCase(
                            current,
                            ready.word
                    );

            replacePreviousWordAfterSpace(
                    current,
                    corrected
            );

            languageExecutor.execute(
                    () -> personal.learn(
                            context[0],
                            context[1],
                            corrected
                    )
            );

            pendingWordNorm = "";
            pendingCorrection = null;

            scheduleSuggestionRefresh();
            return;
        }

        final int generation =
                suggestionGeneration.incrementAndGet();

        languageExecutor.execute(
                () -> {
                    LocalDictionary.Correction correction =
                            dictionary.bestAutocorrect(
                                    current,
                                    personal
                            );

                    String learned =
                            correction == null
                                    ? current
                                    : matchCase(
                                            current,
                                            correction.word
                                    );

                    personal.learn(
                            context[0],
                            context[1],
                            learned
                    );

                    mainHandler.post(
                            () -> {
                                if (generation !=
                                        suggestionGeneration.get()) {

                                    return;
                                }

                                if (correction != null) {
                                    replacePreviousWordAfterSpace(
                                            current,
                                            learned
                                    );
                                }

                                scheduleSuggestionRefresh();
                            }
                    );
                }
        );
    }

    @Override public void onSuggestionSelected(
            String suggestion
    ) {
        if (suggestion == null ||
                suggestion.isEmpty()) {

            return;
        }

        InputConnection c = ic();

        if (c == null) {
            return;
        }

        String before =
                beforeCursor(160);

        String current =
                currentWord(before);

        String[] context =
                previousWordsBeforeCurrent(
                        before,
                        current
                );

        String finalWord =
                current.isEmpty()
                        ? suggestion
                        : matchCase(
                                current,
                                suggestion
                        );

        if (!current.isEmpty()) {
            replaceCurrentWord(
                    current,
                    finalWord
            );
        } else {
            c.commitText(
                    finalWord,
                    1
            );
        }

        c.commitText(
                " ",
                1
        );

        languageExecutor.execute(
                () -> personal.learn(
                        context[0],
                        context[1],
                        finalWord
                )
        );

        scheduleSuggestionRefresh();
    }

    private void captureClipboard() {
        if (clipboardManager == null ||
                clipboardStore == null ||
                sensitiveInput) {
            return;
        }

        try {
            if (!clipboardManager.hasPrimaryClip()) {
                return;
            }

            ClipData clip =
                    clipboardManager.getPrimaryClip();

            if (clip == null ||
                    clip.getItemCount() == 0) {
                return;
            }

            CharSequence value =
                    clip.getItemAt(0)
                            .coerceToText(this);

            if (value == null) {
                return;
            }

            String text =
                    value.toString();

            if (text.trim().isEmpty()) {
                return;
            }

            clipboardStore.add(text);

            if (clipboardPanelView != null) {
                clipboardPanelView.setItems(
                        clipboardStore.getAll()
                );
            }
        } catch (Throwable ignored) {}
    }

    private void showClipboardPanel() {
        captureClipboard();

        clipboardPanelView =
                new ClipboardPanelView(
                        this,
                        new ClipboardPanelView.Listener() {
                            @Override public void onPaste(
                                    String text
                            ) {
                                InputConnection c = ic();

                                if (c != null &&
                                        text != null) {
                                    c.commitText(
                                            text,
                                            1
                                    );

                                    onKeyFeedback();
                                }
                            }

                            @Override public void onClose() {
                                clipboardPanelView = null;

                                setInputView(
                                        onCreateInputView()
                                );

                                if (correctionEnabled) {
                                    scheduleSuggestionRefresh();
                                }
                            }

                            @Override public void onClear() {
                                if (clipboardStore != null) {
                                    clipboardStore.clear();
                                }

                                try {
                                    if (clipboardManager != null) {
                                        if (Build.VERSION.SDK_INT >= 28) {
                                            clipboardManager.clearPrimaryClip();
                                        } else {
                                            clipboardManager.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                            "",
                                                            ""
                                                    )
                                            );
                                        }
                                    }
                                } catch (Throwable ignored) {}

                                if (clipboardPanelView != null) {
                                    clipboardPanelView.setItems(
                                            clipboardStore == null
                                                    ? new ArrayList<>()
                                                    : clipboardStore.getAll()
                                    );
                                }
                            }
                        }
                );

        clipboardPanelView.setFixedHeightDp(
                preferredHeightDp()
        );

        clipboardPanelView.setItems(
                clipboardStore == null
                        ? new ArrayList<>()
                        : clipboardStore.getAll()
        );

        setInputView(
                clipboardPanelView
        );
    }

    @Override public void onOpenClipboard() {
        showClipboardPanel();
    }

    @Override public void onHide() {
        requestHideSelf(0);
    }

    private void scheduleSuggestionRefresh() {
        mainHandler.removeCallbacks(
                suggestionRefresh
        );

        if (!correctionEnabled ||
                numericInput) {

            setSuggestions(
                    new String[0]
            );

            return;
        }

        mainHandler.post(
                suggestionRefresh
        );
    }

    private void requestSuggestionsNow() {
        if (!correctionEnabled ||
                numericInput) {

            return;
        }

        final String before =
                beforeCursor(180);

        final String current =
                currentWord(before);

        final String[] context =
                previousWordsBeforeCurrent(
                        before,
                        current
                );

        final int generation =
                suggestionGeneration.incrementAndGet();

        languageExecutor.execute(
                () -> {
                    String[] suggestions;

                    LocalDictionary.Correction correction =
                            null;

                    if (current.length() >= 1) {
                        String[] dictionaryValues =
                                dictionary.suggest(
                                        current,
                                        personal,
                                        6
                                );

                        String[] properValues =
                                properNames == null
                                        ? new String[0]
                                        : properNames.suggest(
                                                current,
                                                4
                                        );

                        suggestions =
                                mergeSuggestionSources(
                                        current,
                                        dictionaryValues,
                                        properValues,
                                        3
                                );

                        correction =
                                dictionary.bestAutocorrect(
                                        current,
                                        personal
                                );

                        if (correction == null &&
                                properNames != null) {
                            String properCorrection =
                                    properNames.bestCorrection(
                                            current
                                    );

                            if (properCorrection != null) {
                                correction =
                                        new LocalDictionary.Correction(
                                                properCorrection,
                                                1,
                                                false
                                        );
                            }
                        }
                    } else if (current.isEmpty()) {
                        suggestions =
                                personal.predictNext(
                                        context[0],
                                        context[1],
                                        3
                                );
                    } else {
                        suggestions =
                                new String[0];
                    }

                    final LocalDictionary.Correction finalCorrection =
                            correction;

                    final String[] finalSuggestions =
                            suggestions;

                    mainHandler.post(
                            () -> {
                                if (generation !=
                                        suggestionGeneration.get()) {

                                    return;
                                }

                                pendingWordNorm =
                                        LocalDictionary.normalize(
                                                current
                                        );

                                pendingCorrection =
                                        finalCorrection;

                                setSuggestions(
                                        finalSuggestions
                                );
                            }
                    );
                }
        );
    }
}
