package edu.hitsz.aircraftwar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.List;

import edu.hitsz.aircraftwar.audio.SoundEffect;
import edu.hitsz.aircraftwar.audio.SoundManager;
import edu.hitsz.aircraftwar.game.Difficulty;
import edu.hitsz.aircraftwar.game.GameConfig;
import edu.hitsz.aircraftwar.game.GameEngine;
import edu.hitsz.aircraftwar.game.SpriteStore;
import edu.hitsz.aircraftwar.game.model.AbstractFlyingObject;

public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    public interface GameSessionListener {
        void onGameOver(int score, long durationSeconds, Difficulty difficulty);
    }

    private static final long FRAME_DELAY_MS = 16L;

    private final SurfaceHolder surfaceHolder;
    private final Object gameStateLock = new Object();
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint();
    private final Paint overlayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Difficulty difficulty;
    private final GameSessionListener gameSessionListener;
    private final SoundManager soundManager;

    private Thread renderThread;
    private volatile boolean running;

    private int screenWidth = 1;
    private int screenHeight = 1;
    private float backgroundOffsetY;
    private long lastFrameTimeMs;

    private SpriteStore spriteStore;
    private GameConfig gameConfig;
    private GameEngine gameEngine;
    private boolean gameOverNotified;

    public GameSurfaceView(
            Context context,
            Difficulty difficulty,
            GameSessionListener gameSessionListener,
            SoundManager soundManager) {
        super(context);
        this.difficulty = difficulty;
        this.gameSessionListener = gameSessionListener;
        this.soundManager = soundManager;
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        setFocusable(true);

        hudPaint.setColor(Color.WHITE);
        hudPaint.setTextSize(42f);

        overlayPaint.setColor(0xB0000000);
        overlayTextPaint.setColor(Color.WHITE);
        overlayTextPaint.setTextSize(64f);
        overlayTextPaint.setTextAlign(Paint.Align.CENTER);
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
            gameOverNotified = false;
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
                        soundManager.playEffect(soundEffect);
                    }
                    if (gameEngine.isGameOver() && !gameOverNotified) {
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
                    drawGameOverOverlay(canvas);
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
        canvas.drawText("SCORE: " + gameEngine.getScore(), 24f, 56f, hudPaint);
        canvas.drawText("LIFE: " + gameEngine.getHeroAircraft().getHp(), 24f, 108f, hudPaint);
        canvas.drawText("TIME: " + (gameEngine.getElapsedMs() / 1000) + "s", 24f, 160f, hudPaint);
    }

    private void drawGameOverOverlay(Canvas canvas) {
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, overlayPaint);
        canvas.drawText("GAME OVER", screenWidth * 0.5f, screenHeight * 0.48f, overlayTextPaint);
        canvas.drawText("Saving Result...", screenWidth * 0.5f, screenHeight * 0.56f, overlayTextPaint);
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
