package com.badbones69.crazycrates.paper.api.events;

import com.badbones69.crazycrates.paper.api.objects.Prize;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerPrizeEvent extends Event {
    
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final Crate crate;
    private final Prize prize;
    private final String crateName;
    private Location location;

    @Deprecated(since = "3.7.4", forRemoval = true)
    public PlayerPrizeEvent(@NotNull final Player player, @NotNull final Crate crate, @NotNull final String crateName, @NotNull final Prize prize) {
        this.player = player;
        this.crate = crate;
        this.prize = prize;
        this.crateName = crateName;
    }

    public PlayerPrizeEvent(@NotNull final Player player, @NotNull final Crate crate, @NotNull final Prize prize) {
        this(player, crate, crate.getFileName(), prize);
    }

    public PlayerPrizeEvent(@NotNull final Player player, @NotNull final Crate crate, @NotNull final Prize prize, @NotNull final Location location) {
        this(player, crate, crate.getFileName(), prize);

        this.location = location.clone();
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
    
    public @NotNull final String getCrateName() {
        return this.crateName;
    }
    
    public @NotNull final Prize getPrize() {
        return this.prize;
    }

    /**
     * @return where the prize is given, the crate's location or just above the player for virtual openings; null when
     * the event was fired by a caller that did not pass one
     */
    public @Nullable final Location getLocation() {
        return this.location == null ? null : this.location.clone();
    }
}