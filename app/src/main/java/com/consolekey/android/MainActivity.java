package com.consolekey.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
        p.setMargins(0, dp(8), 0, dp(8));
        b.setLayoutParams(p);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("Console Keyboard");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("v0.1.0\n\nRetrato: teclado normal inspirado no Samsung Keyboard.\nPaisagem: teclado de console com toque + controle.\n\nXbox e PlayStation são detectados automaticamente.");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(18), 0, dp(20));
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

        TextView info = new TextView(this);
        info.setText("Depois de ativar e selecionar, abra qualquer campo de texto. Em pé aparece o teclado normal; ao girar para paisagem aparece o modo console.");
        info.setTextColor(Color.GRAY);
        info.setTextSize(14);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(18), 0, 0);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }
}
