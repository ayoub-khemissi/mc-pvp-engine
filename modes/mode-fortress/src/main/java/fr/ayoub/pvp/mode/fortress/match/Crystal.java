package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.domain.fortress.CrystalHealth;
import fr.ayoub.pvp.domain.fortress.CrystalHitWindow;
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
    private final CrystalHealth health;

    /**
     * Every attacker's own half-second. Without it, mashing the attack button beats waiting for
     * the cooldown — see {@link CrystalHitWindow}, which explains the arithmetic.
     */
    private final CrystalHitWindow window;

    public Crystal(int team, EnderCrystal entity, Block base, double maxHealth, long hitCooldownTicks) {
        this.team = team;
        this.entity = entity;
        this.base = base;
        this.health = new CrystalHealth(maxHealth);
        this.window = new CrystalHitWindow(hitCooldownTicks);
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

    /** What the boss bar shows. Rounded up while it is alive: 0.2 left is not "0". */
    public int health() {
        return health.display();
    }

    public int maxHealth() {
        return (int) Math.ceil(health.max());
    }

    public boolean isDead() {
        return health.isDead();
    }

    /**
     * @param amount what the blow was worth — <b>fractions and all</b>. This used to be an int
     *               floored to a minimum of 1, which meant a spammed swing and a fully charged
     *               one both took exactly 1 off, and mashing beat waiting.
     * @return true if that was the blow that broke it
     */
    public boolean damage(double amount) {
        return health.damage(amount);
    }

    /**
     * How much of this attacker's blow the crystal is willing to take right now.
     *
     * Their own last blow holds it for half a second, so that the attack cooldown Minecraft
     * already applies is worth respecting. It is <b>theirs</b>, not the crystal's: their
     * teammates are not on it, and a three-man push is still worth three men.
     */
    public double admit(java.util.UUID attacker, double amount, long tick) {
        return window.admit(attacker, amount, tick);
    }

    /** Straight to zero. The obsidian under it was pulled out. */
    public boolean kill() {
        return health.damage(health.max());
    }

    /** 0.0 … 1.0, for the bar. */
    public float fraction() {
        return health.fraction();
    }

    public void remove() {
        if (entity.isValid()) {
            entity.remove();
        }
    }
}
