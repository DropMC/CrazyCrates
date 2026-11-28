package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

/**
 * {@code RIBBON}, a Christmas pre-open: two strands of ribbon, in the crate's open color and white, unwind around the crate
 * from its top to the floor with rising chimes, then snap loose. The model's first open animation plays meanwhile.
 */
public class RibbonPhase extends OpeningPhase {

    private static final int LENGTH = 40;

    /** How far the strands sit from the crate's middle, and how many turns they make on the way down. */
    private static final double RADIUS = 0.7, TURNS = 2.5, TOP = 1.4;

    private final Color color = this.opening.getAnimation().getOpenColor();

    private int ticks;

    public RibbonPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void tick() {
        final double progress = (double) this.ticks / LENGTH;

        // the strands trail behind: each tick draws a short arc ending where the ribbon is now.
        for (int trail = 0; trail < 6; trail++) {
            final double at = Math.max(0.0, progress - trail * 0.012);

            strand(at, 0.0, this.color);
            strand(at, Math.PI, Festive.WHITE);
        }

        if (this.ticks % 5 == 0) {
            final int step = Math.min(this.ticks / 5, Festive.PENTATONIC.length - 1);

            Festive.note(this.opening, Sound.BLOCK_AMETHYST_BLOCK_CHIME, Festive.PENTATONIC[step], 1.0f);
            this.opening.sound(Sound.ITEM_ARMOR_EQUIP_LEATHER,0.6f, 1.4f);
        }

        if (this.ticks++ >= LENGTH) {
            snap();

            done();
        }
    }

    private void strand(final double progress, final double offset, @NotNull final Color tint) {
        final double angle = progress * TURNS * Math.PI * 2.0 + offset;

        final Location point = this.opening.base().add(Math.sin(angle) * RADIUS, TOP * (1.0 - progress), Math.cos(angle) * RADIUS);

        this.opening.particle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, Festive.dust(tint, 1.0f));
    }

    private void snap() {
        final Location top = this.opening.base().add(0.0, TOP, 0.0);

        this.opening.sound(Sound.ITEM_LEAD_UNTIED, 1.0f, 1.2f);
        this.opening.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, Festive.pitch(18));

        this.opening.particle(Particle.DUST, top, 20, 0.4, 0.1, 0.4, 0.0, Festive.dust(Festive.GOLD, 1.4f));
        this.opening.particle(Particle.END_ROD, top, 8, 0.2, 0.1, 0.2, 0.05, null);
    }
}
