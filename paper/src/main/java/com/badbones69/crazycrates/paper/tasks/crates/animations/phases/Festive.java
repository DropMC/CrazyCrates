package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ThreadLocalRandom;

/** What the Christmas phases share: their palette, note block pitches and the snow that falls around the crate. */
final class Festive {

    static final Color RED = Color.fromRGB(0xE8343A);
    static final Color GREEN = Color.fromRGB(0x3FB950);
    static final Color WHITE = Color.fromRGB(0xF4F7FB);
    static final Color GOLD = Color.fromRGB(0xF2B632);

    static final Color[] PALETTE = {RED, GREEN, WHITE, GOLD};

    /** Wool bits thrown as confetti, one per palette color. */
    static final ItemStack[] CONFETTI = {
            new ItemStack(Material.RED_WOOL),
            new ItemStack(Material.LIME_WOOL),
            new ItemStack(Material.WHITE_WOOL),
            new ItemStack(Material.YELLOW_WOOL)
    };

    /** A major pentatonic run in note block steps, for chimes that never clash. */
    static final int[] PENTATONIC = {6, 8, 10, 13, 15, 18, 20, 22};

    private Festive() {}

    /**
     * @param note a note block step, 0 (F#3) to 24 (F#5)
     * @return the sound pitch that plays it
     */
    static float pitch(final int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    static void note(@NotNull final CrateOpening opening, @NotNull final Sound sound, final int note, final float volume) {
        opening.sound(sound, volume, pitch(note));
    }

    static @NotNull Color random() {
        return PALETTE[ThreadLocalRandom.current().nextInt(PALETTE.length)];
    }

    static @NotNull Particle.DustOptions dust(@NotNull final Color color, final float size) {
        return new Particle.DustOptions(color, size);
    }

    /** A few snowflakes drifting down over the crate, from up to two and a half blocks above it. */
    static void snow(@NotNull final CrateOpening opening, final int amount) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int index = 0; index < amount; index++) {
            final Location flake = opening.base().add(random.nextDouble(-1.3, 1.3), random.nextDouble(1.5, 2.6), random.nextDouble(-1.3, 1.3));

            opening.particle(Particle.SNOWFLAKE, flake, 0, 0.0, -1.0, 0.0, 0.03, null); // count 0: the offset is the direction.
        }
    }
}
