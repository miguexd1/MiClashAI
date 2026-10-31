package com.clashai.assistant;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 1001;
    private TextView status, ownValue, enemyValue, advice;
    private SeekBar ownBar, enemyBar;
    private boolean threat = false;
    private boolean doubleElixir = false;
    private boolean defensiveAnswer = true;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(28, 28, 28, 24);
        page.setBackgroundColor(Color.rgb(18, 22, 32));

        TextView title = label("CLASH AI", 28, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        page.addView(label("Asistente de estrategia · versión 0.2", 14, Color.LTGRAY));
        page.addView(label("\nElixir propio (estimación manual)", 16, Color.WHITE));
        ownValue = label("5 / 10", 16, Color.WHITE);
        page.addView(ownValue);
        ownBar = bar(5);
        page.addView(ownBar);

        page.addView(label("\nElixir rival (estimación manual)", 16, Color.WHITE));
        enemyValue = label("5 / 10", 16, Color.WHITE);
        page.addView(enemyValue);
        enemyBar = bar(5);
        page.addView(enemyBar);

        Button threatButton = new Button(this);
        threatButton.setText("Amenaza enemiga: NO");
        threatButton.setOnClickListener(v -> {
            threat = !threat;
            threatButton.setText("Amenaza enemiga: " + (threat ? "SÍ" : "NO"));
            updateAdvice();
        });
        page.addView(threatButton);

        Button answerButton = new Button(this);
        answerButton.setText("Tengo defensa adecuada: SÍ");
        answerButton.setOnClickListener(v -> {
            defensiveAnswer = !defensiveAnswer;
            answerButton.setText("Tengo defensa adecuada: " + (defensiveAnswer ? "SÍ" : "NO"));
            updateAdvice();
        });
        page.addView(answerButton);

        Button doubleButton = new Button(this);
        doubleButton.setText("Doble elixir: NO");
        doubleButton.setOnClickListener(v -> {
            doubleElixir = !doubleElixir;
            doubleButton.setText("Doble elixir: " + (doubleElixir ? "SÍ" : "NO"));
            updateAdvice();
        });
        page.addView(doubleButton);

        Button analyze = new Button(this);
        analyze.setText("Analizar situación");
        analyze.setOnClickListener(v -> updateAdvice());
        page.addView(analyze);

        advice = label("\nConsejo: ajusta los datos y pulsa Analizar situación.", 17, Color.rgb(150, 230, 170));
        page.addView(advice);

        Button capture = new Button(this);
        capture.setText("Solicitar captura de pantalla");
        capture.setOnClickListener(v -> requestCapture());
        page.addView(capture);

        Button stop = new Button(this);
        stop.setText("Detener captura");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            status.setText("Captura detenida.");
        });
        page.addView(stop);

        status = label("\nCaptura: inactiva", 14, Color.LTGRAY);
        page.addView(status);
        page.addView(label("\nLimitación: esta versión no identifica automáticamente cartas, tropas ni elixir. La captura no se guarda ni se transmite; todavía no se analiza visualmente.", 12, Color.LTGRAY));

        ownBar.setOnSeekBarChangeListener(listener(ownValue));
        enemyBar.setOnSeekBarChangeListener(listener(enemyValue));
        setContentView(page);
        updateAdvice();
    }

    private SeekBar bar(int value) {
        SeekBar b = new SeekBar(this);
        b.setMax(10);
        b.setProgress(value);
        return b;
    }

    private SeekBar.OnSeekBarChangeListener listener(TextView output) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean user) {
                output.setText(value + " / 10");
                updateAdvice();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 5, 0, 5);
        return t;
    }

    private void updateAdvice() {
        if (advice == null || ownBar == null || enemyBar == null) return;
        advice.setText("Consejo: " + StrategyEngine.advise(
            ownBar.getProgress(), enemyBar.getProgress(),
            threat, doubleElixir, defensiveAnswer));
    }

    private void requestCapture() {
        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class);
            service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            status.setText("\nCaptura: activa (sin análisis visual todavía)");
        } else if (requestCode == REQUEST_CAPTURE) {
            status.setText("\nCaptura: permiso cancelado");
        }
    }
}
