package com.badbones69.crazycrates.paper.listeners.crates;

import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.CrazyCratesPaper;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.tasks.crates.CrateManager;
import com.badbones69.crazycrates.paper.utils.CrateFacing;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import us.crazycrew.crazycrates.api.enums.messages.Message;
import java.util.List;

/**
 * Crates in the world. Crates as blocks: an admin sets one down by placing its {@link Crate#getPlaceable() placeable item}, facing them, and
 * takes it back by breaking it while sneaking (handled with the other admin clicks in {@code PaperInteractListener}).
 * Nothing else may break a crate's block.
 */
public class CratePlaceListener implements Listener {

    public static final String PERMISSION = "crazycrates.admin";

    private final CrazyCrates plugin = CrazyCrates.getPlugin();

    private final CrazyCratesPaper platform = this.plugin.getPlatform();

    private final CrateManager crateManager = this.platform.getCrateManager();

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlaceDenied(BlockPlaceEvent event) {
        final Crate crate = this.crateManager.getCrateFromPlaceable(event.getItemInHand());

        if (crate == null) return;

        final Player player = event.getPlayer();

        if (!player.hasPermission(PERMISSION)) {
            event.setCancelled(true);

            Message.crate_no_permission.sendMessage(player, "{crate}", crate.getCrateName());
        }
    }

    /**
     * The game places the item's own block first. One tick later, on the block's region, it becomes the crate's block
     * turned to face the admin, and the crate is registered there. Changing the block inside the event would race the
     * game's own follow-up on the placed block (its block entity, neighbour updates).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        final Crate crate = this.crateManager.getCrateFromPlaceable(event.getItemInHand());

        if (crate == null) return;

        final Block block = event.getBlockPlaced();
        final Material placed = block.getType();
        final float yaw = CrateFacing.snap(event.getPlayer().getYaw() + 180.0f);

        final Location location = block.getLocation();
        location.setYaw(yaw);

        new FoliaScheduler(this.plugin, location) {
            @Override
            public void run() {
                if (block.getType() != placed) return; // broken or replaced within the tick.

                final BlockData data = crate.getPlaceable().getBlock().createBlockData();

                CrateFacing.apply(data, yaw);

                block.setBlockData(data, false);

                crateManager.placeCrate(location, crate);
            }
        }.runDelayed(1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (this.crateManager.isCrateLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (CrateOpening.isAnimationEntity(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemMerge(ItemMergeEvent event) {
        if (CrateOpening.isAnimationEntity(event.getEntity()) || CrateOpening.isAnimationEntity(event.getTarget())) event.setCancelled(true);
    }

    private void protect(List<Block> blocks) {
        blocks.removeIf(block -> this.crateManager.isCrateLocation(block.getLocation()));
    }
}
