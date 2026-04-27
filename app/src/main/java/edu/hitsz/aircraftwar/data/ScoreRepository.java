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
        return loadAllScoresInternal();
    }

    public List<ScoreRecord> loadScoresByDifficulty(Difficulty difficulty) {
        List<ScoreRecord> allRecords = loadAllScoresInternal();
        List<ScoreRecord> filteredRecords = new ArrayList<>();
        for (ScoreRecord record : allRecords) {
            if (record.getDifficulty() == difficulty) {
                filteredRecords.add(record);
            }
        }
        return filteredRecords;
    }

    public void saveScore(String playerName, int score, long durationSeconds, Difficulty difficulty) {
        List<ScoreRecord> records = loadAllScoresInternal();
        records.add(new ScoreRecord(playerName, score, durationSeconds, difficulty, System.currentTimeMillis()));
        Comparator<ScoreRecord> comparator = buildComparator();

        // Keep up to MAX_RECORDS per difficulty to avoid one mode squeezing out others.
        List<ScoreRecord> limitedRecords = new ArrayList<>();
        for (Difficulty mode : Difficulty.values()) {
            List<ScoreRecord> perMode = new ArrayList<>();
            for (ScoreRecord record : records) {
                if (record.getDifficulty() == mode) {
                    perMode.add(record);
                }
            }
            perMode.sort(comparator);
            if (perMode.size() > MAX_RECORDS) {
                perMode = new ArrayList<>(perMode.subList(0, MAX_RECORDS));
            }
            limitedRecords.addAll(perMode);
        }

        limitedRecords.sort(comparator);
        persistScores(limitedRecords);
    }

    public void clearScores() {
        sharedPreferences.edit().remove(KEY_SCORES).apply();
    }

    public void clearScoresByDifficulty(Difficulty difficulty) {
        List<ScoreRecord> records = loadAllScoresInternal();
        List<ScoreRecord> keptRecords = new ArrayList<>();
        for (ScoreRecord record : records) {
            if (record.getDifficulty() != difficulty) {
                keptRecords.add(record);
            }
        }
        persistScores(keptRecords);
    }

    public boolean deleteScoreRecord(ScoreRecord targetRecord) {
        if (targetRecord == null) {
            return false;
        }

        List<ScoreRecord> records = loadAllScoresInternal();
        List<ScoreRecord> keptRecords = new ArrayList<>();
        boolean deleted = false;
        for (ScoreRecord record : records) {
            if (!deleted && isSameRecord(record, targetRecord)) {
                deleted = true;
                continue;
            }
            keptRecords.add(record);
        }

        if (!deleted) {
            return false;
        }
        persistScores(keptRecords);
        return true;
    }

    private List<ScoreRecord> loadAllScoresInternal() {
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

    private void persistScores(List<ScoreRecord> records) {
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

    private boolean isSameRecord(ScoreRecord left, ScoreRecord right) {
        return left.getCreatedAt() == right.getCreatedAt()
                && left.getScore() == right.getScore()
                && left.getDurationSeconds() == right.getDurationSeconds()
                && left.getDifficulty() == right.getDifficulty()
                && left.getPlayerName().equals(right.getPlayerName());
    }

    private Comparator<ScoreRecord> buildComparator() {
        return Comparator
                .comparingInt(ScoreRecord::getScore).reversed()
                .thenComparing(Comparator.comparingLong(ScoreRecord::getCreatedAt).reversed());
    }
}
