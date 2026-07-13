package fr.ayoub.pvp.domain.fortress;

import java.util.UUID;

/**
 * One fortress on offer during the vote.
 *
 * @param owner     whose fortress it is — <b>null</b> for a preset, which is what a team
 *                  with no playable fortress is given so the match can still happen
 * @param fortressId the saved fortress
 * @param name      what the builder called it; shown on the sign in front of the build
 */
public record Candidate(UUID owner, UUID fortressId, String name) {

    public boolean isPreset() {
        return owner == null;
    }
}
