package edu.hitsz.aircraftwar;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.Locale;

import edu.hitsz.aircraftwar.data.AppPreferences;
import edu.hitsz.aircraftwar.game.Difficulty;
import edu.hitsz.aircraftwar.network.MultiplayerSessionStore;
import edu.hitsz.aircraftwar.network.SocketMatchClient;

public class MultiplayerLobbyActivity extends AppCompatActivity implements SocketMatchClient.Listener {

    private EditText hostInput;
    private EditText portInput;
    private EditText playerNameInput;
    private TextView statusTextView;
    private TextView hintTextView;
    private Button connectButton;
    private MaterialButtonToggleGroup difficultyGroup;

    private SocketMatchClient matchClient;
    private Difficulty selectedDifficulty = Difficulty.NORMAL;
    private boolean launchingGame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_lobby);

        hostInput = findViewById(R.id.edit_host);
        portInput = findViewById(R.id.edit_port);
        playerNameInput = findViewById(R.id.edit_player_name);
        statusTextView = findViewById(R.id.text_match_status);
        hintTextView = findViewById(R.id.text_match_hint);
        connectButton = findViewById(R.id.button_connect_match);
        difficultyGroup = findViewById(R.id.group_match_difficulty);

        hostInput.setText(AppPreferences.getMatchHost(this));
        portInput.setText(String.valueOf(AppPreferences.getMatchPort(this)));
        String playerName = AppPreferences.getPlayerName(this);
        playerNameInput.setText(playerName.isEmpty() ? buildDefaultPlayerName() : playerName);

        difficultyGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.button_match_easy) {
                selectedDifficulty = Difficulty.EASY;
            } else if (checkedId == R.id.button_match_hard) {
                selectedDifficulty = Difficulty.HARD;
            } else {
                selectedDifficulty = Difficulty.NORMAL;
            }
        });
        difficultyGroup.check(R.id.button_match_normal);

        connectButton.setOnClickListener(view -> connectToMatchServer());
        findViewById(R.id.button_cancel_match).setOnClickListener(view -> {
            disconnectClient();
            finish();
        });

        renderIdleState();
    }

    @Override
    protected void onDestroy() {
        if (!launchingGame && !isChangingConfigurations()) {
            disconnectClient();
        }
        super.onDestroy();
    }

    @Override
    public void onConnecting() {
        statusTextView.setText("\u6B63\u5728\u8FDE\u63A5\u670D\u52A1\u5668\u2026");
        hintTextView.setText("\u6210\u529F\u540E\u5C06\u81EA\u52A8\u7B49\u5F85\u53E6\u4E00\u540D\u73A9\u5BB6\u52A0\u5165\u5BF9\u6218\u3002");
        setFormEnabled(false);
        connectButton.setEnabled(false);
    }

    @Override
    public void onWaitingForOpponent(Difficulty difficulty) {
        statusTextView.setText("\u5339\u914D\u4E2D");
        hintTextView.setText(
                String.format(
                        Locale.getDefault(),
                        "\u5DF2\u8FDE\u63A5\u6210\u529F\uFF0C\u6B63\u5728\u7B49\u5F85 %s \u96BE\u5EA6\u7684\u5BF9\u624B\u2026",
                        UiText.getDifficultyLabel(this, difficulty)));
    }

    @Override
    public void onMatched(String opponentName, Difficulty difficulty) {
        launchingGame = true;
        statusTextView.setText("\u5339\u914D\u6210\u529F");
        hintTextView.setText((opponentName == null || opponentName.isEmpty() ? "\u5BF9\u624B" : opponentName) + " \u5DF2\u8FDB\u5165\u6218\u573A");
        MultiplayerSessionStore.setActiveClient(matchClient);
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_MULTIPLAYER, true);
        intent.putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name());
        intent.putExtra(GameActivity.EXTRA_PLAYER_NAME, safeTrim(playerNameInput.getText().toString()));
        intent.putExtra(GameActivity.EXTRA_OPPONENT_NAME, opponentName);
        startActivity(intent);
        finish();
    }

    @Override
    public void onOpponentStateChanged(String opponentName, int score, boolean defeated, long durationSeconds) {
        // No-op before entering battle.
    }

    @Override
    public void onMatchFinished(int localScore, long localDurationSeconds, int opponentScore, long opponentDurationSeconds) {
        // No-op before entering battle.
    }

    @Override
    public void onDisconnected(String reason) {
        if (launchingGame || isFinishing() || isDestroyed()) {
            return;
        }
        statusTextView.setText("\u5DF2\u65AD\u5F00");
        hintTextView.setText(reason);
        setFormEnabled(true);
        connectButton.setEnabled(true);
        matchClient = null;
        MultiplayerSessionStore.clear();
    }

    private void connectToMatchServer() {
        String host = safeTrim(hostInput.getText().toString());
        String portValue = safeTrim(portInput.getText().toString());
        String playerName = safeTrim(playerNameInput.getText().toString());

        if (TextUtils.isEmpty(host)) {
            hostInput.setError("\u8BF7\u8F93\u5165\u670D\u52A1\u5668\u5730\u5740");
            return;
        }
        if (TextUtils.isEmpty(portValue)) {
            portInput.setError("\u8BF7\u8F93\u5165\u7AEF\u53E3");
            return;
        }
        if (TextUtils.isEmpty(playerName)) {
            playerNameInput.setError("\u8BF7\u8F93\u5165\u98DE\u884C\u5458\u4EE3\u53F7");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            portInput.setError("\u7AEF\u53E3\u5FC5\u987B\u662F\u6570\u5B57");
            return;
        }

        AppPreferences.setMatchHost(this, host);
        AppPreferences.setMatchPort(this, port);
        AppPreferences.setPlayerName(this, playerName);

        disconnectClient();
        matchClient = new SocketMatchClient(host, port, playerName, selectedDifficulty);
        matchClient.setListener(this);
        matchClient.connect();
    }

    private void disconnectClient() {
        if (matchClient != null) {
            matchClient.setListener(null);
            matchClient.disconnect();
            matchClient = null;
        }
        MultiplayerSessionStore.clear();
    }

    private void renderIdleState() {
        statusTextView.setText("\u672A\u5F00\u59CB\u8FDE\u63A5");
        hintTextView.setText("\u6A21\u62DF\u5668\u53EF\u7528 10.0.2.2 \u8FDE\u81EA\u5DF1\u7535\u8111\u3002\u771F\u673A\u8C03\u8BD5\u65F6\u8BF7\u586B\u7535\u8111\u5C40\u57DF\u7F51 IP\u3002");
    }

    private void setFormEnabled(boolean enabled) {
        hostInput.setEnabled(enabled);
        portInput.setEnabled(enabled);
        playerNameInput.setEnabled(enabled);
        difficultyGroup.setEnabled(enabled);
        findViewById(R.id.button_match_easy).setEnabled(enabled);
        findViewById(R.id.button_match_normal).setEnabled(enabled);
        findViewById(R.id.button_match_hard).setEnabled(enabled);
    }

    private String buildDefaultPlayerName() {
        int suffix = (int) (System.currentTimeMillis() % 1000L);
        return String.format(Locale.getDefault(), "Pilot-%03d", suffix);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
