package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;

/**
 * PhoenixCrates' {@code PHYSICAL_ITEM} display: the lid shuts, and the prize pops out of a bang above the crate as an item
 * that falls back onto it, named after the prize, for three and a half seconds. The prize is given as it appears.
 */
public class PhysicalItemPhase extends OpeningPhase {

    private static final int LENGTH = 70;

    private final Color color = this.opening.getAnimation().getDisplayColor();

    private Item item;
    private int ticks;

    public PhysicalItemPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        this.opening.closeLid();

        final Location location = this.opening.displayLocation();

        RewardDisplayPhase.burst(this.opening, location);

        this.opening.sound(Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.2f);
        this.opening.sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.0f);
        this.opening.sound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        this.item = RewardDisplayPhase.showPrize(this.opening, location, true);

        this.opening.givePrize();
    }

    @Override
    public void tick() {
        this.opening.particle(Particle.DUST, this.opening.base(), 2, 0.6, 0.0, 0.6, 0.0, new Particle.DustOptions(this.color, 1.0f));

        if (this.ticks++ == LENGTH) done();
    }

    @Override
    public void finish() {
        this.opening.remove(this.item);
        this.opening.sound(Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.2f);
    }
}
