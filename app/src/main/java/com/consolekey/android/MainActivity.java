package com.consolekey.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private SharedPreferences prefs;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button button(String text) {
        Button b = new Button(this);

        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(25,25,25));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54));
        p.setMargins(0, dp(7), 0, dp(7));

        b.setLayoutParams(p);
        return b;
    }

    private void addControl(
            LinearLayout root,
            String title,
            String prefKey,
            int defaultValue,
            int min,
            int max,
            String unit
    ) {
        TextView label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setPadding(0, dp(14), 0, dp(4));

        root.addView(label, new LinearLayout.LayoutParams(-1, -2));

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);

        int current = Math.max(min, Math.min(max, prefs.getInt(prefKey, defaultValue)));
        seek.setProgress(current - min);

        label.setText(title + ": " + current + " " + unit);
        root.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                label.setText(title + ": " + value + " " + unit);

                if (fromUser) {
                    prefs.edit().putInt(prefKey, value).apply();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);

        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(21);
        v.setPadding(0, dp(22), 0, dp(2));

        return v;
    }

    private TextView help(String text) {
        TextView v = new TextView(this);

        v.setText(text);
        v.setTextColor(Color.GRAY);
        v.setTextSize(13);

        return v;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        prefs = getSharedPreferences(ConsoleImeService.PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(Color.BLACK);

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Console Keyboard");
        title.setTextColor(Color.WHITE);
        title.setTextSize(29);
        title.setGravity(Gravity.CENTER);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText(
                "v0.2.9\n\n" +
                "Retrato: teclado normal + painel de emoji por categorias + long press.\n" +
                "Paisagem: console por toque + controle.\n" +
                "Xbox/PlayStation detectados automaticamente."
        );
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(14), 0, dp(16));

        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        Button enable = button("1. Ativar teclado no Android");
        enable.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        );
        root.addView(enable);

        Button choose = button("2. Selecionar Console Keyboard");
        choose.setOnClickListener(v -> {
            InputMethodManager imm =
                    (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

            imm.showInputMethodPicker();
        });
        root.addView(choose);

        root.addView(sectionTitle("Tamanho do teclado"));
        root.addView(help(
                "Retrato e paisagem ficam separados. Feche e abra o teclado para aplicar a nova altura."
        ));

        addControl(
                root,
                "Retrato",
                ConsoleImeService.PREF_PORTRAIT_HEIGHT,
                235,
                180,
                340,
                "dp"
        );

        addControl(
                root,
                "Paisagem",
                ConsoleImeService.PREF_LANDSCAPE_HEIGHT,
                150,
                105,
                240,
                "dp"
        );

        root.addView(sectionTitle("Toque e segurar"));
        root.addView(help(
                "Controla quanto tempo precisa segurar uma tecla antes de aparecerem ç, acentos e símbolos extras."
        ));

        addControl(
                root,
                "Atraso",
                ConsoleImeService.PREF_LONG_PRESS_DELAY,
                420,
                200,
                700,
                "ms"
        );

        Button reset = button("Restaurar padrões");
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .putInt(ConsoleImeService.PREF_PORTRAIT_HEIGHT, 235)
                    .putInt(ConsoleImeService.PREF_LANDSCAPE_HEIGHT, 150)
                    .putInt(ConsoleImeService.PREF_LONG_PRESS_DELAY, 420)
                    .apply();

            recreate();
        });

        root.addView(reset);

        TextView info = help(
                "Segure C para Ç; A/E/I/O/U para acentos; números e pontuação também têm atalhos. " +
                "No modo console, segure A/✕ em uma letra e use o D-pad para escolher a alternativa. " +
                "Backspace segurado repete a exclusão."
        );
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(18), 0, 0);

        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }
}
