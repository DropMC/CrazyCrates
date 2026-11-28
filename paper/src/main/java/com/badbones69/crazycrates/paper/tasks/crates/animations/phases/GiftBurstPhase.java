package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code GIFT_BURST}, a Christmas open: the crate spills open with a rustle and a fountain of wool confetti and sparks in
 * red, green, white and gold shoots up out of it, ending in a twinkle. Fits a sack, whose open animation lifts the gifts.
 */
public class GiftBurstPhase extends OpeningPhase {

    private static final int LENGTH = 30, FOUNTAIN = 18;

    /** Where the fountain comes out: the sack's mouth. */
    private static final double MOUTH = 1.3;

    private int ticks;

    public GiftBurstPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        this.opening.sound(Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 0.8f);
        this.opening.sound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.3f);
        this.opening.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, Festive.pitch(13));
    }

    @Override
    public void tick() {
        final Location mouth = this.opening.base().add(0.0, MOUTH, 0.0);

        if (this.ticks < FOUNTAIN) {
            final ThreadLocalRandom random = ThreadLocalRandom.current();

            for (int index = 0; index < 4; index++) {
                final double x = random.nextDouble(-0.35, 0.35), z = random.nextDouble(-0.35, 0.35);

                // count 0: the offset is the direction and the speed scales it.
                this.opening.particle(Particle.ITEM, mouth, 0, x, 1.0, z, 0.35, Festive.CONFETTI[random.nextInt(Festive.CONFETTI.length)]);
                this.opening.particle(Particle.FIREWORK, mouth, 0, x, 1.0, z, 0.25, null);
            }

            this.opening.particle(Particle.DUST, mouth.clone().add(0.0, 0.4 + this.ticks * 0.08, 0.0), 5, 0.3, 0.2, 0.3, 0.0,
                    Festive.dust(Festive.random(), 1.3f));

            if (this.ticks % 6 == 0) this.opening.sound(Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.9f + this.ticks * 0.03f);
        }

        if (this.ticks == FOUNTAIN) this.opening.sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.2f);

        Festive.snow(this.opening, 3);

        if (this.ticks++ >= LENGTH) done();
    }
}
