package edu.hitsz.aircraftwar.game;

public class GameConfig {

    // Normalized joystick dead zone in [0, 1).
    // Increase this value if you want the plane to ignore more small thumb movements.
    private static final float JOYSTICK_DEAD_ZONE_RATIO = 0.18f;

    private final Difficulty difficulty;
    private final int screenWidth;
    private final int screenHeight;
    private final float scale;

    public GameConfig(Difficulty difficulty, int screenWidth, int screenHeight) {
        this.difficulty = difficulty;
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        this.scale = Math.min(this.screenHeight / 768f, 2.0f);
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public int getHeroWidth() {
        return Math.max(78, Math.round(screenWidth * 0.14f));
    }

    public int getHeroHeight() {
        return Math.round(getHeroWidth() * 0.88f);
    }

    public int getMobEnemyWidth() {
        return Math.max(62, Math.round(screenWidth * 0.12f));
    }

    public int getMobEnemyHeight() {
        return Math.round(getMobEnemyWidth() * 0.92f);
    }

    public int getEliteEnemyWidth() {
        return Math.max(68, Math.round(screenWidth * 0.13f));
    }

    public int getEliteEnemyHeight() {
        return Math.round(getEliteEnemyWidth() * 0.95f);
    }

    public int getElitePlusEnemyWidth() {
        return Math.max(72, Math.round(screenWidth * 0.135f));
    }

    public int getElitePlusEnemyHeight() {
        return Math.round(getElitePlusEnemyWidth() * 0.95f);
    }

    public int getBossEnemyWidth() {
        return Math.max(180, Math.round(screenWidth * 0.28f));
    }

    public int getBossEnemyHeight() {
        return Math.round(getBossEnemyWidth() * 0.74f);
    }

    public int getHeroBulletWidth() {
        return Math.max(10, Math.round(screenWidth * 0.018f));
    }

    public int getHeroBulletHeight() {
        return Math.max(24, Math.round(getHeroBulletWidth() * 2.4f));
    }

    public int getEnemyBulletWidth() {
        return getHeroBulletWidth();
    }

    public int getEnemyBulletHeight() {
        return getHeroBulletHeight();
    }

    public int getPropWidth() {
        return Math.max(54, Math.round(screenWidth * 0.1f));
    }

    public int getPropHeight() {
        return getPropWidth();
    }

    public float getHeroSpawnX() {
        return screenWidth * 0.5f;
    }

    public float getHeroSpawnY() {
        return screenHeight - getHeroHeight() * 1.25f;
    }

    public float getSpawnTopLimit() {
        return Math.max(getBossEnemyHeight() * 0.5f, screenHeight * 0.12f);
    }

    public float getBackgroundScrollSpeed() {
        return 2.2f * scale;
    }

    public float getMobEnemySpeedY() {
        return 5.0f * scale;
    }

    public float getEliteEnemySpeedY() {
        return 6.5f * scale;
    }

    public float getElitePlusEnemySpeedY() {
        return 6.0f * scale;
    }

    public float getElitePlusEnemySpeedX() {
        return 3.0f * scale;
    }

    public float getBossEnemySpeedX() {
        return 4.0f * scale;
    }

    public float getHeroBulletSpeedY() {
        return 14.0f * scale;
    }

    public float getHeroMoveSpeed() {
        return Math.max(8.0f * scale, screenWidth * 0.014f);
    }

    public float getJoystickDeadZoneRatio() {
        return JOYSTICK_DEAD_ZONE_RATIO;
    }

    public float getEnemyBulletSpeedY() {
        return 8.5f * scale;
    }

    public float getScatterBulletSpeedX() {
        return 4.5f * scale;
    }

    public float getCircleBulletSpeed() {
        return 8.0f * scale;
    }

    public float getPropSpeedY() {
        return 4.2f * scale;
    }

    public int getHeroMaxHp() {
        return 1000;
    }

    public int getMobEnemyHp() {
        return 30;
    }

    public int getEliteEnemyHp() {
        return 60;
    }

    public int getElitePlusEnemyHp() {
        return 80;
    }

    public int getBossEnemyHp() {
        return 300;
    }

    public int getBossScoreStep() {
        return 200;
    }

    public int getEnemyMaxNumber() {
        switch (difficulty) {
            case EASY:
                return 4;
            case HARD:
                return 6;
            default:
                return 5;
        }
    }

    public long getCycleDurationMs() {
        switch (difficulty) {
            case EASY:
                return 640L;
            case HARD:
                return 500L;
            default:
                return 560L;
        }
    }

    public long getPropEffectDurationMs() {
        return 3000L;
    }

    public int getBaseEnemyScore() {
        return 10;
    }

    public int getBossScore() {
        return 50;
    }
}
