rootProject.name = "pvp-engine"

include("engine-api")
include("engine-domain")
include("engine-storage")
include("engine-core")

// Game modes. Each one is its own plugin — the engine knows nothing about them.
include("modes:mode-duel")
