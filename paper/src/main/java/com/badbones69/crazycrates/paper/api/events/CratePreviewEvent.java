package com.badbones69.crazycrates.paper.api.events;

import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.Tier;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the player's thread right before a crate's preview menu opens. Cancelling it keeps the plugin's own menu shut,
 * so a listener can show the crate's prizes ({@link Crate#getPreviewItems(Player, Tier)})
 * in a menu of its own. Not fired for a tier preview.
 */
public class CratePreviewEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Crate crate;

    private boolean cancelled;

    public CratePreviewEvent(@NotNull final Player player, @NotNull final Crate crate) {
        this.player = player;
        this.crate = crate;
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

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }
}
