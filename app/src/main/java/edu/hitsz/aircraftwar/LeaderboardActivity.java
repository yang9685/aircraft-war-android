package edu.hitsz.aircraftwar;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import edu.hitsz.aircraftwar.data.AppPreferences;
import edu.hitsz.aircraftwar.data.OnlineLeaderboardClient;
import edu.hitsz.aircraftwar.data.ScoreRecord;
import edu.hitsz.aircraftwar.data.ScoreRepository;
import edu.hitsz.aircraftwar.game.Difficulty;

public class LeaderboardActivity extends AppCompatActivity {

    public static final String EXTRA_DIFFICULTY = "difficulty";

    private ScoreRecordAdapter adapter;
    private ScoreRepository scoreRepository;
    private TextView summaryTextView;
    private Button clearButton;
    private Difficulty selectedDifficulty;
    private boolean onlineMode;
    private OnlineLeaderboardClient onlineLeaderboardClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        scoreRepository = new ScoreRepository(this);
        selectedDifficulty = parseDifficulty(getIntent().getStringExtra(EXTRA_DIFFICULTY));
        onlineLeaderboardClient = new OnlineLeaderboardClient(
                AppPreferences.getMatchHost(this),
                AppPreferences.getMatchPort(this));

        ListView scoresListView = findViewById(R.id.list_scores);
        summaryTextView = findViewById(R.id.text_summary);
        clearButton = findViewById(R.id.button_clear_scores);
        Button backButton = findViewById(R.id.button_back_menu);

        // 先初始化 adapter，避免 check() 触发监听时空指针
        adapter = new ScoreRecordAdapter(this);
        scoresListView.setAdapter(adapter);
        scoresListView.setEmptyView(findViewById(R.id.panel_empty_scores));
        scoresListView.setOnItemClickListener((parent, view, position, id) -> {
            if (onlineMode) {
                return;
            }
            ScoreRecord targetRecord = adapter.getItem(position);
            showDeleteConfirmDialog(targetRecord);
        });

        MaterialButtonToggleGroup difficultyGroup = findViewById(R.id.group_difficulty);
        difficultyGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.button_filter_easy) {
                selectedDifficulty = Difficulty.EASY;
            } else if (checkedId == R.id.button_filter_normal) {
                selectedDifficulty = Difficulty.NORMAL;
            } else if (checkedId == R.id.button_filter_hard) {
                selectedDifficulty = Difficulty.HARD;
            } else {
                return;
            }
            reloadScores();
        });
        difficultyGroup.check(resolveDifficultyButtonId(selectedDifficulty));

        MaterialButtonToggleGroup sourceGroup = findViewById(R.id.group_source);
        sourceGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            onlineMode = checkedId == R.id.button_source_online;
            clearButton.setEnabled(!onlineMode && adapter.getCount() > 0);
            reloadScores();
        });
        sourceGroup.check(R.id.button_source_local);

        clearButton.setOnClickListener(view -> {
            scoreRepository.clearScoresByDifficulty(selectedDifficulty);
            reloadScores();
        });
        backButton.setOnClickListener(view -> finish());

        reloadScores();
    }

    private void reloadScores() {
        if (adapter == null) {
            return;
        }

        if (onlineMode) {
            loadOnlineScores();
            return;
        }

        loadLocalScores();
    }

    private void loadLocalScores() {
        List<ScoreRecord> records = scoreRepository.loadScoresByDifficulty(selectedDifficulty);
        String difficultyLabel = UiText.getDifficultyLabel(this, selectedDifficulty);
        if (records.isEmpty()) {
            summaryTextView.setText(getString(
                    R.string.leaderboard_summary_empty_by_difficulty,
                    difficultyLabel));
        } else {
            summaryTextView.setText(getString(
                    R.string.leaderboard_summary_template_by_difficulty,
                    difficultyLabel,
                    records.size(),
                    records.get(0).getScore()));
        }
        clearButton.setEnabled(!records.isEmpty());
        adapter.replaceData(records);
    }

    private void loadOnlineScores() {
        clearButton.setEnabled(false);
        summaryTextView.setText("正在加载在线排行榜...");
        onlineLeaderboardClient.loadLeaderboard(selectedDifficulty, new OnlineLeaderboardClient.LoadCallback() {
            @Override
            public void onSuccess(List<ScoreRecord> records) {
                String difficultyLabel = UiText.getDifficultyLabel(LeaderboardActivity.this, selectedDifficulty);
                if (records.isEmpty()) {
                    summaryTextView.setText("在线 " + difficultyLabel + " 榜单暂无记录");
                } else {
                    summaryTextView.setText("在线 " + difficultyLabel + " 榜单共 "
                            + records.size() + " 条，最高分 " + records.get(0).getScore());
                }
                adapter.replaceData(records);
            }

            @Override
            public void onFailure(String reason) {
                summaryTextView.setText(reason);
                adapter.replaceData(java.util.Collections.emptyList());
            }
        });
    }

    private void showDeleteConfirmDialog(ScoreRecord targetRecord) {
        String message = getString(
                R.string.delete_score_confirm_message,
                targetRecord.getPlayerName(),
                targetRecord.getScore());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_score_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel_label, null)
                .setPositiveButton(R.string.delete_label, (dialog, which) -> {
                    if (scoreRepository.deleteScoreRecord(targetRecord)) {
                        reloadScores();
                    }
                })
                .show();
    }

    private int resolveDifficultyButtonId(Difficulty difficulty) {
        if (difficulty == Difficulty.EASY) {
            return R.id.button_filter_easy;
        }
        if (difficulty == Difficulty.HARD) {
            return R.id.button_filter_hard;
        }
        return R.id.button_filter_normal;
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
