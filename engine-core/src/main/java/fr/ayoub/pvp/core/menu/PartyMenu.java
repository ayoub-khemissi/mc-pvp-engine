package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.party.Invite;
import fr.ayoub.pvp.domain.party.Party;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The party screen: who is in it, who invited you, and the two or three buttons that
 * are all a player ever needs. No commands.
 */
public final class PartyMenu extends Menu {

    private static final int SLOT_INVITE = 29;
    private static final int SLOT_ANSWER = 31;
    private static final int SLOT_LEAVE = 33;

    private final PvPEnginePlugin plugin;

    public PartyMenu(PvPEnginePlugin plugin) {
        super(Component.text("Party", NamedTextColor.LIGHT_PURPLE), MenuLayout.bordered(4));
        this.plugin = plugin;
    }

    @Override
    protected void build(Player viewer) {
        Optional<Party> party = plugin.parties().partyOf(viewer);

        if (party.isPresent()) {
            showMembers(viewer, party.get());
        } else {
            set(layout().slotAt(4), Icons.of(Material.GRAY_DYE,
                    Component.text("You are not in a party", NamedTextColor.GRAY),
                    Component.text("Invite a friend to start one.", NamedTextColor.DARK_GRAY)));
        }

        showInviteButton(viewer, party.orElse(null));
        showPendingInvite(viewer);
        showLeaveButton(viewer, party.orElse(null));
    }

    private void showMembers(Player viewer, Party party) {
        List<UUID> members = party.members();

        for (int i = 0; i < members.size() && i < layout().itemsPerPage(); i++) {
            UUID member = members.get(i);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(member);
            boolean isLeader = party.isLeader(member);
            boolean viewerLeads = party.isLeader(viewer.getUniqueId());
            boolean self = member.equals(viewer.getUniqueId());

            Component role = isLeader
                    ? Component.text("Leader", NamedTextColor.GOLD)
                    : Component.text("Member", NamedTextColor.GRAY);

            Component action = viewerLeads && !self
                    ? Component.text("Click to remove", NamedTextColor.RED)
                    : Component.empty();

            set(layout().slotAt(i), Icons.head(owner,
                            Component.text(name(owner), NamedTextColor.WHITE), role, action),
                    event -> {
                        if (viewerLeads && !self) {
                            plugin.parties().kick(viewer, member);
                            refresh(viewer);
                        }
                    });
        }
    }

    private void showInviteButton(Player viewer, Party party) {
        boolean mayInvite = party == null || (party.isLeader(viewer.getUniqueId()) && !party.isFull());

        if (!mayInvite) {
            set(SLOT_INVITE, Icons.of(Material.GRAY_DYE,
                    Component.text("Cannot invite", NamedTextColor.DARK_GRAY),
                    Component.text(party.isFull()
                            ? "The party is full (" + party.maxSize() + ")."
                            : "Only the leader can invite.", NamedTextColor.GRAY)));
            return;
        }

        set(SLOT_INVITE, Icons.of(Material.PLAYER_HEAD,
                        Component.text("Invite a player", NamedTextColor.GREEN),
                        Component.text("Up to " + plugin.parties().maxSize() + " per party",
                                NamedTextColor.GRAY)),
                event -> new InviteMenu(plugin).open(viewer));
    }

    private void showPendingInvite(Player viewer) {
        Optional<Invite> invite = plugin.parties().pendingInvite(viewer);
        if (invite.isEmpty()) {
            return;
        }

        String from = name(Bukkit.getOfflinePlayer(invite.get().from()));

        set(SLOT_ANSWER, Icons.of(Material.LIME_DYE,
                        Component.text("Accept " + from + "'s invitation", NamedTextColor.GREEN),
                        Component.text("Right-click to decline instead", NamedTextColor.DARK_GRAY)),
                event -> {
                    if (event.isRightClick()) {
                        plugin.parties().decline(viewer);
                    } else {
                        plugin.parties().accept(viewer);
                    }
                    refresh(viewer);
                });
    }

    private void showLeaveButton(Player viewer, Party party) {
        if (party == null) {
            return;
        }

        boolean leads = party.isLeader(viewer.getUniqueId());
        boolean alone = party.size() == 1;

        if (leads && !alone) {
            set(SLOT_LEAVE, Icons.of(Material.BARRIER,
                            Component.text("Disband the party", NamedTextColor.RED),
                            Component.text("Everyone is sent back to the lobby queue-free",
                                    NamedTextColor.GRAY)),
                    event -> {
                        plugin.parties().disband(viewer);
                        viewer.closeInventory();
                    });
            return;
        }

        set(SLOT_LEAVE, Icons.of(Material.BARRIER,
                        Component.text("Leave the party", NamedTextColor.RED)),
                event -> {
                    plugin.parties().leave(viewer);
                    viewer.closeInventory();
                });
    }

    private static String name(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : "?";
    }
}
