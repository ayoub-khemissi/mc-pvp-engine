package fr.ayoub.pvp.domain.fortress;

import java.util.List;

/**
 * Whether a fortress may be taken into a match, and if not, everything that is wrong
 * with it.
 *
 * A build that fails this is not an error: it is a <b>draft</b>. It saves, it loads, it
 * just cannot be picked for a game. The list of problems is what the build menu shows the
 * player — all of them at once, so nobody has to fix one to discover the next.
 */
public record BuildReport(boolean valid, List<String> problems) {

    public BuildReport {
        problems = List.copyOf(problems);
    }

    public static BuildReport ok() {
        return new BuildReport(true, List.of());
    }

    public static BuildReport failed(List<String> problems) {
        return new BuildReport(false, problems);
    }
}
