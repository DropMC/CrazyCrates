package com.badbones69.crazycrates.paper.api.events;

import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.Prize;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Fired right before a prize's items are handed out, after {@link PlayerPrizeEvent} and after the alternative prize was
 * picked. Listeners may change the items in place, or take the hand out over with {@link #setHandled(boolean)}, in which
 * case the plugin gives nothing. The prize's commands and messages run either way.
 */
public class PrizeItemsEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Crate crate;
    private final Prize prize;
    private final Location location;
    private final List<ItemStack> items;

    private boolean handled;

    public PrizeItemsEvent(@NotNull final Player player, @NotNull final Crate crate, @NotNull final Prize prize, @NotNull final Location location, @NotNull final List<ItemStack> items) {
        this.player = player;
        this.crate = crate;
        this.prize = prize;
        this.location = location;
        this.items = items;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public @NotNull final Player getPlayer() {
        return this.player;
    }

    public @NotNull final Crate getCrate() {
        return this.crate;
    }

    /**
     * @return the prize being given, the alternative prize when the player had a blacklisted permission
     */
    public @NotNull final Prize getPrize() {
        return this.prize;
    }

    /**
     * @return where the prize is given, the crate's location or just above the player for virtual openings
     */
    public @NotNull final Location getLocation() {
        return this.location.clone();
    }

    /**
     * @return the items about to be given, a mutable list
     */
    public @NotNull final List<ItemStack> getItems() {
        return this.items;
    }

    public final boolean isHandled() {
        return this.handled;
    }

    /**
     * @param handled true when a listener gives the items itself, so the plugin does not
     */
    public void setHandled(final boolean handled) {
        this.handled = handled;
    }
}
