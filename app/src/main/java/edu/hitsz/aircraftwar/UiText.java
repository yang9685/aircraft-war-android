package edu.hitsz.aircraftwar;

import android.content.Context;

import java.util.Locale;

import edu.hitsz.aircraftwar.game.Difficulty;

public final class UiText {

    private UiText() {
    }

    public static String getDifficultyLabel(Context context, Difficulty difficulty) {
        return context.getString(getDifficultyLabelRes(difficulty));
    }

    public static String formatDuration(long durationSeconds) {
        long hours = durationSeconds / 3600L;
        long minutes = (durationSeconds % 3600L) / 60L;
        long seconds = durationSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private static int getDifficultyLabelRes(Difficulty difficulty) {
        switch (difficulty) {
            case EASY:
                return R.string.difficulty_easy;
            case HARD:
                return R.string.difficulty_hard;
            case NORMAL:
            default:
                return R.string.difficulty_normal;
        }
    }
}
