package com.ryderbelserion.crazycrates.common.objects;

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CrazyLocation {

    private final String crateName;
    private final Key worldKey;
    private final String id;
    private final int x;
    private final int y;
    private final int z;
    private final float yaw;

    public CrazyLocation(final String crateName, final Key worldKey, final String id, final int x, final int y, final int z) {
        this(crateName, worldKey, id, x, y, z, 0.0f);
    }

    /**
     * @param yaw where the crate faces, in degrees like an entity's yaw (0 is south). Placed crates face the player who
     *            placed them, and a crate model is turned to match.
     */
    public CrazyLocation(final String crateName, final Key worldKey, final String id, final int x, final int y, final int z, final float yaw) {
        this.crateName = crateName;
        this.worldKey = worldKey;
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public Key getWorldKey() {
        return this.worldKey;
    }

    @Deprecated
    public String getWorldName() {
        return getWorldId();
    }

    public String getWorldId() {
        return this.worldKey.asString();
    }

    public String getCrateName() {
        return this.crateName;
    }

    public String getId() {
        return this.id;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public float getYaw() {
        return this.yaw;
    }
}
