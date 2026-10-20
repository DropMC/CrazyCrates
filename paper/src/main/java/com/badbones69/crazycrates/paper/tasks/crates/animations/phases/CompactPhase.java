package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PhoenixCrates' {@code COMPACT}: the crate blows apart, a small copy of it rises out of the smoke spinning fast, slows
 * down and bobs in the crate's color. The real crate is hidden meanwhile and comes back when the opening ends.
 */
public class CompactPhase extends OpeningPhase {

    private final Color color = this.opening.getAnimation().getOpenColor();

    private ItemDisplay copy;
    private int ticks;
    private double alpha;
    private double theta = Math.PI * 4;

    public CompactPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        final Location centre = this.opening.centre();

        this.opening.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        this.opening.sound(Sound.ENTITY_SKELETON_DEATH, 1.0f, 2.0f);
        this.opening.sound(Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);

        this.opening.particle(Particle.EXPLOSION, centre, 2, 0.5, 0.5, 0.5, 0.0, null);
        this.opening.particle(Particle.LARGE_SMOKE, centre, 7, 0.25, 0.0, 0.25, 0.2, null);
        this.opening.particle(Particle.BLOCK, centre, 20, 0.5, 0.5, 0.5, 0.3, this.opening.texture());

        final ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int index = 0; index < 20; index++) { // smoke sucked into the crate.
            final Vector from = new Vector(random.nextDouble(-2.0, 2.0), random.nextDouble(-2.0, 2.0), random.nextDouble(-2.0, 2.0));
            final Vector towards = from.clone().multiply(-0.5);

            this.opening.particle(Particle.SMOKE, centre.clone().add(from), 0, towards.getX(), towards.getY(), towards.getZ(), 0.4, null);
        }

        this.copy = this.opening.spawn(position(), ItemDisplay.class, display -> {
            display.setItemStack(this.opening.crateItem());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setRotation(this.opening.yaw(), 0.0f);
            display.setTeleportDuration(1);
            display.setTransformation(transformation());
        });

        this.opening.hideCrate();
    }

    @Override
    public void tick() {
        if (++this.ticks < 10) return;

        if (this.ticks == 10) {
            this.opening.sound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 2.0f);
            this.opening.sound(Sound.ENTITY_SLIME_DEATH, 0.5f, 0.1f);
            this.opening.sound(Sound.ENTITY_CHICKEN_EGG, 1.0f, 2.0f);
        }

        this.copy.teleportAsync(position()); // the copy stays on this region, so the move lands within the tick.
        this.copy.setInterpolationDelay(0);
        this.copy.setInterpolationDuration(1);
        this.copy.setTransformation(transformation());

        this.opening.particle(Particle.DUST, position(), 1, 0.5, 0.5, 0.5, 0.1, new Particle.DustOptions(this.color, 1.0f));

        this.alpha += 0.085;
        this.theta -= Math.clamp(this.theta * 0.05, 0.1, 0.2);

        if (this.theta <= -0.25) done();
    }

    @Override
    public void finish() {
        this.opening.remove(this.copy);
    }

    /** Where Phoenix's armor stand held the crate on its head: rising for the first thirty ticks, then bobbing. */
    private @NotNull Location position() {
        final Location location = this.opening.centre().add(0.0, 0.75 + Math.min(this.alpha, 2.5) + Math.sin(this.alpha) - 1.0, 0.0);
        location.setYaw(this.opening.yaw());

        return location;
    }

    private @NotNull Transformation transformation() {
        return new Transformation(new Vector3f(), new Quaternionf().rotateY((float) this.theta), new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf());
    }
}
