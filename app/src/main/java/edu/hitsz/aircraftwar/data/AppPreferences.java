package edu.hitsz.aircraftwar.data;

import android.content.Context;
import android.content.SharedPreferences;

import edu.hitsz.aircraftwar.game.Difficulty;

public final class AppPreferences {

    private static final String PREFS_NAME = "aircraft_war_prefs";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_LAST_DIFFICULTY = "last_difficulty";

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

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
