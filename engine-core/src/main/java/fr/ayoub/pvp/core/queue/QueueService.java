package fr.ayoub.pvp.core.queue;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.lobby.HotbarItems;
import fr.ayoub.pvp.core.ui.Sidebar;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.queue.Matchmaker;
import fr.ayoub.pvp.domain.queue.Pairing;
import fr.ayoub.pvp.domain.queue.RatingWindow;
import fr.ayoub.pvp.domain.queue.Ticket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The queues. One per (mode, format) — "duel:1v1" is not "duel:3v3".
 *
 * Ticks once a second and asks the (unit-tested) Matchmaker whether a match can be
 * formed. The engine only does the Bukkit part: reading the rating, teleporting,
 * messaging.
 */
public final class QueueService {

    /** ±50 to start, +25 for every 5 seconds waited, never beyond ±500. */
    private static final RatingWindow WINDOW = new RatingWindow(50, 25, 500);

    private final PvPEnginePlugin plugin;

    private final Map<String, List<Ticket>> queues = new HashMap<>();
    private final Map<String, Matchmaker> matchmakers = new HashMap<>();
    private final Map<UUID, Entry> byPlayer = new HashMap<>();

    private record Entry(String key, GameModeDefinition mode, Format format, long joinedAt) {
    }

    public QueueService(PvPEnginePlugin plugin) {
        this.plugin = plugin;
    }

    private static String key(GameModeDefinition mode, Format format) {
        return mode.id() + ":" + format.id();
    }

    public boolean isQueued(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    public Optional<String> queueNameOf(Player player) {
        Entry entry = byPlayer.get(player.getUniqueId());
        return entry == null
                ? Optional.empty()
                : Optional.of(entry.mode().id() + " " + entry.format().id());
    }

    public long waitedSeconds(Player player) {
        Entry entry = byPlayer.get(player.getUniqueId());
        return entry == null ? 0 : (System.currentTimeMillis() - entry.joinedAt()) / 1000;
    }

    // --- joining ---------------------------------------------------------------

    public void join(Player player, GameModeDefinition mode, Format format) {
        if (plugin.matches().isInMatch(player)) {
            player.sendMessage(Component.text("You are already in a match.", NamedTextColor.RED));
            return;
        }
        leave(player);   // never be in two queues

        UUID id = player.getUniqueId();
        String key = key(mode, format);

        // The rating comes from the database — read it off the main thread.
        plugin.async().execute(() -> {
            int rating = plugin.ratings()
                    .find(id, mode.id(), format.id())
                    .map(row -> row.rating())
                    .orElse(PvPEnginePlugin.STARTING_RATING);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || plugin.matches().isInMatch(player)) {
                    return;
                }
                long now = System.currentTimeMillis();

                queues.computeIfAbsent(key, k -> new ArrayList<>()).add(new Ticket(id, rating, now));
                matchmakers.computeIfAbsent(key, k -> new Matchmaker(format, WINDOW));
                byPlayer.put(id, new Entry(key, mode, format, now));

                player.sendMessage(Component.text("Queued for ", NamedTextColor.GREEN)
                        .append(Component.text(mode.id() + " " + format.id(), NamedTextColor.WHITE))
                        .append(Component.text(" — rating " + rating, NamedTextColor.GRAY)));
                player.closeInventory();

                // A barrier appears in the hotbar to leave the queue — still no commands.
                player.getInventory().setItem(
                        HotbarItems.SLOT_LEAVE_QUEUE, plugin.hotbar().leaveQueue());
                Sidebar.update(plugin, player);
            });
        });
    }

    public void leave(Player player) {
        Entry entry = byPlayer.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        List<Ticket> queue = queues.get(entry.key());
        if (queue != null) {
            queue.removeIf(ticket -> ticket.id().equals(player.getUniqueId()));
        }
    }

    // --- the tick --------------------------------------------------------------

    /** Called once a second. */
    public void tick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, List<Ticket>> entry : queues.entrySet()) {
            List<Ticket> queue = entry.getValue();
            Matchmaker matchmaker = matchmakers.get(entry.getKey());
            if (matchmaker == null || queue.isEmpty()) {
                continue;
            }

            // Keep forming matches while the queue allows it.
            while (true) {
                Optional<Pairing> pairing = matchmaker.tryForm(queue, now);
                if (pairing.isEmpty() || !startMatch(entry.getKey(), pairing.get(), queue)) {
                    break;
                }
            }
        }
    }

    private boolean startMatch(String key, Pairing pairing, List<Ticket> queue) {
        Entry any = byPlayer.get(pairing.allTickets().getFirst().id());
        if (any == null) {
            return false;
        }

        List<List<UUID>> teams = pairing.teams().stream()
                .map(team -> team.stream().map(Ticket::id).toList())
                .toList();

        // The map is picked from the players' level, so a bronze duel and a legend duel
        // do not happen on the same arena.
        int averageRating = (int) Math.round(pairing.allTickets().stream()
                .mapToInt(Ticket::rating)
                .average()
                .orElse(PvPEnginePlugin.STARTING_RATING));

        Optional<?> started = plugin.matches().start(any.mode(), any.format(), teams, averageRating);
        if (started.isEmpty()) {
            // No free arena — leave everyone queued and try again next tick.
            return false;
        }

        for (Ticket ticket : pairing.allTickets()) {
            queue.removeIf(queued -> queued.id().equals(ticket.id()));
            byPlayer.remove(ticket.id());
        }
        return true;
    }
}
