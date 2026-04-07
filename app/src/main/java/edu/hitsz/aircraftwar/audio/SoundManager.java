package edu.hitsz.aircraftwar.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

import java.util.EnumMap;
import java.util.Map;

import edu.hitsz.aircraftwar.R;

public class SoundManager {

    private static SoundManager instance;

    private final Context appContext;
    private final SoundPool soundPool;
    private final Map<SoundEffect, Integer> effectIds = new EnumMap<>(SoundEffect.class);

    private MediaPlayer bgmPlayer;
    private int currentBgmResId;
    private boolean soundEnabled = true;

    private SoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                .setMaxStreams(6)
                .build();

        effectIds.put(SoundEffect.BULLET, soundPool.load(appContext, R.raw.bullet, 1));
        effectIds.put(SoundEffect.BULLET_HIT, soundPool.load(appContext, R.raw.bullet_hit, 1));
        effectIds.put(SoundEffect.BOMB_EXPLOSION, soundPool.load(appContext, R.raw.bomb_explosion, 1));
        effectIds.put(SoundEffect.GET_SUPPLY, soundPool.load(appContext, R.raw.get_supply, 1));
        effectIds.put(SoundEffect.GAME_OVER, soundPool.load(appContext, R.raw.game_over, 1));
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        if (!soundEnabled) {
            stopBgm();
        }
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void playGameBgm() {
        if (!soundEnabled) {
            return;
        }
        startBgm(R.raw.bgm);
    }

    public void pauseBgm() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.pause();
        }
    }

    public void resumeBgm() {
        if (soundEnabled && bgmPlayer != null && !bgmPlayer.isPlaying()) {
            bgmPlayer.start();
        }
    }

    public void stopBgm() {
        if (bgmPlayer != null) {
            if (bgmPlayer.isPlaying()) {
                bgmPlayer.stop();
            }
            bgmPlayer.release();
            bgmPlayer = null;
        }
        currentBgmResId = 0;
    }

    public void playEffect(SoundEffect soundEffect) {
        if (!soundEnabled) {
            return;
        }
        Integer soundId = effectIds.get(soundEffect);
        if (soundId != null) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    public void release() {
        stopBgm();
        soundPool.release();
        instance = null;
    }

    private void startBgm(int rawResId) {
        if (bgmPlayer != null && currentBgmResId == rawResId) {
            if (!bgmPlayer.isPlaying()) {
                bgmPlayer.start();
            }
            return;
        }
        stopBgm();
        bgmPlayer = MediaPlayer.create(appContext, rawResId);
        if (bgmPlayer != null) {
            currentBgmResId = rawResId;
            bgmPlayer.setLooping(true);
            bgmPlayer.start();
        }
    }
}
