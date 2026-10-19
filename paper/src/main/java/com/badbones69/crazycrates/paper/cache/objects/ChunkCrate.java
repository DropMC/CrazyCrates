package com.badbones69.crazycrates.paper.cache.objects;

import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.CrazyCratesPaper;
import com.ryderbelserion.fusion.paper.FusionPaper;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;

public final class ChunkCrate {

    private final CrazyCrates plugin = JavaPlugin.getPlugin(CrazyCrates.class);
    private final CrazyCratesPaper platform = this.plugin.getPlatform();
    private final FusionPaper fusion = this.platform.getFusion();

    private final NamespacedKey locationKey;
    private final Location location;

    public ChunkCrate(final Location location) {
        this.locationKey = new NamespacedKey(this.plugin, this.fusion.replacePlaceholders(
                "{x}_{y}_{z}",
                Map.of(
                        "{x}", String.valueOf(location.getBlockX()),
                        "{y}", String.valueOf(location.getBlockY()),
                        "{z}", String.valueOf(location.getBlockZ())
                )
        ));

        this.location = location;
    }

    /**
     * Fetched on use, not in the constructor: a reload builds these on the global region thread on Folia, which may not
     * touch a chunk, so callers run {@link #init(String)} and {@link #remove()} on the location's own region.
     */
    private Chunk chunk() {
        return this.location.getChunk();
    }

    private String id;

    public ChunkCrate init(final String id) {
        final PersistentDataContainer container = chunk().getPersistentDataContainer();

        container.set(this.locationKey, PersistentDataType.STRING, this.id = id); // store crate id at the location key.

        return this;
    }

    public ChunkCrate remove() {
        final PersistentDataContainer container = chunk().getPersistentDataContainer();

        if (container.has(this.locationKey)) {
            container.remove(this.locationKey);
        }

        return this;
    }

    public String get() {
        final PersistentDataContainer container = chunk().getPersistentDataContainer();

        return container.getOrDefault(this.locationKey, PersistentDataType.STRING, "");
    }

    public NamespacedKey getLocationKey() {
        return this.locationKey;
    }

    public Location getLocation() {
        return this.location;
    }

    public String getId() {
        return this.id;
    }
}