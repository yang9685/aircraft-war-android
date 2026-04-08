package edu.hitsz.aircraftwar.game.model;

import java.util.List;
import java.util.Random;

import edu.hitsz.aircraftwar.game.GameConfig;
import edu.hitsz.aircraftwar.game.SpriteType;

public abstract class AbstractAircraft extends AbstractFlyingObject {

    protected final int maxHp;
    protected int hp;

    protected AbstractAircraft(
            float locationX,
            float locationY,
            float speedX,
            float speedY,
            int hp,
            int width,
            int height,
            SpriteType spriteType) {
        super(locationX, locationY, speedX, speedY, width, height, spriteType);
        this.maxHp = hp;
        this.hp = hp;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void decreaseHp(int decrease) {
        hp -= decrease;
        if (hp <= 0) {
            hp = 0;
            vanish();
        }
    }

    public void increaseHp(int increase) {
        hp = Math.min(maxHp, hp + increase);
    }

    public abstract List<BaseBullet> shoot(GameConfig config);

    public abstract List<AbstractProp> dropProps(GameConfig config, Random random);

    public abstract int getScoreValue(GameConfig config);
}
