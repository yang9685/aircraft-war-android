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
import edu.hitsz.aircraftwar.network.MultiplayerSessionStore;
import edu.hitsz.aircraftwar.network.SocketMatchClient;

public class GameActivity extends AppCompatActivity
        implements FloatingJoystickGameSurfaceView.GameSessionListener, SocketMatchClient.Listener {

    public static final String EXTRA_DIFFICULTY = "difficulty";
    public static final String EXTRA_MULTIPLAYER = "multiplayer";
    public static final String EXTRA_PLAYER_NAME = "player_name";
    public static final String EXTRA_OPPONENT_NAME = "opponent_name";

    private FloatingJoystickGameSurfaceView gameSurfaceView;
    private SoundManager soundManager;
    private Difficulty difficulty;
    private boolean gameOverHandled;
    private boolean multiplayerMode;
    private boolean resultDialogShown;
    private String localPlayerName;
    private String opponentPlayerName;
    private SocketMatchClient matchClient;
    private MatchResult localResult;
    private MatchResult opponentResult;
    private AlertDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        difficulty = parseDifficulty(getIntent().getStringExtra(EXTRA_DIFFICULTY));
        multiplayerMode = getIntent().getBooleanExtra(EXTRA_MULTIPLAYER, false);
        localPlayerName = getIntent().getStringExtra(EXTRA_PLAYER_NAME);
        opponentPlayerName = getIntent().getStringExtra(EXTRA_OPPONENT_NAME);
        soundManager = SoundManager.getInstance(this);
        soundManager.setSoundEnabled(AppPreferences.isSoundEnabled(this));

        gameSurfaceView = new FloatingJoystickGameSurfaceView(this, difficulty, this, soundManager);
        if (multiplayerMode) {
            matchClient = MultiplayerSessionStore.getActiveClient();
            if (matchClient == null) {
                finish();
                return;
            }
            matchClient.setListener(this);
            gameSurfaceView.setOpponentState(opponentPlayerName, 0, false);
        }
        setContentView(gameSurfaceView);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeMultiplayerSession();
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
        dismissWaitingDialog();
        if (isFinishing()) {
            closeMultiplayerSession();
        }
        soundManager.stopBgm();
        super.onDestroy();
    }

    @Override
    public void onScoreChanged(int score) {
        if (multiplayerMode && matchClient != null) {
            matchClient.sendScore(score);
        }
    }

    @Override
    public void onGameOver(int score, long durationSeconds, Difficulty difficulty) {
        if (gameOverHandled || isFinishing()) {
            return;
        }
        gameOverHandled = true;
        soundManager.stopBgm();

        if (multiplayerMode) {
            localResult = new MatchResult(score, durationSeconds, difficulty);
            if (matchClient != null) {
                matchClient.sendDeath(score, durationSeconds);
            }
            maybeShowMatchResult();
            if (!resultDialogShown) {
                showWaitingDialog();
            }
            return;
        }

        showSinglePlayerResultDialog(score, durationSeconds, difficulty);
    }

    @Override
    public void onConnecting() {
        // No-op on battle page.
    }

    @Override
    public void onWaitingForOpponent(Difficulty difficulty) {
        // No-op on battle page.
    }

    @Override
    public void onMatched(String opponentName, Difficulty difficulty) {
        opponentPlayerName = opponentName;
        if (gameSurfaceView != null) {
            gameSurfaceView.setOpponentState(opponentPlayerName, 0, false);
        }
    }

    @Override
    public void onOpponentStateChanged(String opponentName, int score, boolean defeated, long durationSeconds) {
        opponentPlayerName = opponentName;
        if (defeated) {
            opponentResult = new MatchResult(score, durationSeconds, difficulty);
        }
        if (gameSurfaceView != null) {
            gameSurfaceView.setOpponentState(opponentPlayerName, score, defeated);
        }
        maybeShowMatchResult();
    }

    @Override
    public void onMatchFinished(int localScore, long localDurationSeconds, int opponentScore, long opponentDurationSeconds) {
        if (localResult == null) {
            localResult = new MatchResult(localScore, localDurationSeconds, difficulty);
        }
        opponentResult = new MatchResult(opponentScore, opponentDurationSeconds, difficulty);
        if (gameSurfaceView != null) {
            gameSurfaceView.setOpponentState(opponentPlayerName, opponentScore, true);
        }
        maybeShowMatchResult();
    }

    @Override
    public void onDisconnected(String reason) {
        dismissWaitingDialog();
        if (isFinishing()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("\u8054\u673A\u5DF2\u65AD\u5F00")
                .setMessage(reason)
                .setCancelable(false)
                .setPositiveButton("\u8FD4\u56DE\u4E3B\u83DC\u5355", (dialog, which) -> {
                    closeMultiplayerSession();
                    finish();
                })
                .show();
    }

    private void showSinglePlayerResultDialog(int score, long durationSeconds, Difficulty difficulty) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_over_clean, null, false);
        TextView finalScoreTextView = dialogView.findViewById(R.id.text_final_score);
        TextView finalDurationTextView = dialogView.findViewById(R.id.text_final_duration);
        TextInputEditText input = dialogView.findViewById(R.id.edit_player_name);
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
            if (playerName.isEmpty()) {
                playerName = "\u98de\u884c\u5458";
            }
            new ScoreRepository(this).saveScore(playerName, score, durationSeconds, difficulty);
            dialog.dismiss();
            openLeaderboard();
        });
    }

    private void maybeShowMatchResult() {
        if (!multiplayerMode || resultDialogShown || localResult == null || opponentResult == null || isFinishing()) {
            return;
        }
        resultDialogShown = true;
        dismissWaitingDialog();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_game_over_clean, null, false);
        TextView finalScoreTextView = dialogView.findViewById(R.id.text_final_score);
        TextView finalDurationTextView = dialogView.findViewById(R.id.text_final_duration);
        TextInputEditText input = dialogView.findViewById(R.id.edit_player_name);

        finalScoreTextView.setText(localResult.score + " \u5206");
        finalDurationTextView.setText(
                "\u4F60\uFF1A"
                        + UiText.formatDuration(localResult.durationSeconds)
                        + "\n"
                        + (opponentPlayerName == null || opponentPlayerName.isEmpty() ? "\u5BF9\u624B" : opponentPlayerName)
                        + "\uFF1A"
                        + opponentResult.score
                        + " \u5206 / "
                        + UiText.formatDuration(opponentResult.durationSeconds));
        if (localPlayerName != null && !localPlayerName.trim().isEmpty()) {
            input.setText(localPlayerName);
            input.setSelection(localPlayerName.length());
        }

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
            closeMultiplayerSession();
            openLeaderboard();
        });
        dialogView.findViewById(R.id.button_save_score).setOnClickListener(view -> {
            CharSequence inputText = input.getText();
            String playerName = inputText == null ? "" : inputText.toString().trim();
            if (playerName.isEmpty()) {
                playerName = localPlayerName == null || localPlayerName.trim().isEmpty()
                        ? "\u98de\u884c\u5458"
                        : localPlayerName.trim();
            }
            new ScoreRepository(this).saveScore(playerName, localResult.score, localResult.durationSeconds, difficulty);
            dialog.dismiss();
            closeMultiplayerSession();
            openLeaderboard();
        });
    }

    private void showWaitingDialog() {
        if (waitingDialog != null || isFinishing()) {
            return;
        }
        waitingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("\u5DF2\u9635\u4EA1")
                .setMessage("\u6B63\u5728\u7B49\u5F85\u5BF9\u624B\u5B8C\u6210\u672C\u5C40\u5BF9\u6218\u2026")
                .setCancelable(false)
                .create();
        waitingDialog.show();
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null) {
            waitingDialog.dismiss();
            waitingDialog = null;
        }
    }

    private void closeMultiplayerSession() {
        if (matchClient != null) {
            matchClient.setListener(null);
            matchClient.disconnect();
            matchClient = null;
        }
        MultiplayerSessionStore.clear();
    }

    private void openLeaderboard() {
        Intent intent = new Intent(this, LeaderboardActivity.class);
        intent.putExtra(LeaderboardActivity.EXTRA_DIFFICULTY, difficulty.name());
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

    private static final class MatchResult {
        private final int score;
        private final long durationSeconds;
        private final Difficulty difficulty;

        private MatchResult(int score, long durationSeconds, Difficulty difficulty) {
            this.score = score;
            this.durationSeconds = durationSeconds;
            this.difficulty = difficulty;
        }
    }
}
