package fr.ayoub.pvp.core.queue;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.lobby.HotbarItems;
import fr.ayoub.pvp.core.ui.Sidebar;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.party.Party;
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
 * A queue entry is a {@link Ticket}, which is a <b>group</b>: a lone player is a group of
 * one, a party is a group of several. The party leader queues, and the whole party goes in
 * as a single ticket — that is what guarantees friends end up on the same team.
 *
 * Ticks once a second and asks the (unit-tested) Matchmaker whether a match can be formed.
 * The engine only does the Bukkit part: reading the ratings, teleporting, messaging.
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
        Optional<Party> party = plugin.parties().partyOf(player);
        List<Player> group = party.map(plugin.parties()::onlineMembers).orElse(List.of(player));

        if (!mayQueue(player, party.orElse(null), group, format)) {
            return;
        }

        group.forEach(this::leave);   // never be in two queues

        String key = key(mode, format);
        List<UUID> members = group.stream().map(Player::getUniqueId).toList();
        UUID ticketId = party.map(Party::id).orElse(player.getUniqueId());

        // The ratings come from the database — read them off the main thread.
        plugin.async().execute(() -> {
            int rating = averageRating(members, mode, format);

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Everything may have changed while we were on the database thread.
                List<Player> still = group.stream()
                        .filter(Player::isOnline)
                        .filter(member -> !plugin.matches().isInMatch(member))
                        .toList();

                if (still.size() != members.size()) {
                    player.sendMessage(Component.text(
                            "Your party changed — queue again.", NamedTextColor.RED));
                    return;
                }

                long now = System.currentTimeMillis();

                queues.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new Ticket(ticketId, members, rating, now));
                matchmakers.computeIfAbsent(key, k -> new Matchmaker(format, WINDOW));

                Component queued = Component.text("Queued for ", NamedTextColor.GREEN)
                        .append(Component.text(mode.id() + " " + format.id(), NamedTextColor.WHITE))
                        .append(Component.text(
                                members.size() > 1
                                        ? " — party of " + members.size() + ", rating " + rating
                                        : " — rating " + rating,
                                NamedTextColor.GRAY));

                for (Player member : still) {
                    byPlayer.put(member.getUniqueId(), new Entry(key, mode, format, now));
                    member.sendMessage(queued);
                    member.closeInventory();

                    // A barrier appears in the hotbar to leave the queue — still no commands.
                    member.getInventory().setItem(
                            HotbarItems.SLOT_LEAVE_QUEUE, plugin.hotbar().leaveQueue());
                    Sidebar.update(plugin, member);
                }
            });
        });
    }

    /** Every reason a group cannot enter this queue, each with its own message. */
    private boolean mayQueue(Player player, Party party, List<Player> group, Format format) {
        if (plugin.matches().isInMatch(player)) {
            player.sendMessage(Component.text("You are already in a match.", NamedTextColor.RED));
            return false;
        }
        if (plugin.matches().isSpectating(player)) {
            player.sendMessage(Component.text(
                    "Stop spectating first — sneak (Shift) to leave.", NamedTextColor.RED));
            return false;
        }

        if (party != null) {
            if (!party.isLeader(player.getUniqueId())) {
                player.sendMessage(Component.text(
                        "Only the party leader can start a queue.", NamedTextColor.RED));
                return false;
            }
            if (group.size() < party.size()) {
                player.sendMessage(Component.text(
                        "A member of your party is offline.", NamedTextColor.RED));
                return false;
            }
            if (group.size() > format.playersPerTeam()) {
                player.sendMessage(Component.text("Your party has " + group.size()
                        + " players — a " + format.id() + " team only holds "
                        + format.playersPerTeam() + ".", NamedTextColor.RED));
                return false;
            }
            for (Player member : group) {
                if (plugin.matches().isInMatch(member)) {
                    player.sendMessage(Component.text(
                            member.getName() + " is in a match.", NamedTextColor.RED));
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A party queues on its <b>average</b> rating: three golds and a bronze is a gold-level
     * team, and it is matched against gold-level teams.
     */
    private int averageRating(List<UUID> members, GameModeDefinition mode, Format format) {
        int total = 0;
        for (UUID member : members) {
            total += plugin.ratings()
                    .find(member, mode.id(), format.id())
                    .map(row -> row.rating())
                    .orElse(PvPEnginePlugin.STARTING_RATING);
        }
        return Math.round((float) total / members.size());
    }

    /**
     * Leave the queue.
     *
     * A ticket is a group, so this pulls the <b>whole</b> ticket out: a party queued
     * together and leaves together. One member leaving would otherwise leave a ticket
     * claiming players it no longer has.
     */
    public void leave(Player player) {
        Entry entry = byPlayer.get(player.getUniqueId());
        if (entry == null) {
            return;
        }

        List<Ticket> queue = queues.get(entry.key());
        if (queue == null) {
            byPlayer.remove(player.getUniqueId());
            return;
        }

        Ticket ticket = queue.stream()
                .filter(queued -> queued.members().contains(player.getUniqueId()))
                .findFirst()
                .orElse(null);

        if (ticket == null) {
            byPlayer.remove(player.getUniqueId());
            return;
        }

        queue.remove(ticket);
        for (UUID member : ticket.members()) {
            byPlayer.remove(member);

            Player online = Bukkit.getPlayer(member);
            if (online == null) {
                continue;
            }
            online.getInventory().setItem(HotbarItems.SLOT_LEAVE_QUEUE, null);
            if (!online.equals(player)) {
                online.sendMessage(Component.text(player.getName() + " left the queue.",
                        NamedTextColor.YELLOW));
            }
            Sidebar.update(plugin, online);
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
                if (pairing.isEmpty() || !startMatch(pairing.get(), queue)) {
                    break;
                }
            }
        }
    }

    private boolean startMatch(Pairing pairing, List<Ticket> queue) {
        Entry any = byPlayer.get(pairing.allPlayers().getFirst());
        if (any == null) {
            return false;
        }

        List<List<UUID>> teams = new ArrayList<>();
        for (int team = 0; team < pairing.teams().size(); team++) {
            teams.add(pairing.teamMembers(team));
        }

        // The map is picked from the players' level, so a bronze duel and a legend duel
        // do not happen on the same arena. Averaged per player: a party of 3 counts 3 times.
        int players = pairing.allPlayers().size();
        int total = pairing.allTickets().stream()
                .mapToInt(ticket -> ticket.rating() * ticket.size())
                .sum();
        int averageRating = Math.round((float) total / players);

        Optional<?> started = plugin.matches().start(any.mode(), any.format(), teams, averageRating);
        if (started.isEmpty()) {
            // No free arena — leave everyone queued and try again next tick.
            return false;
        }

        for (Ticket ticket : pairing.allTickets()) {
            queue.remove(ticket);
            ticket.members().forEach(byPlayer::remove);
        }
        return true;
    }
}
