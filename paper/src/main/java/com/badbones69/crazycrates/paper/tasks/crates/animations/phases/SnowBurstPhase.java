package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code SNOW_BURST}, a Christmas open: the lid pops off in a puff of powder snow, snowflakes burst out in every direction
 * and a red and white ring spreads over the floor around the crate. Fits a gift box, whose open animation swings the lid.
 */
public class SnowBurstPhase extends OpeningPhase {

    private static final int LENGTH = 26;

    private int ticks;

    public SnowBurstPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        final Location top = this.opening.base().add(0.0, 1.2, 0.0);

        this.opening.sound(Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);
        this.opening.sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.2f);
        this.opening.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, Festive.pitch(18));

        final ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int index = 0; index < 60; index++) {
            // count 0: the offset is the direction and the speed scales it.
            this.opening.particle(Particle.SNOWFLAKE, top, 0, random.nextDouble(-1.0, 1.0), random.nextDouble(0.2, 1.0), random.nextDouble(-1.0, 1.0),
                    0.25, null);
        }

        this.opening.particle(Particle.ITEM_SNOWBALL, top, 30, 0.3, 0.2, 0.3, 0.15, null);
        this.opening.particle(Particle.CLOUD, top, 10, 0.2, 0.1, 0.2, 0.05, null);
    }

    @Override
    public void tick() {
        final double radius = 0.4 + this.ticks * 0.08;
        final int points = 12 + this.ticks;

        for (int index = 0; index < points; index++) {
            final double angle = Math.PI * 2.0 * index / points;
            final Location point = this.opening.base().add(Math.sin(angle) * radius, 0.1, Math.cos(angle) * radius);

            this.opening.particle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, Festive.dust(index % 2 == 0 ? Festive.RED : Festive.WHITE, 0.9f));
        }

        Festive.snow(this.opening, 4);

        if (this.ticks == 10) this.opening.sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 0.8f, 1.3f);

        if (this.ticks++ >= LENGTH) done();
    }
}
