package fr.ayoub.pvp.domain.match;

/** The life of a match, from "we found players" to "the arena is free again". */
public enum MatchState {

    /** Players are picked, nothing has happened yet. */
    CREATED,

    /** Teleported into the arena, frozen, kits given. */
    PREPARING,

    /** 3 · 2 · 1 … */
    COUNTDOWN,

    /** FIGHT. */
    LIVE,

    /** A round is over; another may follow. */
    ROUND_ENDING,

    /** The match is decided; ratings are applied. */
    ENDING,

    /** Players go back to the lobby, the arena is reset and released. */
    CLEANUP,

    /** Done. */
    CLOSED
}
