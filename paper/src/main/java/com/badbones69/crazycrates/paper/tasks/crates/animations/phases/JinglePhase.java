package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

/**
 * {@code JINGLE}, a Christmas pre-open: the first line of Jingle Bells rings out on bells while snow starts falling and a
 * colored note pops over the crate on every beat. The model's first open animation (the sack shaking) plays meanwhile.
 */
public class JinglePhase extends OpeningPhase {

    /** Tick each note starts on, and the note block step it plays: E E E, E E E, E G C D E. */
    private static final int[] TICKS = {0, 4, 8, 16, 20, 24, 32, 36, 40, 46, 48};
    private static final int[] NOTES = {10, 10, 10, 10, 10, 10, 10, 13, 6, 8, 10};

    /** The last note rings this long before the crate opens. */
    private static final int LENGTH = 62;

    private int ticks;
    private int next;

    public JinglePhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        this.opening.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    @Override
    public void tick() {
        Festive.snow(this.opening, 2 + this.ticks / 12);

        if (this.next < TICKS.length && this.ticks == TICKS[this.next]) {
            ring(NOTES[this.next], this.next == TICKS.length - 1);

            this.next++;
        }

        if (this.ticks++ >= LENGTH) done();
    }

    private void ring(final int note, final boolean last) {
        Festive.note(this.opening, Sound.BLOCK_NOTE_BLOCK_BELL, note, 1.0f);
        Festive.note(this.opening, Sound.BLOCK_NOTE_BLOCK_CHIME, note, 0.5f);

        if (last) this.opening.sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.4f);

        final Location above = this.opening.base().add(0.0, 1.6, 0.0);

        // a note particle's color comes from its x offset with count 0, from 0 to 1 around the color wheel.
        this.opening.particle(Particle.NOTE, above, 0, note / 24.0, 0.0, 0.0, 1.0, null);
        this.opening.particle(Particle.DUST, above, 6, 0.35, 0.2, 0.35, 0.0, Festive.dust(Festive.random(), 1.2f));
    }
}
