package com.badbones69.crazycrates.paper.tasks.crates.animations;

import org.jetbrains.annotations.NotNull;

/**
 * One of the three phases of a {@code Physical} crate opening, ported from PhoenixCrates' phase runners. Every method runs
 * on the crate's region, once a tick while the phase is current.
 */
public abstract class OpeningPhase {

    protected final CrateOpening opening;

    private boolean done;

    protected OpeningPhase(@NotNull final CrateOpening opening) {
        this.opening = opening;
    }

    /** Called once, when the phase becomes current. */
    public void start() {}

    /** Called every tick until {@link #done()}. */
    public abstract void tick();

    /** Called once, when the opening moves past the phase or is cut short. Removes what the phase spawned. */
    public void finish() {}

    /** Marks the phase over; the opening moves on once the model's animation for it ended too. */
    protected final void done() {
        this.done = true;
    }

    public final boolean isDone() {
        return this.done;
    }

    /** A phase that does nothing, for {@code NONE}. It still opens the crate's lid, as PhoenixCrates' default does. */
    public static final class Instant extends OpeningPhase {

        public Instant(@NotNull final CrateOpening opening) {
            super(opening);
        }

        @Override
        public void tick() {
            done();
        }

        @Override
        public void finish() {
            this.opening.openLid();
        }
    }
}
