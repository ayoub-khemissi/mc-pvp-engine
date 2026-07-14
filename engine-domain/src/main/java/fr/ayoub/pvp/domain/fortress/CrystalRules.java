package fr.ayoub.pvp.domain.fortress;

/**
 * How much of a blow the crystal actually takes, per kind of blow.
 *
 * <p><b>What the game already did for us.</b> By the time a hit reaches the mode, Minecraft has
 * done the hard part: the number it hands over is the weapon's attack damage, scaled by how far
 * the attack cooldown had recharged (a fifth of it if the swing was spammed, all of it if the
 * player waited), multiplied by 1.5 for a critical, plus Sharpness, plus Strength. Arrows come
 * scaled by draw and by Power. Re-deriving any of that would be re-implementing the game, and
 * getting it subtly wrong — which is worse than not doing it, because the player's own damage
 * indicator would disagree with what the crystal took.
 *
 * <p><b>What is ours to decide.</b> Only how much a <em>crystal</em> cares. It is an objective,
 * not a mob: a sword, a bow and a bed do not have to be worth against it what they are worth
 * against a player, and a fortress that falls to two minutes of archery from a safe rooftop is
 * not a fortress. So each kind of blow gets a multiplier — and every one of them is a number in
 * the config, because all of this is still an experiment.
 */
public record CrystalRules(double melee, double projectile, double explosion, double other) {

    /** Where a blow came from. The Bukkit layer classifies; this decides what it is worth. */
    public enum Source {
        /** A player hitting it. Already carries the weapon, the cooldown and the crit. */
        MELEE,
        /** An arrow, a trident, a snowball. Already carries the draw and the Power. */
        PROJECTILE,
        /** TNT, a bed, a crystal, a creeper. */
        EXPLOSION,
        /** Fire, lava, a falling anvil — whatever else the world can throw at it. */
        OTHER
    }

    public CrystalRules {
        require(melee, "melee");
        require(projectile, "projectile");
        require(explosion, "explosion");
        require(other, "other");
    }

    /** All four kinds taken exactly as the game reports them. */
    public static CrystalRules vanilla() {
        return new CrystalRules(1, 1, 1, 1);
    }

    /**
     * @param raw what Minecraft says the blow was worth
     * @return what the crystal loses. Never negative — nothing heals a crystal.
     */
    public double damageOf(Source source, double raw) {
        if (raw <= 0) {
            return 0;
        }
        return raw * switch (source) {
            case MELEE -> melee;
            case PROJECTILE -> projectile;
            case EXPLOSION -> explosion;
            case OTHER -> other;
        };
    }

    private static void require(double multiplier, String name) {
        if (multiplier < 0) {
            throw new IllegalArgumentException(
                    "the '" + name + "' multiplier cannot be negative, was " + multiplier);
        }
    }
}
