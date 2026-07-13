package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortressValidatorTest {

    /** Small on purpose: the rules must not care about the size. */
    private static final int SIZE = 10;

    private final BuildRules rules = new BuildRules(
            SIZE,
            Map.of("STONE", 200, "OBSIDIAN", 8, "OAK_PLANKS", 50),
            "OBSIDIAN",   // the crystal must stand on this
            1,            // clearance radius: 1 → a 3×3 footprint
            3);           // clearance height, in blocks above the base

    private Blueprint blueprint = new Blueprint(SIZE);

    /** The smallest legal fortress: a crystal, on obsidian, in the open. */
    private void aValidFortress() {
        blueprint.set(5, 0, 5, "OBSIDIAN");
        blueprint.crystal(new BlockPos(5, 1, 5));
    }

    private BuildReport check() {
        return FortressValidator.validate(blueprint, rules);
    }

    // --- the crystal ------------------------------------------------------------

    @Test
    void aFortressWithoutACrystalCannotBePlayed() {
        blueprint.set(5, 0, 5, "OBSIDIAN");

        BuildReport report = check();

        assertFalse(report.valid());
        assertTrue(mentions(report, "crystal"),
                "the report must say what is missing: " + report.problems());
    }

    /** The problems are prose shown to a player — do not pin a test to their casing. */
    private static boolean mentions(BuildReport report, String word) {
        return report.problems().stream()
                .anyMatch(problem -> problem.toLowerCase().contains(word.toLowerCase()));
    }

    @Test
    void aCrystalOnObsidianInTheOpenIsValid() {
        aValidFortress();

        assertTrue(check().valid(), () -> check().problems().toString());
    }

    @Test
    void theCrystalMustStandOnTheRightBlock() {
        blueprint.set(5, 0, 5, "STONE");
        blueprint.crystal(new BlockPos(5, 1, 5));

        BuildReport report = check();

        assertFalse(report.valid());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("OBSIDIAN")));
    }

    @Test
    void theCrystalCannotFloat() {
        blueprint.crystal(new BlockPos(5, 1, 5));   // nothing underneath

        assertFalse(check().valid());
    }

    @Test
    void theCrystalCannotSitOnTheFloorOfTheCube() {
        // y = 0 leaves no room for the base block below it
        blueprint.crystal(new BlockPos(5, 0, 5));

        assertFalse(check().valid());
    }

    @Test
    void theCrystalCannotBeOutsideTheCube() {
        blueprint.crystal(new BlockPos(SIZE, 1, 5));

        assertFalse(check().valid());
    }

    // --- the clearance around the crystal ---------------------------------------

    @Test
    void nothingMayBeBuiltInTheCrystalsClearance() {
        aValidFortress();
        blueprint.set(4, 1, 5, "STONE");   // right beside the crystal

        BuildReport report = check();

        assertFalse(report.valid());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("clearance")),
                report.problems().toString());
    }

    @Test
    void theClearanceIsThreeBlocksTall() {
        aValidFortress();
        blueprint.set(5, 3, 5, "STONE");   // base is y=0, crystal y=1, so y=1..3 is clear

        assertFalse(check().valid());
    }

    @Test
    void justOutsideTheClearanceIsAllowed() {
        aValidFortress();
        blueprint.set(3, 1, 5, "STONE");   // 2 away: outside a radius of 1
        blueprint.set(5, 4, 5, "STONE");   // 4 above the base: above the clearance

        assertTrue(check().valid(), () -> check().problems().toString());
    }

    @Test
    void theClearanceSizeIsAParameter() {
        BuildRules wide = new BuildRules(SIZE, rules.allowance(), "OBSIDIAN", 2, 3);
        aValidFortress();
        blueprint.set(3, 1, 5, "STONE");   // fine with radius 1, not with radius 2

        assertTrue(FortressValidator.validate(blueprint, rules).valid());
        assertFalse(FortressValidator.validate(blueprint, wide).valid());
    }

    @Test
    void aClearanceThatFallsOutsideTheCubeIsFine() {
        // A crystal in the corner: part of its clearance is simply not in the cube.
        blueprint.set(0, 0, 0, "OBSIDIAN");
        blueprint.crystal(new BlockPos(0, 1, 0));

        assertTrue(check().valid(), () -> check().problems().toString());
    }

    // --- the palette and the budget ---------------------------------------------

    @Test
    void aBlockOutsideThePaletteIsRefused() {
        aValidFortress();
        blueprint.set(1, 1, 1, "BEDROCK");

        BuildReport report = check();

        assertFalse(report.valid());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("BEDROCK")));
    }

    @Test
    void aBlockOverItsQuotaIsRefused() {
        aValidFortress();
        for (int i = 0; i < 9; i++) {
            blueprint.set(i, 5, 0, "OBSIDIAN");   // 9 + the base = 10, quota is 8
        }

        BuildReport report = check();

        assertFalse(report.valid());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("OBSIDIAN")));
    }

    @Test
    void exactlyTheQuotaIsAllowed() {
        aValidFortress();
        for (int i = 0; i < 7; i++) {
            blueprint.set(i, 5, 0, "OBSIDIAN");   // 7 + the base = 8, exactly the quota
        }

        assertTrue(check().valid(), () -> check().problems().toString());
    }

    @Test
    void howMuchIsLeftOfEachBlock() {
        aValidFortress();
        blueprint.set(0, 5, 0, "STONE");
        blueprint.set(1, 5, 0, "STONE");

        assertEquals(198, rules.remaining(blueprint, "STONE"));
        assertEquals(7, rules.remaining(blueprint, "OBSIDIAN"), "the crystal base counts");
        assertEquals(0, rules.remaining(blueprint, "BEDROCK"), "not in the palette, so none");
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        blueprint.set(1, 1, 1, "BEDROCK");   // not in the palette, and no crystal either

        BuildReport report = check();

        assertFalse(report.valid());
        assertTrue(report.problems().size() >= 2,
                "a builder should not have to fix one problem to discover the next: "
                        + report.problems());
    }

    // --- the rules can change under a fortress that is already built ---------------

    @Test
    void tighteningAQuotaTurnsAValidFortressIntoADraft() {
        // The whole reason validation cannot be a flag written once at save time. Somebody
        // lowers the obsidian budget in config.yml, and every fortress built under the old
        // one has to stop being playable — quietly keeping them would mean pasting a
        // fortress that breaks the rules the match is being played under.
        aValidFortress();
        for (int i = 0; i < 5; i++) {
            blueprint.set(i, 5, 0, "OBSIDIAN");   // 5 + the base = 6
        }

        assertTrue(check().valid(), "six obsidian is fine when the quota is eight");

        BuildRules tightened = new BuildRules(
                SIZE, Map.of("STONE", 200, "OBSIDIAN", 3, "OAK_PLANKS", 50),
                "OBSIDIAN", 1, 3);

        BuildReport report = FortressValidator.validate(blueprint, tightened);

        assertFalse(report.valid(), "and a draft the day the quota drops to three");
        assertTrue(mentions(report, "OBSIDIAN"));
    }

    @Test
    void droppingABlockFromThePaletteInvalidatesWhatUsedIt() {
        aValidFortress();
        blueprint.set(1, 1, 1, "OAK_PLANKS");

        BuildRules withoutWood = new BuildRules(
                SIZE, Map.of("STONE", 200, "OBSIDIAN", 8), "OBSIDIAN", 1, 3);

        assertFalse(FortressValidator.validate(blueprint, withoutWood).valid());
    }

    @Test
    void aFortressBuiltAtAnotherCubeSizeCannotBePlayed() {
        // The cube is a config knob. A fortress saved at 10³ cannot be pasted into a 20³
        // pad — it would sit in a corner of it. It must be re-built, not silently stretched.
        Blueprint small = new Blueprint(6);
        small.set(2, 0, 2, "OBSIDIAN");
        small.crystal(new BlockPos(2, 1, 2));

        BuildReport report = FortressValidator.validate(small, rules);   // rules say SIZE = 10

        assertFalse(report.valid());
        assertTrue(mentions(report, "size"), report.problems().toString());
    }

    @Test
    void relaxingTheRulesMakesADraftPlayableAgain() {
        // It has to work both ways, or a fortress is punished forever for a rule that was
        // since taken back.
        aValidFortress();
        blueprint.set(1, 1, 1, "BEDROCK");

        assertFalse(check().valid());

        BuildRules permissive = new BuildRules(
                SIZE, Map.of("BEDROCK", 10, "OBSIDIAN", 8), "OBSIDIAN", 1, 3);

        assertTrue(FortressValidator.validate(blueprint, permissive).valid());
    }

    // --- a draft is not an error -------------------------------------------------

    @Test
    void anEmptyBuildIsSimplyNotReadyYet() {
        BuildReport report = check();

        assertFalse(report.valid());
        assertFalse(report.problems().isEmpty(), "it must say why, so the UI can show it");
    }
}
