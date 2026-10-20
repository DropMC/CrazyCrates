package com.badbones69.crazycrates.paper.tasks.crates.animations;

import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.crates.CrateAnimation;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The particles a crate gives off while it stands idle (the crate's {@code Idle-Effects}), one task per crate location on
 * its region. Like PhoenixCrates, a player watching a crate open does not see them.
 */
public class CrateIdleEffects {

    /** How far away the particles are sent. */
    private static final double RANGE = 48.0;

    private final CrazyCrates plugin = CrazyCrates.getPlugin();

    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();

    /**
     * Starts the particles at a crate location, replacing any already there.
     *
     * @param location the crate's block
     * @param crate the crate
     * @param id the location's id
     */
    public void start(@NotNull final Location location, @NotNull final Crate crate, @NotNull final String id) {
        stop(id);

        final CrateAnimation animation = crate.getAnimation();

        if (animation == null || animation.getIdleEffects().isEmpty()) return;

        final List<CrateAnimation.IdleEffect> effects = animation.getIdleEffects();
        final Location centre = location.getBlock().getLocation().add(0.5, 0.5, 0.5);

        this.tasks.put(id, new FoliaScheduler(this.plugin, centre) {
            @Override
            public void run() {
                for (final Player player : centre.getNearbyPlayers(RANGE)) {
                    if (CrateOpening.isOpening(player)) continue;

                    for (final CrateAnimation.IdleEffect effect : effects) {
                        show(player, centre, effect);
                    }
                }
            }
        }.runAtFixedRate(1, 2));
    }

    public void stop(@NotNull final String id) {
        final ScheduledTask task = this.tasks.remove(id);

        if (task != null) task.cancel();
    }

    public void purge() {
        List.copyOf(this.tasks.keySet()).forEach(this::stop);
    }

    private static void show(@NotNull final Player player, @NotNull final Location centre, @NotNull final CrateAnimation.IdleEffect effect) {
        final Class<?> type = effect.particle().getDataType();

        final Object data = data(type, effect.color());

        if (data == null && type != Void.class) return; // a particle needing data this cannot give, such as a block.

        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final double spread = effect.spread();

        for (int index = 0; index < effect.amount(); index++) {
            final Location location = centre.clone().add(effect.offset()).add(
                    spread > 0 ? random.nextDouble(-spread, spread) : 0.0,
                    spread > 0 ? random.nextDouble(-spread, spread) : 0.0,
                    spread > 0 ? random.nextDouble(-spread, spread) : 0.0
            );

            player.spawnParticle(effect.particle(), location, 0, 0.0, 0.0, 0.0, effect.velocity(), data);
        }
    }

    private static @Nullable Object data(@NotNull final Class<?> type, @NotNull final Color color) {
        if (type == Particle.DustOptions.class) return new Particle.DustOptions(color, 1.0f);

        if (type == Particle.DustTransition.class) {
            return new Particle.DustTransition(color, Color.fromRGB(color.getRed() / 2, color.getGreen() / 2, color.getBlue() / 2), 1.0f);
        }

        if (type == Color.class) return color;

        return null;
    }
}
