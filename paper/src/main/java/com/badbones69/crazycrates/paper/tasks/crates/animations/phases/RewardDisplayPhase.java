package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * PhoenixCrates' {@code DEFAULT} display: a bang above the crate, then the prize floats there, spinning, with its name
 * over it for three and a half seconds while colored sparks circle overhead. The prize is given as it appears.
 */
public class RewardDisplayPhase extends OpeningPhase {

    private static final int LENGTH = 70;

    private final Color color = this.opening.getAnimation().getDisplayColor();

    private Item item;
    private int ticks;
    private double delta;

    public RewardDisplayPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        final Location location = this.opening.displayLocation();

        burst(this.opening, location);

        this.opening.sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.0f);
        this.opening.sound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        this.item = showPrize(this.opening, location, false);

        this.opening.givePrize();
    }

    @Override
    public void tick() {
        if (!this.opening.isCrateHidden()) {
            this.opening.particle(Particle.DUST, this.opening.base(), 1, 0.5, 0.0, 0.5, 0.0, new Particle.DustOptions(this.color, 1.0f));
        }

        final Location spark = this.opening.base().add(Math.sin(this.delta), 3.0 + Math.sin(this.delta / 2.0) / 2.0, Math.cos(this.delta));

        this.opening.particle(Particle.ENTITY_EFFECT, spark, 1, 0.0, 0.0, 0.0, 0.0, this.color);

        if (this.ticks % 10 == 0) this.opening.sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.3f, 1.0f);

        if (this.ticks++ == LENGTH) done();

        this.delta += 0.3;
    }

    @Override
    public void finish() {
        this.opening.remove(this.item);
        this.opening.closeLid();
        this.opening.sound(Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.2f);
    }

    /** The smoke and bang the prize appears in, shared with {@link PhysicalItemPhase}. */
    static void burst(@NotNull final CrateOpening opening, @NotNull final Location location) {
        opening.particle(Particle.SMOKE, location, 20, 0.2, 0.2, 0.2, 0.4, null);
        opening.particle(Particle.POOF, location, 1, 0.0, 0.0, 0.0, 0.0, null);
        opening.particle(Particle.ENTITY_EFFECT, location, 10, 0.0, 0.0, 0.0, 0.5, Color.fromRGB(0x555555));
        opening.particle(Particle.EXPLOSION, location, 1, 0.0, 0.0, 0.0, 0.0, null);
    }

    /**
     * The prize's display item as an item on the ground that nobody can pick up, named after the prize.
     *
     * @param falls true to pop it up and let it fall, false to keep it floating in place
     */
    static @NotNull Item showPrize(@NotNull final CrateOpening opening, @NotNull final Location location, final boolean falls) {
        final ItemStack stack = opening.getPrize().getDisplayItem(opening.getPlayer(), opening.getCrate());

        return opening.spawn(location, Item.class, item -> {
            item.setItemStack(stack);
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setUnlimitedLifetime(true);
            item.setGravity(falls);
            item.setVelocity(falls ? new Vector(0.0, 0.2, 0.0) : new Vector());
            item.customName(Component.text(stack.getAmount() + "x ", NamedTextColor.YELLOW).append(stack.effectiveName()));
            item.setCustomNameVisible(true);
        });
    }
}
