package com.badbones69.crazycrates.paper.utils;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a crate faces, as a yaw snapped to the 16 directions a sign or a skull can take. Yaw 0 faces south and grows
 * clockwise, like an entity's.
 */
public final class CrateFacing {

    private static final BlockFace[] FACES = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    private static final float STEP = 360.0f / FACES.length;

    private CrateFacing() {}

    /**
     * @param yaw any yaw
     * @return the yaw snapped to the nearest of the 16 directions, between -180 and 180
     */
    public static float snap(final float yaw) {
        return Location.normalizeYaw(Math.round(yaw / STEP) * STEP);
    }

    /**
     * @param yaw any yaw
     * @return the nearest of the 16 directions
     */
    public static @NotNull BlockFace faceOf(final float yaw) {
        return FACES[Math.floorMod(Math.round(yaw / STEP), FACES.length)];
    }

    /**
     * @param face one of the 16 directions, as PhoenixCrates stores it
     * @return its yaw, or null when the face is up, down or self
     */
    public static @Nullable Float yawOf(@NotNull final BlockFace face) {
        for (int index = 0; index < FACES.length; index++) {
            if (FACES[index] == face) return snap(index * STEP);
        }

        return null;
    }

    /**
     * Turns a block to face the yaw as close as it can: a rotatable block takes all 16 directions, a directional one the
     * nearest side it allows, except one that can face up (a shulker box), which stands upright like any placed on the floor.
     *
     * @param data the block's data, changed in place
     * @param yaw where the crate faces
     */
    public static void apply(@NotNull final BlockData data, final float yaw) {
        if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(faceOf(yaw));

            return;
        }

        if (data instanceof Directional directional) {
            if (directional.getFaces().contains(BlockFace.UP)) { // a shulker box stands upright, as when placed on the floor.
                directional.setFacing(BlockFace.UP);

                return;
            }

            final BlockFace side = faceOf(Math.round(yaw / 90.0f) * 90.0f);

            if (directional.getFaces().contains(side)) directional.setFacing(side);
        }
    }
}
