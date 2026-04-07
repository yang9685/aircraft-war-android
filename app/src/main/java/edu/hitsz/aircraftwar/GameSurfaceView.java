package edu.hitsz.aircraftwar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final long FRAME_DELAY_MS = 16L;
    private static final float HERO_BOTTOM_MARGIN_RATIO = 0.14f;

    private final SurfaceHolder surfaceHolder;
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Thread renderThread;
    private volatile boolean running;

    private Bitmap backgroundBitmap;
    private Bitmap heroBitmap;
    private Bitmap scaledBackgroundBitmap;
    private Bitmap scaledHeroBitmap;

    private int screenWidth = 1;
    private int screenHeight = 1;
    private float heroCenterX;
    private float heroCenterY;
    private float backgroundOffsetY;

    public GameSurfaceView(Context context) {
        super(context);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        setFocusable(true);

        hudPaint.setColor(Color.WHITE);
        hudPaint.setTextSize(42f);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        ensureBitmapsLoaded();
        startLoop();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        rebuildScaledBitmaps();

        if (heroCenterX == 0f && heroCenterY == 0f) {
            heroCenterX = screenWidth * 0.5f;
            heroCenterY = screenHeight * (1f - HERO_BOTTOM_MARGIN_RATIO);
        } else {
            heroCenterX = clamp(heroCenterX, getHeroHalfWidth(), screenWidth - getHeroHalfWidth());
            heroCenterY = clamp(heroCenterY, getHeroHalfHeight(), screenHeight - getHeroHalfHeight());
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopLoop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                heroCenterX = clamp(event.getX(), getHeroHalfWidth(), screenWidth - getHeroHalfWidth());
                heroCenterY = clamp(event.getY(), getHeroHalfHeight(), screenHeight - getHeroHalfHeight());
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public void run() {
        while (running) {
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
        if (surfaceHolder.getSurface().isValid()) {
            startLoop();
        }
    }

    private void drawFrame() {
        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }

        try {
            drawScrollingBackground(canvas);
            drawHero(canvas);
            drawHud(canvas);
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawScrollingBackground(Canvas canvas) {
        if (scaledBackgroundBitmap == null) {
            canvas.drawColor(Color.BLACK);
            return;
        }

        float backgroundHeight = scaledBackgroundBitmap.getHeight();
        backgroundOffsetY += 3f;
        if (backgroundOffsetY >= backgroundHeight) {
            backgroundOffsetY = 0f;
        }

        canvas.drawBitmap(scaledBackgroundBitmap, 0f, backgroundOffsetY - backgroundHeight, null);
        canvas.drawBitmap(scaledBackgroundBitmap, 0f, backgroundOffsetY, null);
    }

    private void drawHero(Canvas canvas) {
        if (scaledHeroBitmap == null) {
            return;
        }
        float left = heroCenterX - getHeroHalfWidth();
        float top = heroCenterY - getHeroHalfHeight();
        canvas.drawBitmap(scaledHeroBitmap, left, top, null);
    }

    private void drawHud(Canvas canvas) {
        canvas.drawText("Aircraft War Android", 24f, 56f, hudPaint);
        canvas.drawText("Baseline migration running", 24f, 108f, hudPaint);
    }

    private void startLoop() {
        if (running) {
            return;
        }
        running = true;
        renderThread = new Thread(this, "aircraft-war-render");
        renderThread.start();
    }

    private void stopLoop() {
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

    private void ensureBitmapsLoaded() {
        if (backgroundBitmap == null) {
            backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
        }
        if (heroBitmap == null) {
            heroBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.hero);
        }
    }

    private void rebuildScaledBitmaps() {
        ensureBitmapsLoaded();
        if (backgroundBitmap != null) {
            scaledBackgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, screenWidth, screenHeight, true);
        }
        if (heroBitmap != null) {
            int heroWidth = Math.max(1, screenWidth / 8);
            int heroHeight = heroBitmap.getHeight() * heroWidth / heroBitmap.getWidth();
            scaledHeroBitmap = Bitmap.createScaledBitmap(heroBitmap, heroWidth, heroHeight, true);
        }
    }

    private float getHeroHalfWidth() {
        return scaledHeroBitmap == null ? 0f : scaledHeroBitmap.getWidth() / 2f;
    }

    private float getHeroHalfHeight() {
        return scaledHeroBitmap == null ? 0f : scaledHeroBitmap.getHeight() / 2f;
    }

    private float clamp(float value, float minValue, float maxValue) {
        if (value < minValue) {
            return minValue;
        }
        return Math.min(value, maxValue);
    }
}
