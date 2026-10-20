package com.badbones69.crazycrates.paper.support.models.types;

import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.crates.CrateModel;
import com.badbones69.crazycrates.paper.support.models.ModelManager;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.DummyTracker;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows crate models through BetterModel. A BetterModel dummy tracker is sent only to the players it is told about, so
 * each model keeps a task on its own region that sends it to the players in range and takes it from those who left.
 */
public class BetterModelSupport extends ModelManager {

    /** Ticks of the viewer task (one a second) before a model that never loaded is reported; BetterModel takes a few on boot. */
    private static final int MISSING_WARNING_TICKS = 30;

    /** How long both models stay up when swapping the shared one and an opener's copy, so the crate never blinks out. */
    private static final int SWAP_TICKS = 3;

    private final Server server = this.plugin.getServer();

    private final Map<String, Display> displays = new ConcurrentHashMap<>();

    @Override
    public void createModel(@NotNull final Location location, @NotNull final Crate crate, @NotNull final String id) {
        removeModel(id);

        final CrateModel model = crate.getModel();

        final Display display = new Display(crate.getFileName(), origin(location, model), model);

        this.displays.put(id, display);

        display.task = new FoliaScheduler(this.plugin, display.origin) {
            @Override
            public void run() {
                display.tick();
            }
        }.runAtFixedRate(1, 20);
    }

    /**
     * Where the model stands: the offset is turned with the crate around the middle of its block, so an off-centre model
     * stays put on the block whichever way the crate faces. A dummy tracker takes its rotation from this location's yaw.
     */
    private static @NotNull Location origin(@NotNull final Location location, @NotNull final CrateModel model) {
        final Vector centre = new Vector(0.5, 0.0, 0.5);
        final Vector offset = model.getOffset().subtract(centre).rotateAroundY(-Math.toRadians(location.getYaw())).add(centre);

        final Location origin = location.clone().add(offset);
        origin.setPitch(0.0f);

        return origin;
    }

    @Override
    public void removeModel(@NotNull final String id) {
        final Display display = this.displays.remove(id);

        if (display != null) display.close();
    }

    @Override
    public void forgetViewer(@NotNull final Player player) {
        for (final Display display : this.displays.values()) {
            display.forget(player);
        }
    }

    @Override
    public @Nullable ModelSession openFor(@NotNull final String id, @NotNull final Player player) {
        final Display display = this.displays.get(id);

        if (display == null || display.tracker == null) return null;

        final Optional<ModelRenderer> renderer = BetterModel.model(display.model.getName());

        if (renderer.isEmpty()) return null;

        final UUID uuid = player.getUniqueId();

        final ModelRenderer model = renderer.get();
        final DummyTracker copy = model.create(BukkitAdapter.adapt(display.origin));

        copy.spawn(BukkitAdapter.adapt(player));

        final boolean[] closed = {false};

        later(display.origin, () -> { // the copy takes a few ticks to reach the client.
            if (!closed[0]) display.hideFrom(player);
        });

        return new ModelSession() {
            private String current;

            @Override
            public int play(@NotNull final String animation) {
                final Optional<BlueprintAnimation> blueprint = animation.isBlank() ? Optional.empty() : model.animation(animation);

                if (blueprint.isEmpty()) return -1;

                // like PhoenixCrates, each animation starts over the one before. A hold animation never reports its end, and
                // play-once would snap the model back to its closed rest pose, so the caller waits out the length instead.
                if (this.current != null) copy.stopAnimation(this.current);

                if (!copy.animate(animation, AnimationModifier.DEFAULT)) return -1;

                this.current = animation;

                return Math.round(blueprint.get().length() * 20.0f);
            }

            @Override
            public void close() {
                closed[0] = true;

                display.showTo(uuid);

                later(display.origin, copy::close);
            }
        };
    }

    private void later(@NotNull final Location location, @NotNull final Runnable action) {
        if (!this.plugin.isEnabled()) { // shutting down: nothing can be scheduled any more.
            action.run();

            return;
        }

        new FoliaScheduler(this.plugin, location) {
            @Override
            public void run() {
                action.run();
            }
        }.runDelayed(SWAP_TICKS);
    }

    @Override
    public void purge() {
        for (final String id : List.copyOf(this.displays.keySet())) {
            removeModel(id);
        }
    }

    @Override
    public String getName() {
        return "BetterModel";
    }

    private final class Display {

        private final String crateName;
        private final Location origin;
        private final CrateModel model;
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        // players opening the crate, who see their own copy of the model instead (openFor).
        private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

        private volatile DummyTracker tracker;
        private volatile ScheduledTask task;
        private volatile boolean closed;
        private int missingTicks;

        private Display(@NotNull final String crateName, @NotNull final Location origin, @NotNull final CrateModel model) {
            this.crateName = crateName;
            this.origin = origin;
            this.model = model;
        }

        /**
         * Runs on the origin's region. The tracker is created here, not in createModel, because creating one fires an event
         * and a reload runs on the global region thread on Folia. BetterModel also loads its models asynchronously, after
         * merging with Nexo, so on boot the model may not exist yet: it is looked up again on every tick until it does.
         */
        private void tick() {
            if (this.closed) return;

            if (this.tracker == null) {
                final Optional<ModelRenderer> renderer = BetterModel.model(this.model.getName());

                if (renderer.isEmpty()) {
                    if (++this.missingTicks == MISSING_WARNING_TICKS) {
                        BetterModelSupport.this.plugin.getComponentLogger().warn("BetterModel has no model named {}, used by the crate {}. Retrying until it loads.", this.model.getName(), this.crateName);
                    }

                    return;
                }

                this.tracker = renderer.get().create(BukkitAdapter.adapt(this.origin));

                if (!this.model.getIdleAnimation().isEmpty()) {
                    this.tracker.animate(this.model.getIdleAnimation());
                }

                // closed while the tracker was being created, so close() found nothing to close.
                if (this.closed) {
                    this.tracker.close();

                    return;
                }
            }

            final DummyTracker tracker = this.tracker;

            final Set<UUID> inRange = new HashSet<>();

            for (final Player player : this.origin.getNearbyPlayers(this.model.getRange())) {
                final UUID uuid = player.getUniqueId();

                if (this.hidden.contains(uuid)) continue;

                inRange.add(uuid);

                if (this.viewers.add(uuid) || !tracker.isSpawned(uuid)) {
                    tracker.spawn(BukkitAdapter.adapt(player));
                }
            }

            for (final UUID uuid : List.copyOf(this.viewers)) {
                if (inRange.contains(uuid)) continue;

                this.viewers.remove(uuid);

                final Player player = BetterModelSupport.this.server.getPlayer(uuid);

                if (player != null) tracker.remove(BukkitAdapter.adapt(player));
            }
        }

        private void hideFrom(@NotNull final Player player) {
            this.hidden.add(player.getUniqueId());

            forget(player);
        }

        private void showTo(@NotNull final UUID uuid) {
            this.hidden.remove(uuid);

            final Player player = BetterModelSupport.this.server.getPlayer(uuid);
            final DummyTracker tracker = this.tracker;

            if (player == null || tracker == null || this.closed) return;

            this.viewers.add(uuid);

            tracker.spawn(BukkitAdapter.adapt(player));
        }

        private void forget(@NotNull final Player player) {
            if (!this.viewers.remove(player.getUniqueId())) return;

            final DummyTracker tracker = this.tracker;

            if (tracker != null) tracker.remove(BukkitAdapter.adapt(player));
        }

        private void close() {
            this.closed = true;

            final ScheduledTask task = this.task;

            if (task != null) task.cancel();

            final DummyTracker tracker = this.tracker;

            if (tracker != null) tracker.close();
        }
    }
}
