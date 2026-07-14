package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How hard the crystal is to break, per kind of blow.
 *
 * Minecraft has already done the hard part by the time a blow reaches us: the number it hands
 * over is the weapon's damage, scaled by how far the attack cooldown had recharged, multiplied
 * by 1.5 if it was a critical, plus Sharpness, plus Strength. Re-deriving any of that would be
 * re-implementing the game — and getting it subtly wrong, which is worse than not doing it.
 *
 * <p>What is <b>ours</b> to decide is how much a crystal cares. A pickaxe and a bow and a bed
 * do not have to be worth what they are worth against a player: a fortress that falls to two
 * minutes of archery is not a fortress. So each kind of blow gets a multiplier, and every one
 * of them is a number in the config, because all of this is still an experiment.
 */
class CrystalRulesTest {

    private static final CrystalRules VANILLA = new CrystalRules(1, 1, 1, 1);

    @Test
    void takesTheGameAtItsWordByDefault() {
        assertEquals(7.0, VANILLA.damageOf(CrystalRules.Source.MELEE, 7.0));
        assertEquals(4.5, VANILLA.damageOf(CrystalRules.Source.PROJECTILE, 4.5));
        assertEquals(30.0, VANILLA.damageOf(CrystalRules.Source.EXPLOSION, 30.0));
    }

    /**
     * The cooldown, which is the thing that was broken, survives untouched: we scale what the
     * game gives us, so a spammed swing stays worth a fifth of a patient one.
     */
    @Test
    void preservesTheAttackCooldown() {
        CrystalRules rules = new CrystalRules(2, 1, 1, 1);

        double spammed = rules.damageOf(CrystalRules.Source.MELEE, 8 * 0.2);
        double charged = rules.damageOf(CrystalRules.Source.MELEE, 8 * 1.0);

        assertEquals(charged / spammed, 5.0, 1e-9);
        assertTrue(spammed < charged);
    }

    @Test
    void weighsEachKindOfBlowOnItsOwn() {
        CrystalRules rules = new CrystalRules(1.5, 0.5, 0.25, 0);

        assertEquals(12.0, rules.damageOf(CrystalRules.Source.MELEE, 8));
        assertEquals(4.0, rules.damageOf(CrystalRules.Source.PROJECTILE, 8));
        assertEquals(2.0, rules.damageOf(CrystalRules.Source.EXPLOSION, 8));
        assertEquals(0.0, rules.damageOf(CrystalRules.Source.OTHER, 8));
    }

    /** A multiplier of zero is how a server says "fire cannot touch a crystal". */
    @Test
    void letsAKindOfBlowBeMadeHarmless() {
        CrystalRules rules = new CrystalRules(1, 1, 1, 0);

        assertEquals(0.0, rules.damageOf(CrystalRules.Source.OTHER, 1000));
    }

    @Test
    void neverHealsTheCrystal() {
        assertEquals(0.0, VANILLA.damageOf(CrystalRules.Source.MELEE, -10));
    }

    @Test
    void refusesANegativeMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> new CrystalRules(-1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CrystalRules(1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CrystalRules(1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CrystalRules(1, 1, 1, -1));
    }
}
