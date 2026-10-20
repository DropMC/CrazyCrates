package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PhoenixCrates' {@code SWIRL}: the crate bursts with a bang, then a ring of the crate's color spirals down onto it from
 * three and a half blocks up while it smokes, and pops above it after about two seconds.
 */
public class SwirlPhase extends OpeningPhase {

    private final Color color = this.opening.getAnimation().getOpenColor();

    private double delta;

    public SwirlPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        this.opening.sound(Sound.BLOCK_LAVA_POP, 2.0f, 1.0f);
        this.opening.sound(Sound.BLOCK_LAVA_EXTINGUISH, 2.0f, 1.0f);
        this.opening.sound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.1f);
        this.opening.sound(Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.7f);
        this.opening.sound(Sound.ENTITY_ENDERMAN_AMBIENT, 1.0f, 2.0f);

        this.opening.particle(Particle.EXPLOSION, this.opening.base().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0, null);
        this.opening.particle(Particle.BLOCK, this.opening.base().add(0.0, 0.5, 0.0), 220, 0.6, 0.6, 0.6, 0.3, this.opening.texture());
    }

    @Override
    public void tick() {
        final double radius = Math.max(0.0, 2.0 - this.delta / 2.4);
        final Particle.DustOptions dust = new Particle.DustOptions(this.color, 1.0f);

        for (double angle = 0.0; angle <= Math.PI * 2; angle++) {
            final Location point = this.opening.base().add(
                    Math.sin(this.delta * 2.0 + angle) * radius,
                    3.5 - this.delta / 10.0,
                    Math.cos(this.delta * 2.0 + angle) * radius
            );

            this.opening.particle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }

        final ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int index = 0; index <= 4; index++) {
            final Location smoke = this.opening.base().add(random.nextDouble(-0.2, 0.2), 0.5 + random.nextDouble(0.0, 0.4), random.nextDouble(-0.2, 0.2));

            this.opening.particle(Particle.SMOKE, smoke, 0, 0.0, 0.2, 0.0, 0.8, null); // count 0: the offset is the direction.
        }

        if (this.delta > 3.5) {
            final Location top = this.opening.base().add(0.0, 3.0, 0.0);

            this.opening.particle(Particle.SMOKE, top, 20, 0.2, 0.2, 0.2, 0.4, null);
            this.opening.particle(Particle.POOF, top, 1, 0.0, 0.0, 0.0, 0.0, null);
            this.opening.particle(Particle.ENTITY_EFFECT, top, 10, 0.0, 0.0, 0.0, 0.5, this.color);
            this.opening.particle(Particle.EXPLOSION, top, 1, 0.0, 0.0, 0.0, 0.0, null); // silent: the prize's display bangs next.

            done();

            return;
        }

        this.delta += 0.1;
    }
}
