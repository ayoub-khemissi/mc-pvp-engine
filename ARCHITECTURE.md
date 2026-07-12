# PvP Engine — Architecture & Plan

A modular **engine** that hosts multiple PvP game modes (duels, team fights, dodgeball, …)
with queues, arenas, invisible walls, and a full ELO ranking system.

The engine provides the *plumbing*. A **game mode** is a small plugin that says
"here are my rules"; it should not have to reimplement queues, arenas, ELO, or UI.

---

## 1. Key decisions

| Decision | Choice | Why |
|---|---|---|
| **Platform** | **Paper plugin** (not Forge) | Players join with a **vanilla client, no install**. Mandatory for a public competitive server. Forge would force every player to install a modpack. |
| **MC version** | Target **26.1.2** (stable) now, keep 26.2 on a test server | Paper has 26.2 builds but the ecosystem is still settling. Pin one version; the engine is version-agnostic anyway. |
| **Topology (v1)** | **One Paper server, multiple worlds** | Far simpler. Lobby world + arena world(s). Enough for hundreds of players. |
| **Topology (later)** | Velocity proxy + arena servers + Redis | Only when one box isn't enough. The engine hides this behind interfaces so the switch is not a rewrite. |
| **Database** | **MySQL** + HikariCP, all access async | Profiles, ratings, match history, leaderboards. |
| **Language** | Java 21+ (match your Paper build) | — |
| **Build** | Gradle (Kotlin DSL), multi-module | One repo, several jars. |

> **Design rule that makes everything else work:** keep the *domain logic*
> (ELO math, matchmaking, region shapes, match state machine) as **pure Java with
> zero Bukkit imports**. Bukkit only appears in thin adapter layers. This makes the
> core unit-testable without a server, and swappable later.

---

## 2. The systems (what to modularize)

You listed 4 needs. Here is the full decomposition — 9 core systems + the mode SPI.

| # | System | Responsibility |
|---|---|---|
| 1 | **Match framework** | The heart. Match lifecycle state machine, teams, rounds, win conditions, events. |
| 2 | **Arena / Map loader** | Map templates, allocating an arena for a match, spawns, teleport in/out, reset, release. |
| 3 | **Region / Bounds** | Shapes (cuboid, cylinder, sphere, polygon) + invisible-wall enforcement. |
| 4 | **Queue / Matchmaking** | Per (mode, format) queues, rating-based pairing, team balancing. |
| 5 | **Rating (ELO)** | Rating math, divisions, leaderboards, match history. |
| 6 | **Player state & profiles** | Snapshot/restore inventory & state, cached profile, load on join / flush on quit. |
| 7 | **UI toolkit** | Hotbar items, chest GUIs, scoreboard sidebar, boss bar, titles, sounds. **This is your "no commands" UX.** |
| 8 | **Persistence** | MySQL, HikariCP, migrations, repositories, async. |
| 9 | **Config & admin tools** | YAML config, in-game map setup wand + admin commands. |
| — | **GameMode SPI** | The contract a game mode implements. |

---

## 3. Repository structure

```
pvp-engine/
├── settings.gradle.kts
├── build.gradle.kts                 # shared config (Java version, Paper repo)
│
├── engine-api/                      # ⭐ interfaces + models ONLY. Modes compile against this.
│   └── GameModeDefinition, MatchHandler, MatchContext, Region, Rating…
│
├── engine-domain/                   # ⭐ pure Java, no Bukkit. 100% unit-tested.
│   └── EloCalculator, Matchmaker, MatchStateMachine, Region shapes
│
├── engine-core/                     # the Paper plugin. Implements the API using Bukkit.
│   ├── arena/  queue/  rating/  ui/  storage/  player/  admin/
│   └── plugin.yml   (name: PvPEngine)
│
├── modes/
│   ├── mode-duel/                   # plugin, depends on PvPEngine. 1v1..5v5 vanilla PvP.
│   └── mode-dodgeball/              # later — proves the engine is really modular
│
└── sql/                             # versioned migrations (V1__init.sql, …)
```

**No Docker.** MySQL runs natively — installed on your Windows machine for local dev, and
directly on the OVH box in production. The plugin only needs host / port / database / user /
password in `config.yml`, so the same jar runs in both places.

**Why modes are separate plugins:** you can ship a new game mode without touching
(or restarting the development of) the engine. Each mode declares `depend: [PvPEngine]`
and registers itself on enable.

---

## 4. The GameMode SPI (how a mode plugs in)

A mode implements two things: a **definition** (metadata) and a **handler** (per-match behaviour).

```java
public interface GameModeDefinition {
    String id();                          // "duel"
    Component displayName();
    ItemStack icon();                     // shown in the "Play" menu
    List<Format> formats();               // 1v1, 2v2, 3v3, 5v5
    boolean ranked();                     // does it affect ELO?
    MatchRules defaultRules();            // rounds, time limit, respawn policy
    MatchHandler createHandler(MatchContext ctx);
}

/** Per-match callbacks. Every method has a sane default — a vanilla duel needs almost none. */
public interface MatchHandler {
    void onPrepare(MatchContext ctx);                     // teleport, give kit, freeze
    void onCountdownTick(MatchContext ctx, int secondsLeft);
    void onStart(MatchContext ctx);                       // unfreeze — FIGHT!
    void onRoundStart(MatchContext ctx, int round);
    void onPlayerDeath(MatchContext ctx, Player victim, @Nullable Player killer);
    void onPlayerQuit(MatchContext ctx, Player player);
    void onTick(MatchContext ctx);
    @Nullable MatchOutcome checkWinCondition(MatchContext ctx);  // null = keep playing
    void onRoundEnd(MatchContext ctx, int round, MatchOutcome outcome);
    void onEnd(MatchContext ctx, MatchOutcome outcome);
    void onCleanup(MatchContext ctx);                     // engine handles teleport-back
}
```

`MatchContext` hands the mode everything it needs: the arena, teams, players, rules,
a scoreboard handle, a key-value state store, and messaging helpers.

**The engine ships default implementations** so a plain PvP mode is ~50 lines:
- default win condition: **last team standing**
- default kit: from YAML
- default countdown, freeze, titles, teleport, ELO application, cleanup

Registering a mode:
```java
@Override public void onEnable() {
    PvPEngine.api().modes().register(new DuelMode(this));
}
```

---

## 5. Match lifecycle (state machine)

```
QUEUED
  └─► MATCH_FOUND ─► ALLOCATING_ARENA ─► PREPARING ─► COUNTDOWN(3,2,1) ─► LIVE
                                                            ▲               │
                                                            │        (round ends)
                                                            └── ROUND_ENDING ◄┘
                                                                     │ (all rounds done)
                                                                     ▼
                                                ENDING ─► RESULTS(ELO) ─► CLEANUP ─► CLOSED
```

Each transition fires a Bukkit event (`MatchStartEvent`, `RoundEndEvent`, `MatchEndEvent`, …)
so **other features (stats, cosmetics, parties) hook in without coupling**.

---

## 6. Arena / Map loader

Two strategies behind one interface — **start with the simple one**.

```java
public interface ArenaProvider {
    CompletableFuture<ArenaInstance> allocate(String mapId);
    void release(ArenaInstance instance);
}
```

| Strategy | How | When |
|---|---|---|
| **StaticArenaProvider** ✅ **v1** | Pre-built arenas placed far apart in one `arenas` world. Allocation = pick a free slot. Reset = clear items/entities, restore player state. | Perfect for PvP where **nobody breaks blocks** — which is your case. Zero disk I/O, instant. |
| **InstancedArenaProvider** (later) | Copy a template world folder → load a fresh world per match → unload + delete after. | Needed only for modes that damage terrain (bedwars-like, buildable). |

**Map definition** — `maps/<mapId>/map.yml`:
```yaml
id: colosseum
display-name: "Colosseum"
modes: [duel, dodgeball]          # which modes can use this map
world: arenas
spawns:
  team-0: {x: 100.5, y: 65, z: 0.5, yaw: -90}
  team-1: {x: 140.5, y: 65, z: 0.5, yaw: 90}
bounds:
  type: cylinder                   # cuboid | cylinder | sphere | polygon
  center: {x: 120, y: 64, z: 0}
  radius: 25
  min-y: 60
  max-y: 90
```

Admin builds maps in-game with a **selection wand** + `/pvpadmin map …` (commands are
fine for admins; the *players* never type commands).

---

## 7. Region / invisible walls

Pure-Java shapes, unit-testable:

```java
public sealed interface Region permits Cuboid, Cylinder, Sphere, Polygon {
    boolean contains(double x, double y, double z);
    Vec3 nearestInside(Vec3 point);
    Vec3 center();
}
```

**Enforcement** (`WallService`):
- Listen to `PlayerMoveEvent`, but only act when the player actually **changed block** (perf).
- If the destination is outside the region → **cancel the move + push velocity back toward center + action-bar message**. This feels like a solid wall.
- Also guard: ender pearls landing outside (cancel teleport), and a Y-ceiling/floor.
- Optional polish: render a faint particle wall when a player gets close.

---

## 8. Queue & matchmaking

```java
record QueueEntry(UUID playerOrParty, int rating, long joinedAt) {}
```

- One queue per **(modeId, format)** — e.g. `duel:1v1`, `duel:3v3`.
- Matchmaker ticks every second:
  - Start with a tight rating window (±50).
  - **Widen the window the longer you wait** (+25 every 5s, capped at ±500) so nobody waits forever.
  - Build balanced teams (snake-draft by rating for 2v2+).
- On match found → allocate arena → `MATCH_FOUND` → PREPARING.
- Player leaves the queue by right-clicking the "Leave queue" item (no command).

Pure `Matchmaker` class = fully unit-testable with fake players.

---

## 9. ELO & ranking

```java
public interface RatingCalculator { RatingChange compute(TeamRating a, TeamRating b, Outcome o); }
public final class EloCalculator implements RatingCalculator { … }
```

- Standard Elo: `expected = 1 / (1 + 10^((Ropp − R)/400))`, `newR = R + K × (score − expected)`.
- **Team matches:** compare the two teams' **average** rating, then apply the delta to each member.
- **Dynamic K**: `K=40` for the first 10 games (placement), `K=20` under 30 games, `K=10` after — so new players converge fast and veterans are stable.
- **Ratings are per (mode, format)** — your 1v1 rank is not your 3v3 rank.
- **Divisions** (config-driven thresholds): Bronze → Silver → Gold → Platinum → Diamond → Master → Grandmaster.
- Leaving a live ranked match = **loss** (anti rage-quit).
- Leaderboard: cached top-100 per (mode, format), refreshed every ~60s (never query on the main thread).

---

## 10. UI toolkit — your "no commands" requirement

| Surface | Use |
|---|---|
| **Hotbar items** (lobby) | slot 0 = **Play** (compass) → opens mode menu · slot 4 = **Profile / Rank** (book) → stats + leaderboard · slot 8 = **Leave queue** (barrier, only while queued) |
| **Chest GUI** (`MenuService`) | Mode select → format select (1v1/2v2/…) → confirm. Also profile, leaderboard, (later) class & spell selection. Buttons with click handlers + pagination. |
| **Scoreboard sidebar** | Lobby: rank, rating, W/L, queue status. In match: round, score, time left. |
| **Boss bar** | Countdown and match timer. |
| **Titles / action bar / sounds** | `3 · 2 · 1 · FIGHT!`, `VICTORY` / `DEFEAT`, `+18 ELO`. |

Lobby inventory is **locked** (cancel drop/move) so the items can't be lost.

---

## 11. Database (MySQL)

```sql
CREATE TABLE players (
  uuid        BINARY(16) PRIMARY KEY,
  username    VARCHAR(16) NOT NULL,
  first_seen  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username)
);

CREATE TABLE ratings (
  uuid        BINARY(16) NOT NULL,
  mode_id     VARCHAR(32) NOT NULL,
  format      VARCHAR(8)  NOT NULL,          -- '1v1', '2v2', …
  rating      INT NOT NULL DEFAULT 1000,
  peak_rating INT NOT NULL DEFAULT 1000,
  games       INT NOT NULL DEFAULT 0,
  wins        INT NOT NULL DEFAULT 0,
  losses      INT NOT NULL DEFAULT 0,
  streak      INT NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, mode_id, format),
  INDEX idx_leaderboard (mode_id, format, rating DESC),
  FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

CREATE TABLE matches (
  id          BINARY(16) PRIMARY KEY,
  mode_id     VARCHAR(32) NOT NULL,
  format      VARCHAR(8)  NOT NULL,
  map_id      VARCHAR(64) NOT NULL,
  ranked      BOOLEAN NOT NULL DEFAULT TRUE,
  started_at  TIMESTAMP NOT NULL,
  ended_at    TIMESTAMP NULL,
  winner_team TINYINT NULL,
  end_reason  VARCHAR(24) NOT NULL,          -- WIN | DRAW | FORFEIT | ABORTED
  INDEX idx_mode_time (mode_id, started_at)
);

CREATE TABLE match_participants (
  match_id      BINARY(16) NOT NULL,
  uuid          BINARY(16) NOT NULL,
  team          TINYINT NOT NULL,
  result        ENUM('WIN','LOSS','DRAW') NOT NULL,
  rating_before INT NOT NULL,
  rating_after  INT NOT NULL,
  kills         INT NOT NULL DEFAULT 0,
  deaths        INT NOT NULL DEFAULT 0,
  PRIMARY KEY (match_id, uuid),
  INDEX idx_history (uuid, match_id),
  FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE
);
```

**Rules:**
- **Never touch the DB on the main thread.** HikariCP + a dedicated executor, `CompletableFuture`.
- Load the profile **async on join**; cache in memory; flush on match end + on quit.
- Versioned migrations (Flyway or a simple `V1__init.sql` runner).

---

## 12. End-to-end flow (the target for v1)

```
Player joins  → profile loaded (async) → teleported to LOBBY → lobby kit + hotbar + sidebar
Right-click COMPASS → "Play" GUI → choose Duel → choose 1v1 → joins queue
Sidebar shows "In queue — 0:12 — searching ±75"
Matchmaker pairs two players of similar rating
→ arena allocated → both teleported, frozen, given the kit
→ boss bar + titles: 3 · 2 · 1 · FIGHT!
→ they fight, invisible walls keep them inside the ring
→ one dies → last team standing → MatchOutcome
→ VICTORY / DEFEAT title, "+18 ELO" / "−15 ELO"
→ ratings written to MySQL, match + participants recorded
→ 3s later: teleported back to LOBBY, state restored, arena released
```

---

## 13. Testing strategy — TDD (non-negotiable)

The whole point of putting the logic in a **Bukkit-free `engine-domain`** is that it can be
tested in milliseconds, with no server. That is your regression net.

| Layer | How it's tested | Speed |
|---|---|---|
| **`engine-domain`** — Elo, matchmaking, regions, **talent trees**, match state machine | **JUnit 5 unit tests, written first (red → green)** | ms — run on every change |
| **`engine-core` adapters** — listeners, GUIs, repositories | MockBukkit / thin integration tests; keep these classes dumb so there is little to test | seconds |
| **End-to-end** | A real Paper test server: queue → arena → fight → ELO | manual, per milestone |

**Rules we follow:**
1. A new domain rule starts as a **failing test**, then the implementation.
2. Any bug found in game gets a **regression test in `engine-domain` first**, then the fix.
3. `./gradlew test` must be green before anything is deployed. Later: CI on push.
4. Bukkit-facing classes stay *thin* — if a class is hard to test, the logic belongs in the domain.

**Already green:** `EloCalculatorTest` — 11 tests (expectation maths, K-factor by experience,
team averaging, rating floor, "beating a stronger opponent is worth more").

---

## 14. Classes, talents & builds (the talent tree)

Yes — this is planned, and it fits the architecture cleanly. A talent tree is a
**graph with prerequisites and a points budget**: pure logic, zero Bukkit → the
ideal TDD target, exactly like Elo.

```java
// engine-domain — pure, unit-tested
record TalentNode(String id, String name, int tier, int maxRank, int cost, Set<String> requires) {}

final class TalentTree {                  // validates: unknown prereqs, cycles
    String classId();
    Collection<TalentNode> nodes();
}

record TalentBuild(String classId, Map<String, Integer> ranks) {}   // nodeId -> rank

final class TalentValidator {
    ValidationResult validate(TalentTree tree, TalentBuild build, int availablePoints);
    boolean canUnlock(TalentTree tree, TalentBuild build, String nodeId, int availablePoints);
}
```

**Rules the validator enforces** (each one a unit test):
- a node's rank never exceeds `maxRank`
- every prerequisite must have at least 1 rank
- **tier gating**: to spend in tier *N* you must have spent *N × pointsPerTier* points below it
- total spent (`rank × cost`) ≤ available points
- the tree itself must be acyclic and reference only known nodes

**The UI**: a chest GUI where **each row is a tier** and each item is a node —
grey = locked, white = available, green = ranked (`2/3`). Left-click spends a point,
shift-click refunds. A "Save build" button persists it. The player picks a **class**,
then freely spends points → *their* build. Multiple saved builds ("loadouts") per class.

**Persistence**: `player_builds(uuid, class_id, build_name, ranks_json, active)`.

**Later**, talents bind to **abilities** (cooldown, trigger, effect) — the ability system
reads the validated build and grants the corresponding abilities on match start.

---

## 15. Roadmap (build order = your order)

| Milestone | Deliverable | Done when… |
|---|---|---|
| **M0 — Bootstrap** | Gradle multi-module, Paper plugin loads, config, **native MySQL** + migrations | Plugin enables on a Paper server; DB connects |
| **M1 — Lobby** | Lobby world, player state save/restore, lobby kit, **hotbar items + GUI framework + sidebar** | Join → lobby → compass opens a menu |
| **M2 — Arenas & walls** | `map.yml`, StaticArenaProvider, **Region shapes + invisible walls**, admin wand | You cannot walk out of the ring |
| **M3 — Match + Queue** | Match state machine, queue + matchmaker, **Duel 1v1 (vanilla kit)** | Full loop: queue → arena → 3·2·1 → fight → back to lobby |
| **M4 — ELO** | Elo calc, divisions, MySQL persistence, **profile GUI + leaderboard GUI** | Winner gains rating, leaderboard shows top players |
| **M5 — Teams** | 2v2 / 3v3 / 5v5, team balancing, parties | 3v3 queue works |
| **M6 — Rounds & spectators** | Best-of-N, spectate a live match | Bo3 duel works |
| **M7 — Second mode** | **Dodgeball** plugin — proves modularity (no engine changes needed) | New mode added without touching engine-core |
| **M8 — Classes, talents & builds** | Class select GUI, **talent tree GUI**, `TalentValidator` (TDD), saved builds in MySQL, abilities with cooldowns | A player picks a class, spends talent points, saves a build, and fights with it |
| **M9 — Scale-out** *(only if needed)* | Velocity proxy, arena servers, Redis queue | 1000+ concurrent |

**M1 → M4 is your MVP** = a working ranked 1v1 server.

---

## 16. Risks & gotchas (decide these early)

| Risk | Mitigation |
|---|---|
| **Main-thread stalls** | All DB + file I/O async. Never `Bukkit.createWorld` mid-match (another reason static arenas win in v1). |
| **Combat feel** | 1.9+ has attack cooldown. If you want "1.8 PvP", tune the `generic.attack_speed` attribute in the kit. **Decide this before tuning kits.** |
| **Disconnect abuse** | Quit during a live ranked match = loss + rating applied. Short reconnect grace (~30s) optional. |
| **Arena not clean** | On release: clear dropped items, arrows, remove effects, heal, restore state. |
| **Crash recovery** | On startup: clear stale match state, teleport orphaned players to lobby, free all arena slots. |
| **Smurfs / boosting** | Placement games with high K; rate-limit rematches vs the same opponent. |
| **Cheating** | Plan for an external anti-cheat; the engine should not try to be one. |

---

## 15. Tech stack

- **Paper API** (server), Java 21+
- **Gradle** (Kotlin DSL), multi-module, shadow for shading deps
- **HikariCP** + MySQL connector, **Flyway** (migrations)
- **Adventure** (bundled in Paper) for text/titles/bossbars
- **JUnit 5** for `engine-domain` (Elo, matchmaking, regions — no server needed)
