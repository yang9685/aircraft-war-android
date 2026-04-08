package edu.hitsz.aircraftwar;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import edu.hitsz.aircraftwar.data.ScoreRecord;
import edu.hitsz.aircraftwar.data.ScoreRepository;

public class LeaderboardActivity extends AppCompatActivity {

    private ScoreRecordAdapter adapter;
    private ScoreRepository scoreRepository;
    private TextView summaryTextView;
    private Button clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        scoreRepository = new ScoreRepository(this);

        ListView scoresListView = findViewById(R.id.list_scores);
        summaryTextView = findViewById(R.id.text_summary);
        clearButton = findViewById(R.id.button_clear_scores);
        Button backButton = findViewById(R.id.button_back_menu);

        adapter = new ScoreRecordAdapter(this);
        scoresListView.setAdapter(adapter);
        scoresListView.setEmptyView(findViewById(R.id.panel_empty_scores));

        clearButton.setOnClickListener(view -> {
            scoreRepository.clearScores();
            reloadScores();
        });
        backButton.setOnClickListener(view -> finish());

        reloadScores();
    }

    private void reloadScores() {
        List<ScoreRecord> records = scoreRepository.loadScores();
        if (records.isEmpty()) {
            summaryTextView.setText(R.string.leaderboard_summary_empty);
        } else {
            summaryTextView.setText(getString(
                    R.string.leaderboard_summary_template,
                    records.size(),
                    records.get(0).getScore()));
        }
        clearButton.setEnabled(!records.isEmpty());
        adapter.replaceData(records);
    }
}
