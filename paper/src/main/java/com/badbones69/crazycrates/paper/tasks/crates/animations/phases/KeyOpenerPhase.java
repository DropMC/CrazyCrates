package com.badbones69.crazycrates.paper.tasks.crates.animations.phases;

import com.badbones69.crazycrates.paper.tasks.crates.animations.CrateOpening;
import com.badbones69.crazycrates.paper.tasks.crates.animations.OpeningPhase;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * PhoenixCrates' {@code KEY_OPENER}: the crate's key slides into the front of the crate, waits, turns half a circle with a
 * click, and smoke comes out for two seconds before the lid opens. Phoenix moves the key with an armor stand's arm; this
 * moves an item display, which the client interpolates between ticks.
 */
public class KeyOpenerPhase extends OpeningPhase {

    /** How far in front of the crate's face the key starts and ends sliding. */
    private static final double START = 0.75, END = 0.28, SLIDE = 0.05;

    /** Keys are modelled upright with the bit at the bottom (the menus tilt them), so this lays the bit along the slide, into the crate. */
    private static final float TILT = (float) Math.toRadians(90.0);

    private ItemDisplay key;
    private Location keyhole;
    private int step;
    private int ticks;
    private double distance = START;
    private double turn;

    public KeyOpenerPhase(@NotNull final CrateOpening opening) {
        super(opening);
    }

    @Override
    public void start() {
        this.keyhole = this.opening.centre().add(this.opening.facing().multiply(0.5));

        this.key = this.opening.spawn(position(), ItemDisplay.class, display -> {
            display.setItemStack(this.opening.getCrate().getKey());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // local +X points into the crate: the display's yaw is the crate's, turned a quarter to the right.
            display.setRotation(this.opening.yaw() - 90.0f, 0.0f);
            display.setTeleportDuration(1);
            display.setTransformation(transformation(0.0));
        });
    }

    @Override
    public void tick() {
        switch (this.step) {
            case 0 -> { // slide in.
                this.distance -= SLIDE;

                if (this.distance <= END) {
                    this.distance = END;

                    this.opening.sound(Sound.BLOCK_STONE_STEP, 2.0f, 0.5f);

                    next();
                }

                this.key.teleportAsync(position()); // Folia forbids teleport() on region threads.
            }

            case 1 -> { // wait half a second, then turn.
                if (++this.ticks <= 10) return;

                this.turn = Math.min(Math.PI, this.turn + 0.1);

                this.key.setInterpolationDelay(0);
                this.key.setInterpolationDuration(1);
                this.key.setTransformation(transformation(this.turn));

                if (this.turn >= Math.PI) {
                    this.opening.sound(Sound.BLOCK_LEVER_CLICK, 2.0f, 1.0f);

                    next();
                }
            }

            default -> { // smoke.
                if (++this.ticks <= 40) {
                    this.opening.particle(Particle.SMOKE, this.keyhole, 2, 0.2, 0.2, 0.2, 0.1, null);

                    return;
                }

                done();
            }
        }
    }

    @Override
    public void finish() {
        this.opening.remove(this.key);
        this.opening.openLid();
    }

    private void next() {
        this.step++;
        this.ticks = 0;
    }

    private @NotNull Location position() {
        final Vector out = this.opening.facing().multiply(this.distance);

        final Location location = this.keyhole.clone().add(out);
        location.setYaw(this.opening.yaw() - 90.0f);
        location.setPitch(0.0f);

        return location;
    }

    private @NotNull Transformation transformation(final double turn) {
        final Quaternionf rotation = new Quaternionf().rotateX((float) turn).rotateZ(TILT);

        return new Transformation(new Vector3f(), rotation, new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf(new AxisAngle4f()));
    }
}
