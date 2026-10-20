package com.badbones69.crazycrates.paper.support.holograms.types;

import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.crates.CrateHologram;
import com.badbones69.crazycrates.paper.support.holograms.HologramManager;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holograms with no plugin behind them, like PhoenixCrates' {@code BUILTIN} ones: one text display per crate location.
 * The display is never saved with the chunk, so a task on the crate's region puts it back whenever the chunk loads again.
 * A player watching the crate open does not see it, since the prize floats where the text is.
 */
public class BuiltinHologramSupport extends HologramManager {

    /** How often, in ticks, a missing display is put back. */
    private static final long CHECK_TICKS = 40L;

    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();

    @Override
    public void createHologram(@NotNull final Location location, @NotNull final Crate crate, @NotNull final String id) {
        final CrateHologram settings = crate.getHologram();

        if (crate.getCrateType() == CrateType.menu || !settings.isEnabled()) {
            removeHologram(id);

            return;
        }

        if (exists(id)) return;

        final Location position = location.getBlock().getLocation().add(getVector(crate));
        final Hologram hologram = new Hologram(position, settings);

        this.holograms.put(id, hologram);

        hologram.task = new FoliaScheduler(this.plugin, position) {
            @Override
            public void run() {
                hologram.ensureSpawned();
            }
        }.runAtFixedRate(1, CHECK_TICKS);
    }

    @Override
    public void removeHologram(@NotNull final String id) {
        final Hologram hologram = this.holograms.remove(id);

        if (hologram != null) hologram.remove(false);
    }

    @Override
    public boolean exists(@NotNull final String id) {
        return this.holograms.containsKey(id);
    }

    @Override
    public void purge(final boolean isShutdown) {
        this.holograms.values().forEach(hologram -> hologram.remove(isShutdown));
        this.holograms.clear();
    }

    /**
     * Hides a crate's hologram from a player while they watch it open. Call on the crate's region.
     *
     * @param player the player
     * @param id the crate location's id
     */
    public void hideFrom(@NotNull final Player player, @NotNull final String id) {
        final Hologram hologram = this.holograms.get(id);

        if (hologram != null && hologram.display != null && hologram.display.isValid()) player.hideEntity(this.plugin, hologram.display);
    }

    /**
     * Shows a crate's hologram to a player again. Call on the crate's region.
     *
     * @param player the player
     * @param id the crate location's id
     */
    public void showTo(@NotNull final Player player, @NotNull final String id) {
        final Hologram hologram = this.holograms.get(id);

        if (hologram != null && hologram.display != null && hologram.display.isValid()) player.showEntity(this.plugin, hologram.display);
    }

    @Override
    public @NotNull final String getName() {
        return "Builtin";
    }

    private static final class Hologram {

        private final Location position;
        private final Component text;
        private final Color background;
        private final boolean shadow;
        private final float viewRange;

        private volatile @Nullable TextDisplay display;
        private volatile @Nullable ScheduledTask task;

        private Hologram(@NotNull final Location position, @NotNull final CrateHologram settings) {
            this.position = position;
            // line by line, so a tag left open on one line (a <bold> heading) does not run into the next.
            this.text = Component.join(JoinConfiguration.newlines(), settings.getMessages().stream().map(MiniMessage.miniMessage()::deserialize).toList());
            this.background = background(settings.getBackgroundColor());
            this.shadow = settings.getTextShadow();
            this.viewRange = Math.max(settings.getRange(), 1) / 64.0f; // view range scales the 64 block entity range.
        }

        private void ensureSpawned() {
            final TextDisplay current = this.display;

            if (current != null && current.isValid()) return;

            if (!this.position.isChunkLoaded()) return;

            this.display = this.position.getWorld().spawn(this.position, TextDisplay.class, display -> {
                display.setPersistent(false);
                display.text(this.text);
                display.setBillboard(Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setShadowed(this.shadow);
                display.setViewRange(this.viewRange);

                if (this.background != null) {
                    display.setDefaultBackground(false);
                    display.setBackgroundColor(this.background);
                }
            });
        }

        /**
         * @param isShutdown whether the server is stopping, when the display goes with its chunk anyway
         */
        private void remove(final boolean isShutdown) {
            final ScheduledTask current = this.task;

            if (current != null) current.cancel();

            final TextDisplay display = this.display;

            this.display = null;

            if (display == null || isShutdown) return;

            display.getScheduler().run(CrazyCrates.getPlugin(), _ -> display.remove(), null);
        }

        private static @Nullable Color background(@NotNull final String value) {
            if (value.isBlank() || value.equalsIgnoreCase("default")) return null;

            if (value.equalsIgnoreCase("transparent")) return Color.fromARGB(0);

            try {
                if (value.startsWith("#")) {
                    final String hex = value.substring(1);

                    return hex.length() == 6 ? Color.fromARGB(0xC8000000 | Integer.parseInt(hex, 16)) : Color.fromARGB((int) Long.parseLong(hex, 16));
                }
            } catch (final NumberFormatException _) {
                return null;
            }

            final NamedTextColor named = NamedTextColor.NAMES.value(value.toLowerCase().replace(' ', '_'));

            return named == null ? null : Color.fromARGB(0xC8000000 | named.value());
        }
    }
}
