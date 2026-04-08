package edu.hitsz.aircraftwar;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import edu.hitsz.aircraftwar.audio.SoundManager;
import edu.hitsz.aircraftwar.data.AppPreferences;
import edu.hitsz.aircraftwar.data.ScoreRepository;
import edu.hitsz.aircraftwar.game.Difficulty;

public class GameActivity extends AppCompatActivity implements FloatingJoystickGameSurfaceView.GameSessionListener {

    public static final String EXTRA_DIFFICULTY = "difficulty";

    private FloatingJoystickGameSurfaceView gameSurfaceView;
    private SoundManager soundManager;
    private Difficulty difficulty;
    private boolean gameOverHandled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        difficulty = parseDifficulty(getIntent().getStringExtra(EXTRA_DIFFICULTY));
        soundManager = SoundManager.getInstance(this);
        soundManager.setSoundEnabled(AppPreferences.isSoundEnabled(this));

        gameSurfaceView = new FloatingJoystickGameSurfaceView(this, difficulty, this, soundManager);
        setContentView(gameSurfaceView);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameSurfaceView != null) {
            gameSurfaceView.onHostResume();
        }
    }

    @Override
    protected void onPause() {
        if (gameSurfaceView != null) {
            gameSurfaceView.onHostPause();
        }
        soundManager.pauseBgm();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        soundManager.stopBgm();
        super.onDestroy();
    }

    @Override
    public void onGameOver(int score, long durationSeconds, Difficulty difficulty) {
        if (gameOverHandled || isFinishing()) {
            return;
        }
        gameOverHandled = true;
        soundManager.stopBgm();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_over_clean, null, false);
        TextView finalScoreTextView = dialogView.findViewById(R.id.text_final_score);
        TextView finalDurationTextView = dialogView.findViewById(R.id.text_final_duration);
        TextInputEditText input = dialogView.findViewById(R.id.edit_player_name);
        /*

        finalScoreTextView.setText(score + " 分");
        */
        finalScoreTextView.setText(score + " \u5206");
        finalDurationTextView.setText(UiText.formatDuration(durationSeconds));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.button_skip_save).setOnClickListener(view -> {
            dialog.dismiss();
            openLeaderboard();
        });
        dialogView.findViewById(R.id.button_save_score).setOnClickListener(view -> {
            CharSequence inputText = input.getText();
            String playerName = inputText == null ? "" : inputText.toString().trim();
            /*
            if (playerName.isEmpty()) {
                playerName = "飞行员";
            }
            */
            if (playerName.isEmpty()) {
                playerName = "\u98de\u884c\u5458";
            }
            new ScoreRepository(this).saveScore(playerName, score, durationSeconds, difficulty);
            dialog.dismiss();
            openLeaderboard();
        });
    }

    private void openLeaderboard() {
        Intent intent = new Intent(this, LeaderboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private Difficulty parseDifficulty(String value) {
        if (value == null) {
            return Difficulty.NORMAL;
        }
        try {
            return Difficulty.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return Difficulty.NORMAL;
        }
    }
}
