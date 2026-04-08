package edu.hitsz.aircraftwar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;

import edu.hitsz.aircraftwar.audio.SoundEffect;
import edu.hitsz.aircraftwar.audio.SoundManager;
import edu.hitsz.aircraftwar.game.Difficulty;
import edu.hitsz.aircraftwar.game.GameConfig;
import edu.hitsz.aircraftwar.game.GameEngine;
import edu.hitsz.aircraftwar.game.SpriteStore;
import edu.hitsz.aircraftwar.game.model.AbstractFlyingObject;
import edu.hitsz.aircraftwar.game.model.BossEnemy;

public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    public interface GameSessionListener {
        void onGameOver(int score, long durationSeconds, Difficulty difficulty);
    }

    private static final long FRAME_DELAY_MS = 16L;
    private static final long BOMB_FLASH_DURATION_MS = 220L;
    private static final long GAME_OVER_FLASH_DURATION_MS = 520L;

    private final SurfaceHolder surfaceHolder;
    private final Object gameStateLock = new Object();
    private final Paint hudPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudMetaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bossLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bossTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bossFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlaySubTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint();
    private final RectF primaryRect = new RectF();
    private final RectF secondaryRect = new RectF();
    private final Difficulty difficulty;
    private final GameSessionListener gameSessionListener;
    private final SoundManager soundManager;
    private final float density;
    private final float scaledDensity;
    private final int textPrimaryColor;
    private final int textSecondaryColor;
    private final int textMutedColor;
    private final int healthGoodColor;
    private final int healthMidColor;
    private final int healthLowColor;
    private final int warningColor;
    private final int surfaceColor;
    private final int strokeColor;

    private Thread renderThread;
    private volatile boolean running;

    private int screenWidth = 1;
    private int screenHeight = 1;
    private float backgroundOffsetY;
    private long lastFrameTimeMs;
    private long flashStartTimeMs = -1L;
    private long flashDurationMs;
    private int flashPeakAlpha;
    private boolean gameOverSequenceStarted;
    private boolean gameOverNotified;
    private boolean bgmSynced;
    private boolean bossMusicActive;
    private long gameOverNotifyAtMs;

    private SpriteStore spriteStore;
    private GameConfig gameConfig;
    private GameEngine gameEngine;

    public GameSurfaceView(
            Context context,
            Difficulty difficulty,
            GameSessionListener gameSessionListener,
            SoundManager soundManager) {
        super(context);
        this.difficulty = difficulty;
        this.gameSessionListener = gameSessionListener;
        this.soundManager = soundManager;
        this.density = getResources().getDisplayMetrics().density;
        this.scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        this.textPrimaryColor = ContextCompat.getColor(context, R.color.text_primary);
        this.textSecondaryColor = ContextCompat.getColor(context, R.color.text_secondary);
        this.textMutedColor = ContextCompat.getColor(context, R.color.text_muted);
        this.healthGoodColor = ContextCompat.getColor(context, R.color.health_good);
        this.healthMidColor = ContextCompat.getColor(context, R.color.health_mid);
        this.healthLowColor = ContextCompat.getColor(context, R.color.health_low);
        this.warningColor = ContextCompat.getColor(context, R.color.warning);
        this.surfaceColor = ContextCompat.getColor(context, R.color.surface);
        this.strokeColor = ContextCompat.getColor(context, R.color.stroke);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        setFocusable(true);

        hudPanelPaint.setColor(surfaceColor);
        hudStrokePaint.setColor(strokeColor);
        hudStrokePaint.setStyle(Paint.Style.STROKE);
        hudStrokePaint.setStrokeWidth(dp(1.2f));

        hudLabelPaint.setColor(textSecondaryColor);
        hudLabelPaint.setFakeBoldText(true);
        hudLabelPaint.setTextSize(sp(12f));

        hudValuePaint.setColor(textPrimaryColor);
        hudValuePaint.setFakeBoldText(true);
        hudValuePaint.setTextSize(sp(24f));

        hudMetaPaint.setColor(textMutedColor);
        hudMetaPaint.setTextSize(sp(13f));

        barTrackPaint.setColor(0x6632485A);
        barTextPaint.setColor(textPrimaryColor);
        barTextPaint.setTextAlign(Paint.Align.CENTER);
        barTextPaint.setTextSize(sp(11f));
        barTextPaint.setFakeBoldText(true);

        chipPaint.setColor(ContextCompat.getColor(context, R.color.surface_chip));
        chipTextPaint.setColor(textPrimaryColor);
        chipTextPaint.setTextAlign(Paint.Align.CENTER);
        chipTextPaint.setTextSize(sp(12f));
        chipTextPaint.setFakeBoldText(true);

        bossLabelPaint.setColor(warningColor);
        bossLabelPaint.setTextAlign(Paint.Align.CENTER);
        bossLabelPaint.setTextSize(sp(13f));
        bossLabelPaint.setFakeBoldText(true);
        bossTrackPaint.setColor(0x66354A5D);
        bossFillPaint.setColor(ContextCompat.getColor(context, R.color.danger));

        overlayPaint.setColor(0x99101821);
        overlayTextPaint.setColor(textPrimaryColor);
        overlayTextPaint.setTextAlign(Paint.Align.CENTER);
        overlayTextPaint.setTextSize(sp(28f));
        overlayTextPaint.setFakeBoldText(true);

        overlaySubTextPaint.setColor(textSecondaryColor);
        overlaySubTextPaint.setTextAlign(Paint.Align.CENTER);
        overlaySubTextPaint.setTextSize(sp(15f));

        flashPaint.setColor(Color.WHITE);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (gameEngine != null) {
            startLoop();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        synchronized (gameStateLock) {
            screenWidth = Math.max(1, width);
            screenHeight = Math.max(1, height);
            spriteStore = new SpriteStore(getResources());
            gameConfig = new GameConfig(difficulty, screenWidth, screenHeight);
            gameEngine = new GameEngine(gameConfig);
            backgroundOffsetY = 0f;
            flashStartTimeMs = -1L;
            gameOverSequenceStarted = false;
            gameOverNotified = false;
            bgmSynced = false;
            bossMusicActive = false;
        }
        startLoop();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopLoop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        synchronized (gameStateLock) {
            if (gameEngine == null) {
                return super.onTouchEvent(event);
            }
            if (gameEngine.isGameOver()) {
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    gameEngine.moveHeroTo(event.getX(), event.getY());
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }
    }

    @Override
    public void run() {
        lastFrameTimeMs = SystemClock.uptimeMillis();
        while (running) {
            long now = SystemClock.uptimeMillis();
            long deltaMs = Math.max(1L, Math.min(40L, now - lastFrameTimeMs));
            lastFrameTimeMs = now;
            boolean shouldNotifyGameOver = false;
            int gameOverScore = 0;
            long gameOverDurationSeconds = 0L;

            synchronized (gameStateLock) {
                if (gameEngine != null) {
                    gameEngine.update(deltaMs);
                    for (SoundEffect soundEffect : gameEngine.drainSoundEffects()) {
                        handleSoundEffect(soundEffect, now);
                    }
                    if (gameEngine.isGameOver() && !gameOverSequenceStarted) {
                        startGameOverSequence(now);
                    }
                    if (!gameEngine.isGameOver()) {
                        syncBattleBgm();
                    }
                    if (gameOverSequenceStarted && !gameOverNotified && now >= gameOverNotifyAtMs) {
                        gameOverNotified = true;
                        shouldNotifyGameOver = true;
                        gameOverScore = gameEngine.getScore();
                        gameOverDurationSeconds = gameEngine.getElapsedMs() / 1000L;
                    }
                }
            }

            if (shouldNotifyGameOver && gameSessionListener != null) {
                final int score = gameOverScore;
                final long durationSeconds = gameOverDurationSeconds;
                post(() -> gameSessionListener.onGameOver(score, durationSeconds, difficulty));
            }

            drawFrame();
            try {
                Thread.sleep(FRAME_DELAY_MS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void onHostPause() {
        stopLoop();
    }

    public void onHostResume() {
        if (surfaceHolder.getSurface().isValid() && gameEngine != null) {
            startLoop();
        }
    }

    private void handleSoundEffect(SoundEffect soundEffect, long now) {
        if (soundEffect == SoundEffect.BOMB_EXPLOSION) {
            triggerFlash(175, BOMB_FLASH_DURATION_MS, now);
        } else if (soundEffect == SoundEffect.GAME_OVER) {
            soundManager.stopBgm();
            bgmSynced = false;
            bossMusicActive = false;
            startGameOverSequence(now);
        }
        soundManager.playEffect(soundEffect);
    }

    private void startGameOverSequence(long now) {
        if (gameOverSequenceStarted) {
            return;
        }
        gameOverSequenceStarted = true;
        gameOverNotifyAtMs = now + GAME_OVER_FLASH_DURATION_MS;
        triggerFlash(255, GAME_OVER_FLASH_DURATION_MS, now);
    }

    private void syncBattleBgm() {
        if (gameEngine == null) {
            return;
        }
        boolean shouldUseBossBgm = gameEngine.isBossBattleActive();
        if (bgmSynced && shouldUseBossBgm == bossMusicActive) {
            return;
        }
        if (shouldUseBossBgm) {
            soundManager.playBossBgm();
        } else {
            soundManager.playGameBgm();
        }
        bossMusicActive = shouldUseBossBgm;
        bgmSynced = true;
    }

    private void drawFrame() {
        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }

        try {
            synchronized (gameStateLock) {
                if (gameEngine == null || spriteStore == null || gameConfig == null) {
                    canvas.drawColor(Color.BLACK);
                    return;
                }
                drawScrollingBackground(canvas);
                drawObjectList(canvas, gameEngine.getEnemyBullets());
                drawObjectList(canvas, gameEngine.getProps());
                drawObjectList(canvas, gameEngine.getHeroBullets());
                drawObjectList(canvas, gameEngine.getEnemyAircrafts());
                drawObject(canvas, gameEngine.getHeroAircraft());
                drawHud(canvas);
                if (gameEngine.isGameOver()) {
                    drawGameOverBackdrop(canvas);
                }
                drawFlashOverlay(canvas, SystemClock.uptimeMillis());
                if (gameEngine.isGameOver()) {
                    drawGameOverText(canvas);
                }
            }
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawScrollingBackground(Canvas canvas) {
        Bitmap backgroundBitmap = spriteStore.get(gameEngine.getBackgroundSpriteType(), screenWidth, screenHeight);
        float backgroundHeight = backgroundBitmap.getHeight();
        backgroundOffsetY += gameConfig.getBackgroundScrollSpeed();
        if (backgroundOffsetY >= screenHeight) {
            backgroundOffsetY = 0f;
        }

        canvas.drawBitmap(backgroundBitmap, 0f, backgroundOffsetY - backgroundHeight, null);
        canvas.drawBitmap(backgroundBitmap, 0f, backgroundOffsetY, null);
    }

    private void drawObjectList(Canvas canvas, List<? extends AbstractFlyingObject> objects) {
        for (AbstractFlyingObject object : objects) {
            drawObject(canvas, object);
        }
    }

    private void drawObject(Canvas canvas, AbstractFlyingObject object) {
        if (object == null || object.notValid()) {
            return;
        }
        Bitmap bitmap = spriteStore.get(object.getSpriteType(), object.getWidth(), object.getHeight());
        float left = object.getLocationX() - object.getWidth() * 0.5f;
        float top = object.getLocationY() - object.getHeight() * 0.5f;
        canvas.drawBitmap(bitmap, left, top, null);
    }

    private void drawHud(Canvas canvas) {
        float margin = dp(16f);
        float panelHeight = dp(94f);
        float infoPanelWidth = Math.min(screenWidth * 0.4f, dp(190f));
        float hpPanelWidth = Math.min(screenWidth * 0.44f, dp(214f));
        float radius = dp(20f);

        primaryRect.set(margin, margin, margin + infoPanelWidth, margin + panelHeight);
        drawPanel(canvas, primaryRect, radius);
        float infoTextX = primaryRect.left + dp(14f);
        canvas.drawText(getResources().getString(R.string.hud_score_label), infoTextX, primaryRect.top + dp(24f), hudLabelPaint);
        canvas.drawText(String.valueOf(gameEngine.getScore()), infoTextX, primaryRect.top + dp(54f), hudValuePaint);
        canvas.drawText(
                getResources().getString(R.string.hud_time_label) + " " + UiText.formatDuration(gameEngine.getElapsedMs() / 1000L),
                infoTextX,
                primaryRect.top + dp(80f),
                hudMetaPaint);

        primaryRect.set(screenWidth - margin - hpPanelWidth, margin, screenWidth - margin, margin + panelHeight);
        drawPanel(canvas, primaryRect, radius);
        float hpLabelX = primaryRect.left + dp(14f);
        canvas.drawText(getResources().getString(R.string.hud_hp_label), hpLabelX, primaryRect.top + dp(24f), hudLabelPaint);

        String difficultyText = UiText.getDifficultyLabel(getContext(), difficulty);
        float chipWidth = chipTextPaint.measureText(difficultyText) + dp(24f);
        secondaryRect.set(
                primaryRect.right - dp(14f) - chipWidth,
                primaryRect.top + dp(10f),
                primaryRect.right - dp(14f),
                primaryRect.top + dp(36f));
        canvas.drawRoundRect(secondaryRect, dp(13f), dp(13f), chipPaint);
        canvas.drawText(difficultyText, secondaryRect.centerX(), centeredTextY(secondaryRect, chipTextPaint), chipTextPaint);

        secondaryRect.set(
                hpLabelX,
                primaryRect.top + dp(46f),
                primaryRect.right - dp(14f),
                primaryRect.top + dp(68f));
        drawProgressBar(
                canvas,
                secondaryRect,
                gameEngine.getHeroAircraft().getHp(),
                gameEngine.getHeroAircraft().getMaxHp(),
                getResources().getString(
                        R.string.hud_hp_template,
                        gameEngine.getHeroAircraft().getHp(),
                        gameEngine.getHeroAircraft().getMaxHp()));

        canvas.drawText(
                getResources().getString(R.string.hud_mode_template, difficultyText),
                hpLabelX,
                primaryRect.top + dp(84f),
                hudMetaPaint);

        BossEnemy aliveBoss = gameEngine.getAliveBoss();
        if (aliveBoss != null) {
            drawBossBar(canvas, aliveBoss);
        }
    }

    private void drawBossBar(Canvas canvas, BossEnemy aliveBoss) {
        float top = dp(126f);
        float width = Math.min(screenWidth - dp(64f), dp(300f));
        float left = (screenWidth - width) * 0.5f;

        canvas.drawText(getResources().getString(R.string.hud_boss_label), screenWidth * 0.5f, top, bossLabelPaint);

        secondaryRect.set(left, top + dp(12f), left + width, top + dp(28f));
        canvas.drawRoundRect(secondaryRect, dp(10f), dp(10f), bossTrackPaint);

        float ratio = aliveBoss.getMaxHp() == 0 ? 0f : aliveBoss.getHp() / (float) aliveBoss.getMaxHp();
        if (ratio > 0f) {
            primaryRect.set(
                    secondaryRect.left,
                    secondaryRect.top,
                    secondaryRect.left + secondaryRect.width() * ratio,
                    secondaryRect.bottom);
            canvas.drawRoundRect(primaryRect, dp(10f), dp(10f), bossFillPaint);
        }
    }

    private void drawProgressBar(Canvas canvas, RectF rect, int currentValue, int maxValue, String text) {
        canvas.drawRoundRect(rect, dp(12f), dp(12f), barTrackPaint);
        if (maxValue > 0 && currentValue > 0) {
            float ratio = Math.max(0f, Math.min(1f, currentValue / (float) maxValue));
            barFillPaint.setColor(resolveHpColor(ratio));
            primaryRect.set(rect.left, rect.top, rect.left + rect.width() * ratio, rect.bottom);
            canvas.drawRoundRect(primaryRect, dp(12f), dp(12f), barFillPaint);
        }
        canvas.drawText(text, rect.centerX(), centeredTextY(rect, barTextPaint), barTextPaint);
    }

    private void drawPanel(Canvas canvas, RectF rect, float radius) {
        canvas.drawRoundRect(rect, radius, radius, hudPanelPaint);
        canvas.drawRoundRect(rect, radius, radius, hudStrokePaint);
    }

    private void drawGameOverBackdrop(Canvas canvas) {
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, overlayPaint);
    }

    private void drawGameOverText(Canvas canvas) {
        float titleY = screenHeight * 0.48f;
        canvas.drawText(
                getResources().getString(R.string.game_over_overlay_title),
                screenWidth * 0.5f,
                titleY,
                overlayTextPaint);
        canvas.drawText(
                getResources().getString(R.string.game_over_overlay_subtitle),
                screenWidth * 0.5f,
                titleY + dp(34f),
                overlaySubTextPaint);
    }

    private void drawFlashOverlay(Canvas canvas, long now) {
        int flashAlpha = getFlashAlpha(now);
        if (flashAlpha <= 0) {
            return;
        }
        flashPaint.setAlpha(flashAlpha);
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, flashPaint);
    }

    private int getFlashAlpha(long now) {
        if (flashStartTimeMs < 0L || flashDurationMs <= 0L) {
            return 0;
        }
        long elapsed = now - flashStartTimeMs;
        if (elapsed >= flashDurationMs) {
            flashStartTimeMs = -1L;
            return 0;
        }
        float progress = elapsed / (float) flashDurationMs;
        return Math.max(0, Math.round(flashPeakAlpha * (1f - progress)));
    }

    private void triggerFlash(int peakAlpha, long durationMs, long now) {
        flashStartTimeMs = now;
        flashPeakAlpha = peakAlpha;
        flashDurationMs = durationMs;
    }

    private float centeredTextY(RectF rect, Paint paint) {
        return rect.centerY() - (paint.ascent() + paint.descent()) * 0.5f;
    }

    private int resolveHpColor(float ratio) {
        if (ratio > 0.55f) {
            return healthGoodColor;
        }
        if (ratio > 0.25f) {
            return healthMidColor;
        }
        return healthLowColor;
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }

    private synchronized void startLoop() {
        if (running) {
            return;
        }
        running = true;
        renderThread = new Thread(this, "aircraft-war-render");
        renderThread.start();
    }

    private synchronized void stopLoop() {
        running = false;
        if (renderThread == null) {
            return;
        }

        try {
            renderThread.join(500L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            renderThread = null;
        }
    }
}
