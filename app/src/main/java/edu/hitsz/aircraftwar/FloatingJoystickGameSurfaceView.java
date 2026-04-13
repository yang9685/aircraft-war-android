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
import edu.hitsz.aircraftwar.game.model.HeroAircraft;

public class FloatingJoystickGameSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

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
    private boolean heroDragActive;
    private int heroDragPointerId = MotionEvent.INVALID_POINTER_ID;
    private float heroDragOffsetX;
    private float heroDragOffsetY;
    private long gameOverNotifyAtMs;

    private SpriteStore spriteStore;
    private GameConfig gameConfig;
    private GameEngine gameEngine;

    public FloatingJoystickGameSurfaceView(
            Context context,
            Difficulty difficulty,
            GameSessionListener gameSessionListener,
            SoundManager soundManager) {
        super(context);
        this.difficulty = difficulty;
        this.gameSessionListener = gameSessionListener;
        this.soundManager = soundManager;
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        textPrimaryColor = ContextCompat.getColor(context, R.color.text_primary);
        textSecondaryColor = ContextCompat.getColor(context, R.color.text_secondary);
        textMutedColor = ContextCompat.getColor(context, R.color.text_muted);
        healthGoodColor = ContextCompat.getColor(context, R.color.health_good);
        healthMidColor = ContextCompat.getColor(context, R.color.health_mid);
        healthLowColor = ContextCompat.getColor(context, R.color.health_low);
        surfaceColor = ContextCompat.getColor(context, R.color.surface);
        strokeColor = ContextCompat.getColor(context, R.color.stroke);
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
            resetHeroDrag();
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
                case MotionEvent.ACTION_POINTER_DOWN:
                    handleHeroDragStart(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateHeroDragFromEvent(event);
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handleHeroDragEnd(event);
                    return true;
                default:
                    return true;
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
        synchronized (gameStateLock) {
            resetHeroDrag();
        }
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
        resetHeroDrag();
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
        float panelHeight = dp(88f);
        float infoPanelWidth = Math.min(screenWidth * 0.4f, dp(190f));
        float hpPanelWidth = Math.min(screenWidth * 0.44f, dp(214f));
        float radius = dp(20f);

        primaryRect.set(margin, margin, margin + infoPanelWidth, margin + panelHeight);
        drawPanel(canvas, primaryRect, radius);
        float infoTextX = primaryRect.left + dp(14f);
        canvas.drawText("\u5f97\u5206", infoTextX, primaryRect.top + dp(24f), hudLabelPaint);
        canvas.drawText(String.valueOf(gameEngine.getScore()), infoTextX, primaryRect.top + dp(54f), hudValuePaint);
        canvas.drawText(
                "\u65f6\u95f4 " + UiText.formatDuration(gameEngine.getElapsedMs() / 1000L),
                infoTextX,
                primaryRect.top + dp(80f),
                hudMetaPaint);

        primaryRect.set(screenWidth - margin - hpPanelWidth, margin, screenWidth - margin, margin + panelHeight);
        drawPanel(canvas, primaryRect, radius);
        float hpLabelX = primaryRect.left + dp(14f);
        canvas.drawText("\u751f\u547d", hpLabelX, primaryRect.top + dp(24f), hudLabelPaint);

        secondaryRect.set(
                hpLabelX,
                primaryRect.top + dp(42f),
                primaryRect.right - dp(14f),
                primaryRect.top + dp(70f));
        drawProgressBar(
                canvas,
                secondaryRect,
                gameEngine.getHeroAircraft().getHp(),
                gameEngine.getHeroAircraft().getMaxHp(),
                gameEngine.getHeroAircraft().getHp() + " / " + gameEngine.getHeroAircraft().getMaxHp());
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
        float panelWidth = Math.min(screenWidth - dp(56f), dp(260f));
        float panelHeight = dp(108f);
        float left = (screenWidth - panelWidth) * 0.5f;
        float top = screenHeight * 0.5f - panelHeight * 0.5f;
        primaryRect.set(left, top, left + panelWidth, top + panelHeight);
        drawPanel(canvas, primaryRect, dp(28f));

        float titleY = top + dp(44f);
        canvas.drawText("\u4f5c\u6218\u7ed3\u675f", screenWidth * 0.5f, titleY, overlayTextPaint);
        canvas.drawText("\u6218\u62a5\u751f\u6210\u4e2d", screenWidth * 0.5f, titleY + dp(30f), overlaySubTextPaint);
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

    private void handleHeroDragStart(MotionEvent event) {
        int actionIndex = event.getActionIndex();
        float touchX = event.getX(actionIndex);
        float touchY = event.getY(actionIndex);
        if (heroDragActive) {
            return;
        }
        if (!isTouchOnHero(touchX, touchY)) {
            return;
        }
        HeroAircraft heroAircraft = gameEngine.getHeroAircraft();
        heroDragActive = true;
        heroDragPointerId = event.getPointerId(actionIndex);
        heroDragOffsetX = heroAircraft.getLocationX() - touchX;
        heroDragOffsetY = heroAircraft.getLocationY() - touchY;
    }

    private void updateHeroDragFromEvent(MotionEvent event) {
        if (heroDragPointerId == MotionEvent.INVALID_POINTER_ID) {
            return;
        }
        int pointerIndex = event.findPointerIndex(heroDragPointerId);
        if (pointerIndex < 0) {
            resetHeroDrag();
            return;
        }
        float touchX = event.getX(pointerIndex);
        float touchY = event.getY(pointerIndex);
        gameEngine.moveHeroTo(touchX + heroDragOffsetX, touchY + heroDragOffsetY);
    }

    private void handleHeroDragEnd(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            resetHeroDrag();
            return;
        }
        int actionIndex = event.getActionIndex();
        if (event.getPointerId(actionIndex) == heroDragPointerId) {
            resetHeroDrag();
        }
    }

    private boolean isTouchOnHero(float touchX, float touchY) {
        HeroAircraft heroAircraft = gameEngine.getHeroAircraft();
        if (heroAircraft == null || heroAircraft.notValid()) {
            return false;
        }
        float halfWidth = heroAircraft.getWidth() * 0.5f;
        float halfHeight = heroAircraft.getHeight() * 0.5f;
        return touchX >= heroAircraft.getLocationX() - halfWidth
                && touchX <= heroAircraft.getLocationX() + halfWidth
                && touchY >= heroAircraft.getLocationY() - halfHeight
                && touchY <= heroAircraft.getLocationY() + halfHeight;
    }

    private void resetHeroDrag() {
        heroDragActive = false;
        heroDragPointerId = MotionEvent.INVALID_POINTER_ID;
        heroDragOffsetX = 0f;
        heroDragOffsetY = 0f;
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
