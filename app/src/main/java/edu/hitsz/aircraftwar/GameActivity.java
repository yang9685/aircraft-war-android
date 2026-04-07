package edu.hitsz.aircraftwar;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.aircraftwar.audio.SoundManager;
import edu.hitsz.aircraftwar.data.AppPreferences;
import edu.hitsz.aircraftwar.data.ScoreRepository;
import edu.hitsz.aircraftwar.game.Difficulty;

public class GameActivity extends AppCompatActivity implements GameSurfaceView.GameSessionListener {

    public static final String EXTRA_DIFFICULTY = "difficulty";

    private GameSurfaceView gameSurfaceView;
    private SoundManager soundManager;
    private Difficulty difficulty;
    private boolean gameOverHandled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        difficulty = parseDifficulty(getIntent().getStringExtra(EXTRA_DIFFICULTY));
        soundManager = SoundManager.getInstance(this);
        soundManager.setSoundEnabled(AppPreferences.isSoundEnabled(this));

        gameSurfaceView = new GameSurfaceView(this, difficulty, this, soundManager);
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
        if (!gameOverHandled) {
            soundManager.playGameBgm();
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

        EditText input = new EditText(this);
        input.setHint(R.string.player_name_hint);
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle(R.string.game_over_title)
                .setMessage(getString(R.string.game_over_message, score, durationSeconds))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(R.string.save_score, (dialog, which) -> {
                    String playerName = input.getText().toString().trim();
                    if (playerName.isEmpty()) {
                        playerName = getString(R.string.default_player_name);
                    }
                    new ScoreRepository(this).saveScore(playerName, score, durationSeconds, difficulty);
                    openLeaderboard();
                })
                .setNegativeButton(R.string.skip_save, (dialog, which) -> openLeaderboard())
                .show();
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
