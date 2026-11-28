package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code FESTIVE}, a Christmas display: the prize appears to a bell chord in a burst of sparks and floats over the crate for
 * three and a half seconds, circled by two candy cane strands (the display color and white) while snow falls and soft
 * chimes ring. The prize is given as it appears.
 */
public class FestiveDisplayPhase extends OpeningPhase {

    private static final int LENGTH = 70;

    /** C, E and G an octave apart, rung together as the prize appears. */
    private static final int[] CHORD = {6, 10, 13, 18};

    private final Color color = this.opening.getAnimation().getDisplayColor();

    private Item item;
    private int ticks;
    private double delta;

    public FestiveDisplayPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        final Location location = this.opening.displayLocation();

        for (final int note : CHORD) {
            Festive.note(this.opening, Sound.BLOCK_NOTE_BLOCK_BELL, note, 0.8f);
        }

        this.opening.sound(Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);

        this.opening.particle(Particle.FIREWORK, location, 30, 0.0, 0.0, 0.0, 0.15, null);
        this.opening.particle(Particle.END_ROD, location, 10, 0.2, 0.2, 0.2, 0.05, null);

        this.item = RewardDisplayPhase.showPrize(this.opening, location, false);

        this.opening.givePrize();
    }

    @Override
    public void tick() {
        final Location centre = this.opening.displayLocation();

        for (int strand = 0; strand < 2; strand++) {
            final double angle = this.delta + strand * Math.PI;
            final Location point = centre.clone().add(Math.sin(angle) * 0.8, Math.sin(this.delta * 0.7 + strand) * 0.3, Math.cos(angle) * 0.8);

            this.opening.particle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, Festive.dust(strand == 0 ? this.color : Festive.WHITE, 1.1f));
        }

        Festive.snow(this.opening, 2);

        if (this.ticks % 12 == 0) {
            final int note = Festive.PENTATONIC[ThreadLocalRandom.current().nextInt(Festive.PENTATONIC.length)];

            Festive.note(this.opening, Sound.BLOCK_NOTE_BLOCK_CHIME, note, 0.4f);
        }

        if (this.ticks++ == LENGTH) done();

        this.delta += 0.25;
    }

    @Override
    public void finish() {
        this.opening.remove(this.item);
        this.opening.closeLid();
        this.opening.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }
}
