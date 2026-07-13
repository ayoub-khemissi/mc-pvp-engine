package fr.ayoub.pvp.mode.fortress.match;

import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;

/**
 * A team's End Crystal, and the health the mode gives it.
 *
 * Minecraft's own crystal has <b>one</b> hit point and explodes when it loses it. That is
 * exactly wrong for a fortress: the whole point of building one is that taking the crystal
 * takes time, under fire, while somebody defends it. So the vanilla damage is intercepted and
 * thrown away, and this holds the health that actually counts.
 *
 * The explosion is thrown away too — it would blow a hole in the fortress the attacker is
 * standing in, which is funny once.
 */
public final class Crystal {

    private final int team;
    private final EnderCrystal entity;
    private final Block base;
    private final int maxHealth;

    private int health;

    public Crystal(int team, EnderCrystal entity, Block base, int maxHealth) {
        this.team = team;
        this.entity = entity;
        this.base = base;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public int team() {
        return team;
    }

    /**
     * The block it stands on.
     *
     * Pull that out and the crystal goes with it. Vanilla would leave it hanging in the air,
     * which is exactly the wrong answer here: an attacker who has spent five minutes tunnelling
     * under a fortress to reach the obsidian has earned the kill, and a crystal floating over a
     * hole is a joke.
     */
    public Block base() {
        return base;
    }

    public boolean isBase(Block block) {
        return base.getWorld().equals(block.getWorld())
                && base.getX() == block.getX()
                && base.getY() == block.getY()
                && base.getZ() == block.getZ();
    }

    public EnderCrystal entity() {
        return entity;
    }

    public int health() {
        return health;
    }

    public int maxHealth() {
        return maxHealth;
    }

    public boolean isDead() {
        return health <= 0;
    }

    /** @return true if that was the blow that broke it */
    public boolean damage(int amount) {
        health = Math.max(0, health - Math.max(0, amount));
        return isDead();
    }

    /** 0.0 … 1.0, for the bar. */
    public float fraction() {
        return maxHealth <= 0 ? 0f : (float) health / maxHealth;
    }

    public void remove() {
        if (entity.isValid()) {
            entity.remove();
        }
    }
}
