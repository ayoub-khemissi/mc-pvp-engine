package fr.ayoub.pvp.mode.fortress.match;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which votes are running, and how a spectator opens theirs.
 *
 * <b>Double-sneak</b>, and for the same reason the match spectator uses it: a single sneak is
 * how you fly <b>down</b>. Bind the vote screen to one sneak and a player cannot descend to
 * look at the ground floor of the fortress they are inspecting — which is the whole point of
 * letting them fly through the walls in the first place.
 *
 * It is also the only key a spectator can send at all: Minecraft hides their hotbar and takes
 * the 1-9 keys for its own spectator menu.
 */
public final class VoteRegistry implements Listener {

    private static final long DOUBLE_SNEAK_MILLIS = 500L;

    private final Set<VotePhase> running = new HashSet<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();

    void open(VotePhase phase) {
        running.add(phase);
    }

    void close(VotePhase phase) {
        running.remove(phase);
    }

    Optional<VotePhase> voteOf(Player player) {
        return running.stream()
                .filter(phase -> !phase.isOver())
                .filter(phase -> !phase.optionsFor(player).isEmpty())
                .findFirst();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();

        Optional<VotePhase> phase = voteOf(player);
        if (phase.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = lastSneak.put(player.getUniqueId(), now);

        if (previous != null && now - previous <= DOUBLE_SNEAK_MILLIS) {
            lastSneak.remove(player.getUniqueId());
            new VoteMenu(phase.get(), player).open(player);
        }
    }
}
