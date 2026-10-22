package com.badbones69.crazycrates.paper.tasks.crates.types;

import org.bukkit.inventory.ItemStack;
import com.badbones69.crazycrates.paper.api.PrizeManager;
import com.badbones69.crazycrates.paper.api.builders.CrateBuilder;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.crates.CrateLocation;
import com.badbones69.crazycrates.paper.cache.enums.ActiveStatus;
import com.badbones69.crazycrates.paper.cache.objects.ActiveCrate;
import com.badbones69.crazycrates.paper.managers.events.EventManager;
import com.badbones69.crazycrates.paper.managers.events.enums.EventType;
import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import us.crazycrew.crazycrates.api.enums.types.KeyType;
import com.badbones69.crazycrates.paper.utils.ItemUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A crate that opens in the world, with no menu, the way PhoenixCrates' crates did (see {@link CrateOpening}). Sneaking
 * spends up to the crate's mass-open limit at once and skips the animation, like Phoenix's quick open.
 */
public class PhysicalCrate extends CrateBuilder {

    public PhysicalCrate(@NotNull final Crate crate, @NotNull final Player player, @NotNull final Location location) {
        super(crate, player, location);
    }

    private final Player player = getPlayer();
    private final Location location = getLocation();
    private final UUID uuid = this.player.getUniqueId();
    private final Crate crate = getCrate();

    @Override
    public void open(@NotNull final KeyType type, final boolean checkHand, final boolean isSilent, final int amount, @NotNull final EventType eventType) {
        final Optional<CrateLocation> crateLocation = this.crateManager.getCrateLocation(this.location);

        if (crateLocation.isEmpty() || CrateOpening.isOpening(this.player)) {
            this.crateManager.endCrate(this.crate, this.player);

            return;
        }

        final String fileName = this.crate.getFileName();
        final boolean massOpen = this.player.isSneaking();

        // keys go only after the event passes, so a crate refused by permission or with no winnable prize costs nothing.
        if (isCrateEventValid(type, checkHand, isSilent, amount, eventType)) return;

        // a mass open stops at the free slots: one prize each, and the crate refuses to open on a full inventory. it
        // spends only the keys in hand, since a migrated key never stacks with a new one and the rest sit elsewhere.
        final int keys = massOpen ? Math.max(1, Math.min(Math.min(handKeys(), this.crate.getMaxMassOpen()), freeSlots())) : amount;

        if (!this.userManager.takeKeys(this.uuid, fileName, type, keys, checkHand || massOpen)) {
            this.crateManager.endCrate(this.crate, this.player);

            return;
        }

        final Location prizeLocation = this.location.clone().add(0.5, 1.0, 0.5);
        final String name = this.player.getName();

        EventManager.logEvent(EventType.event_crate_opened, name, this.player, this.crate, type, keys);
        EventManager.logEvent(EventType.event_key_taken, name, this.player, this.crate, type, keys);

        if (massOpen) {
            for (int index = 0; index < keys; index++) {
                PrizeManager.givePrize(this.player, prizeLocation, this.crate, this.crate.pickPrize(this.player));
            }

            this.crateManager.endCrate(this.crate, this.player);

            return;
        }

        this.cacheManager.addActiveCrate(this.uuid, new ActiveCrate(ActiveStatus.physical_location, this.location, 1, this.crate));

        new CrateOpening(this.player, this.crate, crateLocation.get(), this.crate.pickPrize(this.player)).start();
    }

    private int handKeys() {
        int keys = 0;

        for (final ItemStack item : List.of(this.player.getInventory().getItemInMainHand(), this.player.getInventory().getItemInOffHand())) {
            if (!item.isEmpty() && ItemUtil.isSimilar(item, this.crate)) keys += item.getAmount();
        }

        return keys;
    }

    private int freeSlots() {
        int free = 0;

        for (final ItemStack item : this.player.getInventory().getStorageContents()) {
            if (item == null || item.isEmpty()) free++;
        }

        return free;
    }
}
