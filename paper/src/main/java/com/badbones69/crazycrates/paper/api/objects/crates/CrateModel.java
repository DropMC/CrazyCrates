package com.badbones69.crazycrates.paper.api.objects.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The model shown at a crate's locations in place of a block, read from the crate's {@code Model} section.
 */
public class CrateModel {

    private final boolean enabled;
    private final String name;
    private final String idleAnimation;
    private final int range;
    private final Vector offset;
    private final List<String> openAnimations;
    private final String closeAnimation;

    /**
     * Empty constructor, for crates without a model.
     */
    public CrateModel() {
        this(false, "", "", 48, new Vector(0.5, 0.0, 0.5), List.of(), "");
    }

    /**
     * A secondary constructor to build a model.
     *
     * @param enabled if the model is shown or not
     * @param name the model's name in the model plugin
     * @param idleAnimation the animation looped while nobody opens the crate, empty for none
     * @param range range the model can be seen from
     * @param offset where the model stands, relative to the corner of the crate's block
     * @param openAnimations the animations played by the three opening phases of a {@code Physical} crate, in order
     * @param closeAnimation the animation played once the prize was shown, empty for none
     */
    public CrateModel(final boolean enabled, @NotNull final String name, @NotNull final String idleAnimation, final int range, @NotNull final Vector offset,
                      @NotNull final List<String> openAnimations, @NotNull final String closeAnimation) {
        this.enabled = enabled;
        this.name = name;
        this.idleAnimation = idleAnimation;
        this.range = range;
        this.offset = offset;
        this.openAnimations = List.copyOf(openAnimations);
        this.closeAnimation = closeAnimation;
    }

    /**
     * Reads the model from a crate's {@code Model} section.
     *
     * @param section the section, null when the crate has none
     * @return the model, disabled when the section is missing
     */
    public static @NotNull CrateModel from(@Nullable final ConfigurationSection section) {
        if (section == null) return new CrateModel();

        return new CrateModel(
                section.getBoolean("Toggle", false),
                section.getString("Name", ""),
                section.getString("Idle-Animation", ""),
                section.getInt("Range", 48),
                new Vector(
                        section.getDouble("Offset.X", 0.5),
                        section.getDouble("Offset.Y", 0.0),
                        section.getDouble("Offset.Z", 0.5)
                ),
                section.getStringList("Open-Animations"),
                section.getString("Close-Animation", "")
        );
    }

    /**
     * Check if the model is enabled or not.
     *
     * @return true if it is enabled and names a model
     */
    public boolean isEnabled() {
        return this.enabled && !this.name.isBlank();
    }

    /**
     * Gets the model's name in the model plugin.
     *
     * @return the name
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * Gets the animation looped while nobody opens the crate.
     *
     * @return the animation, empty for none
     */
    public @NotNull String getIdleAnimation() {
        return this.idleAnimation;
    }

    /**
     * Gets the range at which the model can be seen.
     *
     * @return the range
     */
    public int getRange() {
        return this.range;
    }

    /**
     * Gets where the model stands, relative to the corner of the crate's block.
     *
     * @return a copy of the offset
     */
    public @NotNull Vector getOffset() {
        return this.offset.clone();
    }

    /**
     * Gets the animation a {@code Physical} crate plays during one of its opening phases.
     *
     * @param phase 0 for the key going in, 1 for the crate opening, 2 for the prize
     * @return the animation, empty for none
     */
    public @NotNull String getOpenAnimation(final int phase) {
        return phase < this.openAnimations.size() ? this.openAnimations.get(phase) : "";
    }

    /**
     * Gets the animation played once the prize was shown.
     *
     * @return the animation, empty for none
     */
    public @NotNull String getCloseAnimation() {
        return this.closeAnimation;
    }
}
