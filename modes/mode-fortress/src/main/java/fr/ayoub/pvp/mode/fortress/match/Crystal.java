package fr.ayoub.pvp.mode.fortress.match;

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
    private final int maxHealth;

    private int health;

    public Crystal(int team, EnderCrystal entity, int maxHealth) {
        this.team = team;
        this.entity = entity;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public int team() {
        return team;
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
