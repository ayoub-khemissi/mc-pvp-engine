package fr.ayoub.pvp.core.party;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.domain.party.Invite;
import fr.ayoub.pvp.domain.party.Party;
import fr.ayoub.pvp.domain.party.PartyRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parties, on the Bukkit side.
 *
 * All the rules live in the (unit-tested) {@link PartyRegistry}: this class only turns
 * UUIDs into players, and answers into messages and sounds.
 */
public final class PartyService {

    /** An invite nobody answers dies by itself. */
    private static final long INVITE_TTL_MILLIS = 60_000L;

    private final PvPEnginePlugin plugin;
    private final PartyRegistry registry;

    public PartyService(PvPEnginePlugin plugin, int maxPartySize) {
        this.plugin = plugin;
        this.registry = new PartyRegistry(maxPartySize, INVITE_TTL_MILLIS);
    }

    public int maxSize() {
        return registry.maxPartySize();
    }

    public Optional<Party> partyOf(Player player) {
        return registry.partyOf(player.getUniqueId());
    }

    public boolean isLeader(Player player) {
        return partyOf(player).map(party -> party.isLeader(player.getUniqueId())).orElse(false);
    }

    public Optional<Invite> pendingInvite(Player player) {
        return registry.pendingInvite(player.getUniqueId());
    }

    /** The members who are actually connected right now. */
    public List<Player> onlineMembers(Party party) {
        List<Player> players = new ArrayList<>();
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    // --- actions ---------------------------------------------------------------------

    public void invite(Player inviter, Player target) {
        if (plugin.queue().isQueued(inviter) || plugin.matches().isInMatch(inviter)) {
            inviter.sendMessage(error("Leave the queue before inviting someone."));
            return;
        }
        if (plugin.matches().isInMatch(target)) {
            inviter.sendMessage(error(target.getName() + " is in a match."));
            return;
        }

        if (!registry.invite(inviter.getUniqueId(), target.getUniqueId(), System.currentTimeMillis())) {
            inviter.sendMessage(error(reasonInviteFailed(inviter, target)));
            return;
        }

        inviter.sendMessage(Component.text("Invited ", NamedTextColor.GREEN)
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(" to your party.", NamedTextColor.GREEN)));

        target.sendMessage(Component.text(inviter.getName(), NamedTextColor.WHITE)
                .append(Component.text(" invited you to their party.", NamedTextColor.LIGHT_PURPLE)));
        target.sendMessage(Component.text("Open your ", NamedTextColor.GRAY)
                .append(Component.text("Party", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" item to accept — it expires in 60s.", NamedTextColor.GRAY)));
        target.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
    }

    /** Why the registry said no — so the player is never left guessing. */
    private String reasonInviteFailed(Player inviter, Player target) {
        if (inviter.equals(target)) {
            return "You cannot invite yourself.";
        }
        if (registry.partyOf(target.getUniqueId()).isPresent()) {
            return target.getName() + " is already in a party.";
        }
        return partyOf(inviter)
                .filter(Party::isFull)
                .map(party -> "Your party is full (" + party.maxSize() + ").")
                .orElse("Only the party leader can invite.");
    }

    public void accept(Player player) {
        Optional<Invite> invite = pendingInvite(player);

        if (!registry.accept(player.getUniqueId(), System.currentTimeMillis())) {
            player.sendMessage(error(invite.isPresent()
                    ? "That invitation has expired."
                    : "You have no pending invitation."));
            return;
        }

        Party party = partyOf(player).orElseThrow();
        announce(party, Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" joined the party.", NamedTextColor.GREEN)));
        onlineMembers(party).forEach(member ->
                member.playSound(member, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f));
    }

    public void decline(Player player) {
        registry.decline(player.getUniqueId());
        player.sendMessage(Component.text("Invitation declined.", NamedTextColor.YELLOW));
    }

    /**
     * Leave the party — voluntarily, or because the player disconnected.
     *
     * A party in a queue leaves it: the ticket was built for a group of N, and it is no
     * longer that group.
     */
    public void leave(Player player) {
        Optional<Party> party = partyOf(player);
        if (party.isEmpty()) {
            return;
        }

        plugin.queue().leave(player);   // the whole party is dequeued
        registry.leave(player.getUniqueId());

        Component left = Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" left the party.", NamedTextColor.YELLOW));
        announce(party.get(), left);

        if (player.isOnline() && !party.get().isEmpty()) {
            player.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW));
        }
    }

    /** The leader kicking someone, or disbanding. */
    public void kick(Player leader, UUID target) {
        Party party = partyOf(leader).orElse(null);
        if (party == null || !party.isLeader(leader.getUniqueId()) || !party.contains(target)) {
            return;
        }

        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) {
            leave(kicked);
            kicked.sendMessage(error("You were removed from the party."));
        } else {
            plugin.queue().leave(leader);
            registry.leave(target);
        }
    }

    public void disband(Player leader) {
        Party party = partyOf(leader).orElse(null);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            return;
        }

        plugin.queue().leave(leader);
        announce(party, Component.text("The party was disbanded.", NamedTextColor.YELLOW));
        registry.disband(party);
    }

    /** Called once a second — drops the invites nobody answered. */
    public void tick() {
        registry.expire(System.currentTimeMillis());
    }

    public void announce(Party party, Component message) {
        onlineMembers(party).forEach(member -> member.sendMessage(message));
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
