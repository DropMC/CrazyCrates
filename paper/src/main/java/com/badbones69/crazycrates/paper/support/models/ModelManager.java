package com.badbones69.crazycrates.paper.support.models;

import org.jetbrains.annotations.Nullable;
import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.ryderbelserion.fusion.paper.FusionPaper;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public abstract class ModelManager {

    protected CrazyCrates plugin = CrazyCrates.getPlugin();

    protected FusionPaper fusion = this.plugin.getPlatform().getFusion();

    public abstract void createModel(@NotNull final Location location, @NotNull final Crate crate, @NotNull final String id);

    public abstract void removeModel(@NotNull final String id);

    /**
     * Forgets that the player sees the models, so they are sent again. A respawn clears the client's entities, and a quit
     * leaves nothing to send to.
     *
     * @param player the player
     */
    public abstract void forgetViewer(@NotNull final Player player);

    /**
     * Gives a player their own copy of the model at a crate location for an opening, and hides the shared one from them
     * until the copy is closed. Everyone else keeps seeing the shared model idle. Runs on the location's region.
     *
     * @param id the crate location's id
     * @param player the player opening the crate
     * @return the copy, or null when the location shows no model (yet)
     */
    public abstract @Nullable ModelSession openFor(@NotNull final String id, @NotNull final Player player);

    public abstract void purge();

    public abstract String getName();

    /**
     * One player's copy of a crate model, while they open the crate.
     */
    public interface ModelSession {

        /**
         * Plays an animation in its own loop mode, in place of the one this session played before, like PhoenixCrates: a
         * {@code hold} animation keeps its last pose (the lid stays open) until the next one takes over.
         *
         * @param animation the animation's name
         * @return how many ticks the animation runs, or -1 when the model has no such animation
         */
        int play(@NotNull String animation);

        /**
         * Removes the copy and shows the player the shared model again.
         */
        void close();
    }
}
