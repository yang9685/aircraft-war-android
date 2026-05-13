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
import edu.hitsz.aircraftwar.data.OnlineLeaderboardClient;
import edu.hitsz.aircraftwar.data.ScoreRepository;
import edu.hitsz.aircraftwar.data.ScoreRecord;
import edu.hitsz.aircraftwar.game.Difficulty;
import edu.hitsz.aircraftwar.network.MultiplayerSessionStore;
import edu.hitsz.aircraftwar.network.SocketMatchClient;

public class GameActivity extends AppCompatActivity
        implements FloatingJoystickGameSurfaceView.GameSessionListener, SocketMatchClient.Listener {

    public static final String EXTRA_DIFFICULTY = "difficulty";
    public static final String EXTRA_ONLINE_BATTLE = "online_battle";
    public static final String EXTRA_PLAYER_NAME = "player_name";
    public static final String EXTRA_PLAYER_ID = "player_id";
    public static final String EXTRA_ROOM_ID = "room_id";
    public static final String EXTRA_HOST = "host";

    private FloatingJoystickGameSurfaceView gameSurfaceView;
    private SoundManager soundManager;
    private Difficulty difficulty;
    private boolean gameOverHandled;
    private boolean onlineBattle;
    private boolean resultDialogShown;
    private String localPlayerName;
    private int onlinePlayerId;
    private SocketMatchClient matchClient;
    private OnlineLeaderboardClient onlineLeaderboardClient;
    private MatchResult localResult;
    private int opponentScore;
    private long opponentDurationSeconds;
    private boolean opponentDead;
    private AlertDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        difficulty = parseDifficulty(getIntent().getStringExtra(EXTRA_DIFFICULTY));
        onlineBattle = getIntent().getBooleanExtra(EXTRA_ONLINE_BATTLE, false);
        localPlayerName = getIntent().getStringExtra(EXTRA_PLAYER_NAME);
        onlinePlayerId = getIntent().getIntExtra(EXTRA_PLAYER_ID, 0);
        onlineLeaderboardClient = new OnlineLeaderboardClient(
                AppPreferences.getMatchHost(this),
                AppPreferences.getMatchPort(this));
        soundManager = SoundManager.getInstance(this);
        soundManager.setSoundEnabled(AppPreferences.isSoundEnabled(this));

        gameSurfaceView = new FloatingJoystickGameSurfaceView(this, difficulty, this, soundManager);
        if (onlineBattle) {
            matchClient = MultiplayerSessionStore.getActiveClient();
            if (matchClient == null) {
                finish();
                return;
            }
            matchClient.setListener(this);
            gameSurfaceView.setOnlineBattle(true);
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
    public void onScoreChanged(int score, long durationSeconds, Difficulty difficulty) {
        if (onlineBattle && matchClient != null) {
            matchClient.sendScore(score, durationSeconds);
        }
    }

    @Override
    public void onGameOver(int score, long durationSeconds, Difficulty difficulty) {
        if (gameOverHandled || isFinishing()) {
            return;
        }
        gameOverHandled = true;
        soundManager.stopBgm();

        if (onlineBattle) {
            localResult = new MatchResult(score, durationSeconds, difficulty);
            if (matchClient != null) {
                matchClient.sendResult(score, durationSeconds);
            }
            showWaitingDialog();
            return;
        }

        showSinglePlayerResultDialog(score, durationSeconds, difficulty);
    }

    @Override
    public void onStatus(String message) {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.setMessage(message);
        }
    }

    @Override
    public void onMatchStarted(int roomId, int playerId, Difficulty difficulty) {
        // No-op on battle page.
    }

    @Override
    public void onOpponentScoreUpdate(int playerId, int score, long durationSeconds) {
        opponentScore = score;
        opponentDurationSeconds = durationSeconds;
        if (gameSurfaceView != null) {
            gameSurfaceView.updateOpponentScore(score);
        }
    }

    @Override
    public void onOpponentResult(int playerId, int score, long durationSeconds) {
        opponentScore = score;
        opponentDurationSeconds = durationSeconds;
        opponentDead = true;
        if (gameSurfaceView != null) {
            gameSurfaceView.updateOpponentScore(score);
            gameSurfaceView.setOpponentDead(true);
        }
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.setMessage("\u5BF9\u624B\u5DF2\u5B8C\u6210\u5BF9\u5C40\uff0c\u6B63\u5728\u751F\u6210\u6700\u7EC8\u7ED3\u679C\u2026");
        }
    }

    @Override
    public void onMatchResult(
            int winnerId,
            int playerOneScore,
            long playerOneDurationSeconds,
            int playerTwoScore,
            long playerTwoDurationSeconds) {
        if (localResult == null) {
            int localScore = onlinePlayerId == 1 ? playerOneScore : playerTwoScore;
            long localDurationSeconds = onlinePlayerId == 1 ? playerOneDurationSeconds : playerTwoDurationSeconds;
            localResult = new MatchResult(localScore, localDurationSeconds, difficulty);
        }
        opponentScore = onlinePlayerId == 1 ? playerTwoScore : playerOneScore;
        opponentDurationSeconds = onlinePlayerId == 1 ? playerTwoDurationSeconds : playerOneDurationSeconds;
        opponentDead = true;
        if (gameSurfaceView != null) {
            gameSurfaceView.updateOpponentScore(opponentScore);
            gameSurfaceView.setOpponentDead(true);
        }
        maybeShowMatchResult(winnerId);
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
            uploadScoreToOnlineLeaderboard(playerName, score, durationSeconds, difficulty);
            dialog.dismiss();
            openLeaderboard();
        });
    }

    private void maybeShowMatchResult(int winnerId) {
        if (!onlineBattle || resultDialogShown || localResult == null || isFinishing()) {
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
                        + "\u5BF9\u624B"
                        + "\uFF1A"
                        + opponentScore
                        + " \u5206 / "
                        + UiText.formatDuration(opponentDurationSeconds)
                        + "\n"
                        + buildResultLabel(winnerId));
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
            uploadScoreToOnlineLeaderboard(playerName, localResult.score, localResult.durationSeconds, difficulty);
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
            matchClient.close();
            matchClient = null;
        }
        MultiplayerSessionStore.clear();
    }

    private String buildResultLabel(int winnerId) {
        if (winnerId == 0) {
            return "\u672C\u5C40\u7ED3\u679C\uFF1A\u5E73\u5C40";
        }
        if (winnerId == onlinePlayerId) {
            return "\u672C\u5C40\u7ED3\u679C\uFF1A\u4F60\u83B7\u80DC\u4E86";
        }
        return "\u672C\u5C40\u7ED3\u679C\uFF1A\u5BF9\u624B\u83B7\u80DC";
    }

    private void openLeaderboard() {
        Intent intent = new Intent(this, LeaderboardActivity.class);
        intent.putExtra(LeaderboardActivity.EXTRA_DIFFICULTY, difficulty.name());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void uploadScoreToOnlineLeaderboard(
            String playerName,
            int score,
            long durationSeconds,
            Difficulty difficulty) {
        if (onlineLeaderboardClient == null) {
            return;
        }
        onlineLeaderboardClient.uploadScore(
                new ScoreRecord(playerName, score, durationSeconds, difficulty, System.currentTimeMillis()),
                new OnlineLeaderboardClient.UploadCallback() {
                    @Override
                    public void onSuccess() {
                        // Best effort upload.
                    }

                    @Override
                    public void onFailure(String reason) {
                        // Keep local save successful even if online upload fails.
                    }
                });
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

        private MatchResult(int score, long durationSeconds, Difficulty difficulty) {
            this.score = score;
            this.durationSeconds = durationSeconds;
        }
    }
}
