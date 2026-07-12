# PvP Engine — context for AI agents

Read this before touching anything. It is the fastest way to be useful here.

## What this is

A **modular PvP engine** for Minecraft, written as a **Paper plugin**. It provides the
plumbing (lobby, queues, arenas, invisible walls, ELO, GUIs, MySQL) so that a **game mode**
is a tiny separate plugin that only declares its rules.

- Minecraft **26.1.2** · Paper · **Java 25** (26.x does not run on Java 21)
- Players connect with a **vanilla client** — never require a client mod.

## The rules of this project (non-negotiable)

1. **TDD.** Domain logic gets a **failing test first**, then the implementation.
   Any bug found in game gets a **regression test before the fix**.
   `./gradlew test` must be green before anything is deployed.
2. **`engine-domain` has no Bukkit imports.** Ever. All real logic lives there so it can be
   tested in milliseconds with no server. Bukkit classes stay thin adapters.
   *If a class is hard to test, the logic is in the wrong module.*
3. **Never touch the database on the main thread.** Use `plugin.async()`, then come back
   with `Bukkit.getScheduler().runTask(...)` before touching the world or an inventory.
4. **No player-facing commands.** The UX is items and chest menus (`/pvpadmin` is staff-only).

## Modules

```
engine-domain/    PURE JAVA, no Bukkit. Elo, matchmaking, regions, match state machine,
                  menu layout, arena selection. ~112 unit tests live here.
engine-api/       The SPI a game mode implements: GameModeDefinition, MatchHandler,
                  MatchContext, MatchRules, Team, MatchOutcome, PvPEngineApi.
engine-storage/   JDBC repositories + migrations. Bukkit-free, tested against H2.
engine-core/      The Paper plugin. Wires everything: lobby, arenas, queue, match, UI.
modes/mode-duel/  A game mode, as its OWN plugin (4 KB). Proof the engine is modular.
```

Dependency direction: `core → api → domain`, `core → storage → domain`. Domain depends on
nothing.

## How a game mode works

A mode implements `GameModeDefinition` + `MatchHandler` and registers itself:

```java
public void onEnable() {
    PvPEngineApi.modes().register(new DuelMode());   // plugin.yml: depend: [PvPEngine]
}
```

The engine already does queueing, arena allocation, walls, countdown, deaths,
last-team-standing, titles, ELO and cleanup. `DuelHandler` is **one method** (`giveKit`).
**A new mode should not need any change in engine-core.** If it does, the SPI is missing
something — extend the SPI, do not special-case the mode.

## Things that are easy to get wrong (learned the hard way)

- **Invisible walls are two layers.** `minecraft:barrier` blocks (client-side, no
  rubber-banding) **plus** a server-side region check as the safety net (cheats, ender
  pearls). The server bounds are deliberately **wider** than the barrier (20.5 vs 20) —
  if they were equal, normal play would trip the server check and rubber-band.
- **`/pvpadmin setup` clears its volume before building.** It must stay idempotent:
  leftover `barrier` blocks from an old layout are *invisible* and cause "I bump into
  nothing" bugs.
- **Fatal damage is intercepted**, players do not "die": no death screen, no drops.
- **`PlayerSnapshot`** is taken before a match and restored after, so a crash never eats an
  inventory. `MatchService.abortAll()` runs on shutdown.
- **`PlayerMoveEvent` fires constantly** — only do geometry when the player changed block.
- Ratings are stored per **(player, mode, format)**: 1v1 rank ≠ 3v3 rank.

## Build / test

```bash
./gradlew test     # fast: pure logic + SQL against in-memory H2. No MySQL needed.
./gradlew build    # tests + both plugin jars
```
Outputs: `engine-core/build/libs/engine-core-*.jar`, `modes/mode-duel/build/libs/mode-duel-*.jar`

Needs **JDK 25**. If `JAVA_HOME` is older:
`./gradlew build -Porg.gradle.java.installations.paths=/path/to/jdk-25`

## Deployment

See `DEPLOY.md`. Production install is scripted: `deploy/install.sh`.
**Production must run `online-mode=true`** (local dev uses `false` only so one person can
join twice; on a public server it lets anyone impersonate an operator).

## Map format

A map is a world + a `map.yml`. The engine only needs team spawns and the bounds:

```yaml
modes: [duel]        # omit = any mode
rating: {min: 0, max: 1199}   # omit = any rating; picks the arena from player rating
spawns:
  team-0: {x: .., y: .., z: .., yaw: .., pitch: ..}
  team-1: {...}
bounds:
  type: cylinder     # or cuboid / sphere
  center-x: .., center-z: .., radius: .., min-y: .., max-y: ..
```

`/pvpadmin setup N` generates a throwaway dev map (a void world with a lobby and N arenas).
Real designed maps just drop in — no code needed.

## Roadmap

Done: M0 database · M1 lobby+UI · M2 arenas+walls · M3 queue+match+duel · M4 ELO+leaderboard.
Next: M5 teams/parties · M6 rounds+spectators · M7 a 2nd mode (dodgeball) · M8 classes,
**talent trees** (pure domain, TDD) and abilities · M9 proxy/Redis if one box is not enough.
