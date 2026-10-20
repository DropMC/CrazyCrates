package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

/**
 * PhoenixCrates' {@code CRACK}: the crate shakes apart three times over three seconds, in pieces of itself and smoke,
 * before the lid opens.
 */
public class CrackPhase extends OpeningPhase {

    private static final int LENGTH = 57;

    private int ticks;

    public CrackPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void tick() {
        if (this.ticks == LENGTH || this.ticks % 30 == 0) {
            this.opening.particle(Particle.BLOCK, this.opening.centre(), 40, 0.5, 0.5, 0.5, 0.3, this.opening.texture());
            this.opening.particle(Particle.SMOKE, this.opening.centre(), 10, 0.3, 0.3, 0.3, 0.1, null);
            this.opening.sound(Sound.ENTITY_BAT_TAKEOFF, 0.4f, 0.7f);
            this.opening.sound(Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.7f, 0.8f);
        }

        if (this.ticks++ == LENGTH) done();
    }

    @Override
    public void finish() {
        this.opening.openLid();
    }
}
