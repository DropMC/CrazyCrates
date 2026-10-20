package com.badbones69.crazycrates.paper.api.objects.crates;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.kyori.adventure.key.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a {@code Physical} crate opens in the world, read from the crate's {@code Animation} and {@code Idle-Effects}
 * sections. The three phases follow PhoenixCrates': the key goes in, the crate bursts open, the prize is shown.
 *
 * <pre>
 * Animation:
 *   Pre-Open: {Type: KEY_OPENER}                  # NONE, KEY_OPENER, CRACK
 *   Open: {Type: SWIRL, Color: "#FF8A1F"}         # NONE, SWIRL, COMPACT
 *   Display: {Type: DEFAULT, Color: "#8B3DFF"}    # DEFAULT, PHYSICAL_ITEM
 *   Display-Item-Position: {X: 0.0, Y: 2.0, Z: 0.0}
 * Idle-Effects:
 *   - {Particle: dust, Color: "#FF8A1F", Offset: {X: 0.0, Y: 0.0, Z: 0.0}, Velocity: 1.0, Spread: 0.1, Amount: 2}
 * </pre>
 */
public class CrateAnimation {

    public enum PreOpen { NONE, KEY_OPENER, CRACK }

    public enum Open { NONE, SWIRL, COMPACT }

    public enum Display { DEFAULT, PHYSICAL_ITEM }

    private final PreOpen preOpen;
    private final Open open;
    private final Color openColor;
    private final Display display;
    private final Color displayColor;
    private final Vector displayPosition;
    private final List<IdleEffect> idleEffects;

    private CrateAnimation(@NotNull final PreOpen preOpen, @NotNull final Open open, @NotNull final Color openColor, @NotNull final Display display,
                           @NotNull final Color displayColor, @NotNull final Vector displayPosition, @NotNull final List<IdleEffect> idleEffects) {
        this.preOpen = preOpen;
        this.open = open;
        this.openColor = openColor;
        this.display = display;
        this.displayColor = displayColor;
        this.displayPosition = displayPosition;
        this.idleEffects = idleEffects;
    }

    /**
     * Reads the animation from a crate's section.
     *
     * @param section the crate's section
     * @return the animation, PhoenixCrates' defaults for whatever is missing
     */
    public static @NotNull CrateAnimation from(@NotNull final ConfigurationSection section) {
        final ConfigurationSection animation = section.getConfigurationSection("Animation");

        final List<IdleEffect> idleEffects = new ArrayList<>();

        for (final Map<?, ?> map : section.getMapList("Idle-Effects")) {
            final IdleEffect effect = IdleEffect.from(map);

            if (effect != null) idleEffects.add(effect);
        }

        if (animation == null) {
            return new CrateAnimation(PreOpen.NONE, Open.SWIRL, Color.WHITE, Display.DEFAULT, Color.WHITE, new Vector(0.0, 2.0, 0.0), idleEffects);
        }

        return new CrateAnimation(
                parse(PreOpen.class, animation.getString("Pre-Open.Type", "NONE"), PreOpen.NONE),
                parse(Open.class, animation.getString("Open.Type", "SWIRL"), Open.SWIRL),
                color(animation.getString("Open.Color", "")),
                parse(Display.class, animation.getString("Display.Type", "DEFAULT"), Display.DEFAULT),
                color(animation.getString("Display.Color", "")),
                new Vector(
                        animation.getDouble("Display-Item-Position.X", 0.0),
                        animation.getDouble("Display-Item-Position.Y", 2.0),
                        animation.getDouble("Display-Item-Position.Z", 0.0)
                ),
                idleEffects
        );
    }

    private static <E extends Enum<E>> @NotNull E parse(@NotNull final Class<E> type, @NotNull final String value, @NotNull final E fallback) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException _) {
            return fallback;
        }
    }

    /**
     * @param value a hex color, {@code #RRGGBB}
     * @return the color, white when blank or malformed
     */
    static @NotNull Color color(@Nullable final String value) {
        if (value == null || value.isBlank()) return Color.WHITE;

        try {
            return Color.fromRGB(Integer.parseInt(value.replace("#", "").trim(), 16));
        } catch (final IllegalArgumentException _) {
            return Color.WHITE;
        }
    }

    public @NotNull PreOpen getPreOpen() {
        return this.preOpen;
    }

    public @NotNull Open getOpen() {
        return this.open;
    }

    public @NotNull Color getOpenColor() {
        return this.openColor;
    }

    public @NotNull Display getDisplay() {
        return this.display;
    }

    public @NotNull Color getDisplayColor() {
        return this.displayColor;
    }

    /**
     * @return where the prize floats, relative to the middle of the crate's block floor, before turning with the crate
     */
    public @NotNull Vector getDisplayPosition() {
        return this.displayPosition.clone();
    }

    public @NotNull List<IdleEffect> getIdleEffects() {
        return this.idleEffects;
    }

    /**
     * Particles shown around a crate while nobody opens it, like PhoenixCrates' {@code DEFAULT} idle effect: every other
     * tick, {@code amount} particles at the middle of the block, moved by the offset and a random spread.
     */
    public record IdleEffect(@NotNull Particle particle, @NotNull Color color, @NotNull Vector offset, double velocity, double spread, int amount) {

        static @Nullable IdleEffect from(@NotNull final Map<?, ?> map) {
            final Particle particle = Registry.PARTICLE_TYPE.get(Key.key(text(map.get("Particle"), "dust").toLowerCase(Locale.ROOT)));

            if (particle == null) return null;

            final Map<?, ?> offset = map.get("Offset") instanceof Map<?, ?> value ? value : Map.of();

            return new IdleEffect(
                    particle,
                    CrateAnimation.color(text(map.get("Color"), "")),
                    new Vector(number(offset.get("X"), 0.0), number(offset.get("Y"), 0.0), number(offset.get("Z"), 0.0)),
                    number(map.get("Velocity"), 0.0),
                    number(map.get("Spread"), 0.0),
                    Math.clamp((int) number(map.get("Amount"), 1.0), 1, 10)
            );
        }

        private static @NotNull String text(@Nullable final Object value, @NotNull final String fallback) {
            return value == null ? fallback : String.valueOf(value);
        }

        private static double number(@Nullable final Object value, final double fallback) {
            return value instanceof Number number ? number.doubleValue() : fallback;
        }
    }
}
