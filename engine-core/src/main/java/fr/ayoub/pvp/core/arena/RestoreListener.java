package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.core.match.Match;
import fr.ayoub.pvp.core.match.MatchService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.Optional;

/**
 * Watches a destructible arena and writes down what changes, so it can be put back.
 *
 * <p><b>Everything here fires at LOWEST priority, and none of it ignores cancelled events.</b>
 * Both are deliberate, and both are the bug that would otherwise be almost impossible to see:
 * a block has to be photographed <b>before</b> it changes. Listen at MONITOR and you record
 * an empty hole where the stone used to be, and "restoring" it puts the hole back. Recording
 * a block whose event is cancelled a moment later costs nothing — we simply remember a block
 * as being what it still is.
 *
 * <p>Only matches whose mode allows building are journalled. A duel cancels every break
 * anyway; there is nothing to undo, and nothing to pay for.
 */
public final class RestoreListener implements Listener {

    private final MatchService matches;

    public RestoreListener(MatchService matches) {
        this.matches = matches;
    }

    private Optional<ArenaJournal> journalAt(Location location) {
        return matches.matchAt(location)
                .filter(match -> match.mode().rules().building())
                .map(Match::journal);
    }

    private void remember(Block block) {
        journalAt(block.getLocation()).ifPresent(journal -> journal.remember(block));
    }

    // --- players ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        remember(event.getBlock());
    }

    /**
     * At this point the block is <b>already placed</b>, so its own state is the new one. What
     * we want is what it replaced, which the event kept for us.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        journalAt(event.getBlock().getLocation())
                .ifPresent(journal -> journal.remember(event.getBlockReplacedState()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        event.getReplacedBlockStates().forEach(state ->
                journalAt(state.getLocation()).ifPresent(journal -> journal.remember(state)));
    }

    // --- everything that is not a player ------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::remember);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::remember);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBurn(BlockBurnEvent event) {
        remember(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        remember(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFade(BlockFadeEvent event) {
        remember(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onForm(BlockFormEvent event) {
        remember(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpread(BlockSpreadEvent event) {
        remember(event.getBlock());
    }

    /** Water and lava do not fire a place event. They still fill the hole you dug. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFlow(BlockFromToEvent event) {
        remember(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> {
            remember(block);
            remember(block.getRelative(event.getDirection()));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> {
            remember(block);
            remember(block.getRelative(event.getDirection()));
        });
    }

    /** Falling sand, an enderman lifting a block, a sheep eating grass. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityChange(EntityChangeBlockEvent event) {
        remember(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeavesDecay(LeavesDecayEvent event) {
        remember(event.getBlock());
    }

    /**
     * A looted chest fires <b>no block event at all</b>.
     *
     * Taking the iron out of a chest does not change a block, so nothing above would have
     * noticed — and the next match on that island would find every chest already emptied by
     * the last one. Photographing the chest when it is opened costs one entry and restores
     * its contents with everything else, because the journal stores block STATES, not types.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        Location at = event.getInventory().getLocation();
        if (at != null) {
            remember(at.getBlock());
        }
    }
}
