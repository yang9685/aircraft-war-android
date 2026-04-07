package edu.hitsz.aircraftwar.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import edu.hitsz.aircraftwar.game.Difficulty;

public class ScoreRecord {

    private static final String KEY_PLAYER_NAME = "player_name";
    private static final String KEY_SCORE = "score";
    private static final String KEY_DURATION_SECONDS = "duration_seconds";
    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_CREATED_AT = "created_at";

    private final String playerName;
    private final int score;
    private final long durationSeconds;
    private final Difficulty difficulty;
    private final long createdAt;

    public ScoreRecord(String playerName, int score, long durationSeconds, Difficulty difficulty, long createdAt) {
        this.playerName = playerName;
        this.score = score;
        this.durationSeconds = durationSeconds;
        this.difficulty = difficulty;
        this.createdAt = createdAt;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getScore() {
        return score;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put(KEY_PLAYER_NAME, playerName);
        object.put(KEY_SCORE, score);
        object.put(KEY_DURATION_SECONDS, durationSeconds);
        object.put(KEY_DIFFICULTY, difficulty.name());
        object.put(KEY_CREATED_AT, createdAt);
        return object;
    }

    public static ScoreRecord fromJson(JSONObject object) throws JSONException {
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(object.optString(KEY_DIFFICULTY, Difficulty.NORMAL.name()));
        } catch (IllegalArgumentException exception) {
            difficulty = Difficulty.NORMAL;
        }
        return new ScoreRecord(
                object.optString(KEY_PLAYER_NAME, "Player"),
                object.optInt(KEY_SCORE, 0),
                object.optLong(KEY_DURATION_SECONDS, 0L),
                difficulty,
                object.optLong(KEY_CREATED_AT, System.currentTimeMillis()));
    }

    public String toDisplayLine(int rank) {
        String timeText = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(createdAt));
        return rank + ". " + playerName
                + "  Score: " + score
                + "  Time: " + durationSeconds + "s"
                + "  Mode: " + difficulty.name()
                + "  At: " + timeText;
    }
}
