package edu.hitsz.aircraftwar.data;

import android.content.Context;
import android.content.SharedPreferences;

import edu.hitsz.aircraftwar.game.Difficulty;

public final class AppPreferences {

    private static final String PREFS_NAME = "aircraft_war_prefs";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_LAST_DIFFICULTY = "last_difficulty";
    private static final String KEY_MATCH_HOST = "match_host";
    private static final String KEY_MATCH_PORT = "match_port";
    private static final String KEY_PLAYER_NAME = "player_name";

    private AppPreferences() {
    }

    public static boolean isSoundEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    public static Difficulty getLastDifficulty(Context context) {
        String value = getPrefs(context).getString(KEY_LAST_DIFFICULTY, Difficulty.NORMAL.name());
        try {
            return Difficulty.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return Difficulty.NORMAL;
        }
    }

    public static void setLastDifficulty(Context context, Difficulty difficulty) {
        getPrefs(context).edit().putString(KEY_LAST_DIFFICULTY, difficulty.name()).apply();
    }

    public static String getMatchHost(Context context) {
        return getPrefs(context).getString(KEY_MATCH_HOST, "10.0.2.2");
    }

    public static void setMatchHost(Context context, String host) {
        getPrefs(context).edit().putString(KEY_MATCH_HOST, host).apply();
    }

    public static int getMatchPort(Context context) {
        return getPrefs(context).getInt(KEY_MATCH_PORT, 9999);
    }

    public static void setMatchPort(Context context, int port) {
        getPrefs(context).edit().putInt(KEY_MATCH_PORT, port).apply();
    }

    public static String getPlayerName(Context context) {
        return getPrefs(context).getString(KEY_PLAYER_NAME, "");
    }

    public static void setPlayerName(Context context, String playerName) {
        getPrefs(context).edit().putString(KEY_PLAYER_NAME, playerName).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
