package edu.hitsz.aircraftwar.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.hitsz.aircraftwar.audio.SoundEffect;
import edu.hitsz.aircraftwar.game.factory.BossEnemyFactory;
import edu.hitsz.aircraftwar.game.factory.EliteEnemyFactory;
import edu.hitsz.aircraftwar.game.factory.ElitePlusEnemyFactory;
import edu.hitsz.aircraftwar.game.factory.EnemyFactory;
import edu.hitsz.aircraftwar.game.factory.MobEnemyFactory;
import edu.hitsz.aircraftwar.game.model.AbstractAircraft;
import edu.hitsz.aircraftwar.game.model.AbstractProp;
import edu.hitsz.aircraftwar.game.model.BaseBullet;
import edu.hitsz.aircraftwar.game.model.BossEnemy;
import edu.hitsz.aircraftwar.game.model.HeroAircraft;
import edu.hitsz.aircraftwar.game.strategy.DirectFireStrategy;
import edu.hitsz.aircraftwar.game.strategy.ShootStrategy;

public class GameEngine {

    private final GameConfig config;
    private final Random random = new Random();
    private final EnemyFactory mobEnemyFactory = new MobEnemyFactory();
    private final EnemyFactory eliteEnemyFactory = new EliteEnemyFactory();
    private final EnemyFactory elitePlusEnemyFactory = new ElitePlusEnemyFactory();
    private final EnemyFactory bossEnemyFactory = new BossEnemyFactory();
    private final DirectFireStrategy defaultHeroStrategy = new DirectFireStrategy();
    private final List<SoundEffect> pendingSoundEffects = new ArrayList<>();

    private final HeroAircraft heroAircraft;
    private final List<AbstractAircraft> enemyAircrafts = new ArrayList<>();
    private final List<BaseBullet> heroBullets = new ArrayList<>();
    private final List<BaseBullet> enemyBullets = new ArrayList<>();
    private final List<AbstractProp> props = new ArrayList<>();

    private long elapsedMs;
    private long cycleTimerMs;
    private long strategyExpireAtMs = -1L;
    private int score;
    private int bossPhase = 1;
    private boolean gameOver;

    public GameEngine(GameConfig config) {
        this.config = config;
        this.heroAircraft = new HeroAircraft(
                config.getHeroSpawnX(),
                config.getHeroSpawnY(),
                config.getHeroMaxHp(),
                config.getHeroWidth(),
                config.getHeroHeight());
    }

    public void update(long deltaMs) {
        if (gameOver) {
            return;
        }

        elapsedMs += deltaMs;
        cycleTimerMs += deltaMs;

        if (strategyExpireAtMs > 0L && elapsedMs >= strategyExpireAtMs) {
            heroAircraft.setShootStrategy(defaultHeroStrategy);
            strategyExpireAtMs = -1L;
        }

        while (cycleTimerMs >= config.getCycleDurationMs()) {
            cycleTimerMs -= config.getCycleDurationMs();
            if (enemyAircrafts.size() < config.getEnemyMaxNumber()) {
                spawnRandomEnemy();
            }
            shootAction();
        }

        spawnBossIfNeeded();
        bulletsMoveAction();
        propsMoveAction();
        aircraftsMoveAction();
        crashCheckAction();
        postProcessAction();

        if (heroAircraft.notValid() || heroAircraft.getHp() <= 0) {
            gameOver = true;
            pendingSoundEffects.add(SoundEffect.GAME_OVER);
        }
    }

    public void moveHeroTo(float x, float y) {
        if (!gameOver) {
            heroAircraft.moveTo(x, y, config);
        }
    }

    public void healHero(int amount) {
        heroAircraft.increaseHp(amount);
    }

    public void applyHeroStrategy(ShootStrategy strategy, long durationMs) {
        heroAircraft.setShootStrategy(strategy);
        strategyExpireAtMs = elapsedMs + durationMs;
    }

    public void triggerBomb() {
        pendingSoundEffects.add(SoundEffect.BOMB_EXPLOSION);
        for (AbstractAircraft enemyAircraft : enemyAircrafts) {
            if (enemyAircraft.notValid()) {
                continue;
            }
            enemyAircraft.decreaseHp(Integer.MAX_VALUE);
            if (enemyAircraft.notValid()) {
                score += enemyAircraft.getScoreValue(config);
                props.addAll(enemyAircraft.dropProps(config, random));
            }
        }
        for (BaseBullet enemyBullet : enemyBullets) {
            enemyBullet.vanish();
        }
    }

    public GameConfig getConfig() {
        return config;
    }

    public HeroAircraft getHeroAircraft() {
        return heroAircraft;
    }

    public List<AbstractAircraft> getEnemyAircrafts() {
        return enemyAircrafts;
    }

    public List<BaseBullet> getHeroBullets() {
        return heroBullets;
    }

    public List<BaseBullet> getEnemyBullets() {
        return enemyBullets;
    }

    public List<AbstractProp> getProps() {
        return props;
    }

    public int getScore() {
        return score;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public List<SoundEffect> drainSoundEffects() {
        List<SoundEffect> drained = new ArrayList<>(pendingSoundEffects);
        pendingSoundEffects.clear();
        return drained;
    }

    public boolean isBossBattleActive() {
        return getAliveBoss() != null;
    }

    public BossEnemy getAliveBoss() {
        for (AbstractAircraft enemyAircraft : enemyAircrafts) {
            if (enemyAircraft instanceof BossEnemy && enemyAircraft.isValid()) {
                return (BossEnemy) enemyAircraft;
            }
        }
        return null;
    }

    public SpriteType getBackgroundSpriteType() {
        switch (config.getDifficulty()) {
            case EASY:
                return SpriteType.BACKGROUND_EASY;
            case HARD:
                return SpriteType.BACKGROUND_HARD;
            default:
                return SpriteType.BACKGROUND_NORMAL;
        }
    }

    private void spawnRandomEnemy() {
        EnemyFactory enemyFactory;
        double randomValue = random.nextDouble();
        if (randomValue < 0.7) {
            enemyFactory = mobEnemyFactory;
        } else if (randomValue < 0.9) {
            enemyFactory = eliteEnemyFactory;
        } else {
            enemyFactory = elitePlusEnemyFactory;
        }

        AbstractAircraft sample = enemyFactory.create(0f, 0f, config);
        float spawnX = randomSpawnX(sample.getWidth());
        float spawnY = randomSpawnY(sample.getHeight());
        enemyAircrafts.add(enemyFactory.create(spawnX, spawnY, config));
    }

    private void spawnBossIfNeeded() {
        if (isBossBattleActive()) {
            return;
        }
        if (score < bossPhase * config.getBossScoreStep()) {
            return;
        }
        bossPhase += 1;

        AbstractAircraft sample = bossEnemyFactory.create(0f, 0f, config);
        float spawnX = randomSpawnX(sample.getWidth());
        float spawnY = Math.max(sample.getHeight() * 0.7f, config.getSpawnTopLimit() * 0.4f);
        enemyAircrafts.add(bossEnemyFactory.create(spawnX, spawnY, config));
    }

    private void shootAction() {
        for (AbstractAircraft enemyAircraft : enemyAircrafts) {
            enemyBullets.addAll(enemyAircraft.shoot(config));
        }
        List<BaseBullet> newHeroBullets = heroAircraft.shoot(config);
        if (!newHeroBullets.isEmpty()) {
            pendingSoundEffects.add(SoundEffect.BULLET);
            heroBullets.addAll(newHeroBullets);
        }
    }

    private void bulletsMoveAction() {
        for (BaseBullet heroBullet : heroBullets) {
            heroBullet.forward(config);
        }
        for (BaseBullet enemyBullet : enemyBullets) {
            enemyBullet.forward(config);
        }
    }

    private void propsMoveAction() {
        for (AbstractProp prop : props) {
            prop.forward(config);
        }
    }

    private void aircraftsMoveAction() {
        for (AbstractAircraft enemyAircraft : enemyAircrafts) {
            enemyAircraft.forward(config);
        }
    }

    private void crashCheckAction() {
        for (BaseBullet enemyBullet : enemyBullets) {
            if (enemyBullet.notValid()) {
                continue;
            }
            if (heroAircraft.crash(enemyBullet)) {
                heroAircraft.decreaseHp(enemyBullet.getPower());
                enemyBullet.vanish();
            }
        }

        for (BaseBullet heroBullet : heroBullets) {
            if (heroBullet.notValid()) {
                continue;
            }
            for (AbstractAircraft enemyAircraft : enemyAircrafts) {
                if (enemyAircraft.notValid()) {
                    continue;
                }
                if (enemyAircraft.crash(heroBullet)) {
                    enemyAircraft.decreaseHp(heroBullet.getPower());
                    heroBullet.vanish();
                    pendingSoundEffects.add(SoundEffect.BULLET_HIT);
                    if (enemyAircraft.notValid()) {
                        score += enemyAircraft.getScoreValue(config);
                        props.addAll(enemyAircraft.dropProps(config, random));
                    }
                    break;
                }
            }
        }

        for (AbstractAircraft enemyAircraft : enemyAircrafts) {
            if (enemyAircraft.notValid()) {
                continue;
            }
            if (enemyAircraft.crash(heroAircraft) || heroAircraft.crash(enemyAircraft)) {
                enemyAircraft.vanish();
                heroAircraft.decreaseHp(Integer.MAX_VALUE);
            }
        }

        // Bomb pickup can spawn additional props during the same frame.
        // Iterate over a snapshot so newly added props do not invalidate the loop.
        for (AbstractProp prop : new ArrayList<>(props)) {
            if (prop.notValid()) {
                continue;
            }
            if (heroAircraft.crash(prop)) {
                prop.apply(this);
                prop.vanish();
                pendingSoundEffects.add(SoundEffect.GET_SUPPLY);
            }
        }
    }

    private void postProcessAction() {
        enemyBullets.removeIf(BaseBullet::notValid);
        heroBullets.removeIf(BaseBullet::notValid);
        enemyAircrafts.removeIf(AbstractAircraft::notValid);
        props.removeIf(AbstractProp::notValid);
    }
    private float randomSpawnX(int objectWidth) {
        float minX = objectWidth * 0.5f;
        float maxX = config.getScreenWidth() - objectWidth * 0.5f;
        return minX + random.nextFloat() * Math.max(1f, maxX - minX);
    }

    private float randomSpawnY(int objectHeight) {
        float minY = objectHeight * 0.5f;
        float maxY = Math.max(minY, config.getSpawnTopLimit());
        return minY + random.nextFloat() * Math.max(1f, maxY - minY);
    }
}
