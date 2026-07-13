package fr.ayoub.pvp.mode.fortress.storage;

import fr.ayoub.pvp.domain.fortress.BuildReport;
import fr.ayoub.pvp.domain.fortress.BuildRules;
import fr.ayoub.pvp.domain.fortress.FortressValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player's fortresses, <b>checked against the rules as they are right now</b>.
 *
 * The {@code playable} column is a cache, and it was written under whatever the rules were
 * on the day somebody pressed SAVE. Lower the obsidian budget from forty to ten and every
 * fortress built under the old one is still marked "ready to play" — and would be pasted
 * into a match in breach of the rules that match is being played under. The bug is not a
 * missing check; it is having stored a <b>conclusion</b> instead of recomputing it.
 *
 * So nothing here trusts the column. Every read re-runs {@link FortressValidator}, and
 * repairs the row on its way past, so that the database stops lying too.
 *
 * Blocking JDBC — call it off the main thread.
 */
public final class FortressLibrary {

    /** A fortress, and what the current rules make of it. */
    public record Checked(SavedFortress fortress, BuildReport report) {

        public boolean playable() {
            return report.valid();
        }

        public int slot() {
            return fortress.slot();
        }

        public String name() {
            return fortress.name();
        }
    }

    private final FortressRepository repository;
    private final BuildRules rules;

    public FortressLibrary(FortressRepository repository, BuildRules rules) {
        this.repository = repository;
        this.rules = rules;
    }

    public List<Checked> listFor(UUID owner) {
        List<Checked> checked = new ArrayList<>();

        for (SavedFortress fortress : repository.findAllFor(owner)) {
            BuildReport report = FortressValidator.validate(fortress.blueprint(), rules);

            // The row disagrees with the rules. The rules win — and the row is corrected,
            // rather than left to mislead whatever reads it next.
            if (report.valid() != fortress.playable()) {
                repository.updatePlayable(owner, fortress.slot(), report.valid());
                fortress = fortress.asPlayable(report.valid());
            }
            checked.add(new Checked(fortress, report));
        }
        return checked;
    }

    /** The ones that may actually be taken into a match, today. */
    public List<Checked> playableFor(UUID owner) {
        return listFor(owner).stream().filter(Checked::playable).toList();
    }
}
