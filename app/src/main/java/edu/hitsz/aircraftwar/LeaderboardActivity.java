package edu.hitsz.aircraftwar;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import edu.hitsz.aircraftwar.data.ScoreRecord;
import edu.hitsz.aircraftwar.data.ScoreRepository;

public class LeaderboardActivity extends AppCompatActivity {

    private ArrayAdapter<String> adapter;
    private ScoreRepository scoreRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        scoreRepository = new ScoreRepository(this);

        ListView scoresListView = findViewById(R.id.list_scores);
        TextView emptyView = findViewById(R.id.text_empty_scores);
        Button clearButton = findViewById(R.id.button_clear_scores);
        Button backButton = findViewById(R.id.button_back_menu);

        adapter = new ArrayAdapter<>(this, R.layout.item_score, new ArrayList<>());
        scoresListView.setAdapter(adapter);
        scoresListView.setEmptyView(emptyView);

        clearButton.setOnClickListener(view -> {
            scoreRepository.clearScores();
            reloadScores();
        });
        backButton.setOnClickListener(view -> finish());

        reloadScores();
    }

    private void reloadScores() {
        List<ScoreRecord> records = scoreRepository.loadScores();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            lines.add(records.get(i).toDisplayLine(i + 1));
        }
        adapter.clear();
        adapter.addAll(lines);
        adapter.notifyDataSetChanged();
    }
}
