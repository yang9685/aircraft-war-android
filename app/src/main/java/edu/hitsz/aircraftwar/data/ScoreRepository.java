package edu.hitsz.aircraftwar.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.hitsz.aircraftwar.game.Difficulty;

public class ScoreRepository {

    private static final String PREFS_NAME = "aircraft_war_scores";
    private static final String KEY_SCORES = "scores";
    private static final int MAX_RECORDS = 20;

    private final SharedPreferences sharedPreferences;

    public ScoreRepository(Context context) {
        this.sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<ScoreRecord> loadScores() {
        String raw = sharedPreferences.getString(KEY_SCORES, "[]");
        List<ScoreRecord> records = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                records.add(ScoreRecord.fromJson(object));
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }

        records.sort(buildComparator());
        return records;
    }

    public void saveScore(String playerName, int score, long durationSeconds, Difficulty difficulty) {
        List<ScoreRecord> records = loadScores();
        records.add(new ScoreRecord(playerName, score, durationSeconds, difficulty, System.currentTimeMillis()));
        records.sort(buildComparator());
        if (records.size() > MAX_RECORDS) {
            records = new ArrayList<>(records.subList(0, MAX_RECORDS));
        }

        JSONArray array = new JSONArray();
        for (ScoreRecord record : records) {
            try {
                array.put(record.toJson());
            } catch (JSONException ignored) {
                // Skip malformed record serialization.
            }
        }
        sharedPreferences.edit().putString(KEY_SCORES, array.toString()).apply();
    }

    public void clearScores() {
        sharedPreferences.edit().remove(KEY_SCORES).apply();
    }

    private Comparator<ScoreRecord> buildComparator() {
        return Comparator
                .comparingInt(ScoreRecord::getScore).reversed()
                .thenComparing(Comparator.comparingLong(ScoreRecord::getCreatedAt).reversed());
    }
}
