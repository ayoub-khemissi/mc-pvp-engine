package fr.ayoub.pvp.mode.fortress.storage;

import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.BuildReport;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.BuildRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens to a fortress the day the rules change under it.
 *
 * This is the whole point of the library: the {@code playable} column is a <b>cache</b>, and
 * a cache is allowed to be wrong. The rules are the truth, and they are re-applied on every
 * read.
 */
class FortressLibraryTest {

    private static final int SIZE = 10;

    /** Generous: eight obsidian allowed. */
    private static final BuildRules GENEROUS = new BuildRules(
            SIZE, Map.of("OBSIDIAN", 8, "STONE", 100), "OBSIDIAN", 1, 3);

    /** The same server, after somebody lowers the obsidian budget to two. */
    private static final BuildRules TIGHTENED = new BuildRules(
            SIZE, Map.of("OBSIDIAN", 2, "STONE", 100), "OBSIDIAN", 1, 3);

    private DataSource dataSource;
    private FortressRepository repository;
    private UUID owner;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.migrated();
        repository = new FortressRepository(dataSource);
        owner = UUID.randomUUID();
    }

    /** A fortress using five obsidian: legal under GENEROUS, illegal under TIGHTENED. */
    private void saveAFortressUsingFiveObsidian(int slot) {
        Blueprint blueprint = new Blueprint(SIZE);
        blueprint.set(5, 0, 5, "OBSIDIAN");        // the crystal base
        for (int i = 0; i < 4; i++) {
            blueprint.set(i, 4, 0, "OBSIDIAN");    // four more: five in total
        }
        blueprint.crystal(new BlockPos(5, 1, 5));

        repository.save(new SavedFortress(owner, slot, "Keep", blueprint, true, true));
    }

    private FortressLibrary library(BuildRules rules) {
        return new FortressLibrary(repository, rules);
    }

    @Test
    void aFortressBuiltUnderTheOldRulesIsPlayableUnderThem() {
        saveAFortressUsingFiveObsidian(1);

        List<FortressLibrary.Checked> all = library(GENEROUS).listFor(owner);

        assertEquals(1, all.size());
        assertTrue(all.getFirst().playable());
        assertTrue(all.getFirst().report().problems().isEmpty());
    }

    @Test
    void tighteningTheRulesInvalidatesIt() {
        saveAFortressUsingFiveObsidian(1);

        FortressLibrary.Checked checked = library(TIGHTENED).listFor(owner).getFirst();

        assertFalse(checked.playable(), "five obsidian, and the budget is now two");
        assertTrue(checked.report().problems().stream()
                        .anyMatch(problem -> problem.contains("OBSIDIAN")),
                "and it says why: " + checked.report().problems());
    }

    @Test
    void anInvalidatedFortressCannotBeTakenIntoAMatch() {
        saveAFortressUsingFiveObsidian(1);

        assertEquals(1, library(GENEROUS).playableFor(owner).size());
        assertTrue(library(TIGHTENED).playableFor(owner).isEmpty(),
                "queueing must be impossible, not merely discouraged");
    }

    @Test
    void theStoredFlagIsRepairedRatherThanLeftLying() {
        // The row still says playable = true. Reading it under the new rules must not leave
        // that lie in the database, or anything that trusts the column keeps being wrong.
        saveAFortressUsingFiveObsidian(1);
        assertTrue(repository.find(owner, 1).orElseThrow().playable());

        library(TIGHTENED).listFor(owner);

        assertFalse(repository.find(owner, 1).orElseThrow().playable(),
                "the row was corrected on the way past");
    }

    @Test
    void relaxingTheRulesAgainMakesItPlayableAgain() {
        saveAFortressUsingFiveObsidian(1);
        library(TIGHTENED).listFor(owner);          // invalidated, and the row now says so

        assertTrue(library(GENEROUS).listFor(owner).getFirst().playable(),
                "a fortress must not be punished forever for a rule that was taken back");
        assertTrue(repository.find(owner, 1).orElseThrow().playable());
    }

    @Test
    void theReasonIsCarriedBackSoTheMenuCanShowIt() {
        // "Not playable" with no reason is how a player ends up thinking the server is
        // broken, when in fact they are three blocks over the obsidian budget.
        saveAFortressUsingFiveObsidian(1);

        BuildReport report = library(TIGHTENED).listFor(owner).getFirst().report();

        assertFalse(report.valid());
        assertTrue(report.problems().stream().anyMatch(problem ->
                        problem.contains("5") && problem.contains("2")),
                "it must say how many were placed and how many are allowed: " + report.problems());
    }

    @Test
    void aDraftStaysADraft() {
        // Never playable, and no rule changed under it. It must not suddenly become valid,
        // and it must not be reported as something the rules broke.
        Blueprint noCrystal = new Blueprint(SIZE);
        noCrystal.set(1, 1, 1, "STONE");
        repository.save(new SavedFortress(owner, 1, "Draft", noCrystal, false, true));

        FortressLibrary.Checked checked = library(GENEROUS).listFor(owner).getFirst();

        assertFalse(checked.playable());
        assertTrue(library(GENEROUS).playableFor(owner).isEmpty());
    }

    @Test
    void aPlayerWithNoFortressIsNotAProblem() {
        assertTrue(library(GENEROUS).listFor(owner).isEmpty());
        assertTrue(library(GENEROUS).playableFor(owner).isEmpty());
    }
}
