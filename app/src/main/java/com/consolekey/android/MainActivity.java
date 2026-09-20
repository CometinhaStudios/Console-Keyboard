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

    private void addSizeControl(LinearLayout root, String title, String prefKey, int defaultValue, int min, int max) {
        TextView label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setPadding(0, dp(14), 0, dp(4));
        root.addView(label, new LinearLayout.LayoutParams(-1, -2));

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        int current = Math.max(min, Math.min(max, prefs.getInt(prefKey, defaultValue)));
        seek.setProgress(current - min);
        label.setText(title + ": " + current + " dp");
        root.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                label.setText(title + ": " + value + " dp");
                if (fromUser) prefs.edit().putInt(prefKey, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        sub.setText("v0.1.4\n\nRetrato: teclado normal + emoji.\nPaisagem: modo console por toque + controle.\nXbox/PlayStation detectados automaticamente.");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(14), 0, dp(16));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        Button enable = button("1. Ativar teclado no Android");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);

        Button choose = button("2. Selecionar Console Keyboard");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        root.addView(choose);

        TextView section = new TextView(this);
        section.setText("Tamanho do teclado");
        section.setTextColor(Color.WHITE);
        section.setTextSize(21);
        section.setPadding(0, dp(22), 0, dp(2));
        root.addView(section, new LinearLayout.LayoutParams(-1, -2));

        TextView explain = new TextView(this);
        explain.setText("Ajuste separado para retrato e paisagem. Feche e abra o teclado novamente para aplicar.");
        explain.setTextColor(Color.GRAY);
        explain.setTextSize(13);
        root.addView(explain, new LinearLayout.LayoutParams(-1, -2));

        addSizeControl(root, "Retrato", ConsoleImeService.PREF_PORTRAIT_HEIGHT, 235, 180, 340);
        addSizeControl(root, "Paisagem", ConsoleImeService.PREF_LANDSCAPE_HEIGHT, 150, 105, 240);

        Button reset = button("Restaurar tamanhos padrão");
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .putInt(ConsoleImeService.PREF_PORTRAIT_HEIGHT, 235)
                    .putInt(ConsoleImeService.PREF_LANDSCAPE_HEIGHT, 150)
                    .apply();
            recreate();
        });
        root.addView(reset);

        TextView info = new TextView(this);
        info.setText("Vibração: com gamepad compatível, o retorno tenta ir para o controle. Se o controle não expuser vibração ao Android, o celular vibra como fallback.");
        info.setTextColor(Color.GRAY);
        info.setTextSize(13);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(18), 0, 0);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }
}
