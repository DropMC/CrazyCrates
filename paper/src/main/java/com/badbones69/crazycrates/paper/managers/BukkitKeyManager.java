package com.badbones69.crazycrates.paper.managers;

import com.badbones69.crazycrates.paper.api.enums.other.keys.ItemKeys;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BukkitKeyManager extends us.crazycrew.crazycrates.api.KeyManager<ItemStack> {

    /**
     * The id Nexo stamps on every item it builds, read straight off the item so the plugin needs no Nexo API.
     */
    private static final NamespacedKey NEXO_ID = new NamespacedKey("nexo", "id");

    /**
     * Nexo item id to the crate whose physical key is that item.
     * <p>
     * A key is normally recognised by the crate name this plugin writes on it, but a crate whose key is a Nexo item
     * also accepts any item carrying that Nexo id, whatever else is on it. That is what keeps keys handed out by an
     * earlier crate plugin working: built on the same Nexo items, their name, lore and other data differ from a key
     * built here, but their Nexo id does not.
     */
    private final Map<String, String> nexoKeys = new ConcurrentHashMap<>();

    @Override
    public boolean isMatchingKey(@NotNull final ItemStack item, @NotNull final ItemStack comparing) {
        final String first_key = getKey(item);

        if (first_key.isBlank()) return false;

        final String second_key = getKey(comparing);

        if (second_key.isBlank()) return false;

        return first_key.equals(second_key);
    }

    @Override
    public final boolean isKey(@NotNull final ItemStack item) {
        return !getKey(item).isEmpty();
    }

    @Override
    public final String getKey(@NotNull final ItemStack item) {
        final PersistentDataContainerView container = item.getPersistentDataContainer();

        final String crate = container.get(ItemKeys.crate_key.getNamespacedKey(), PersistentDataType.STRING);

        if (crate != null) return crate;

        final String nexoId = container.get(NEXO_ID, PersistentDataType.STRING);

        if (nexoId == null) return "";

        return this.nexoKeys.getOrDefault(nexoId, "");
    }

    /**
     * Makes every item with this Nexo id count as a key of the crate.
     *
     * @param nexoId the Nexo item id
     * @param crateName the crate's file name
     * @return the other crate that already claimed this id, or null when the link was made
     */
    public final @Nullable String linkNexoKey(@NotNull final String nexoId, @NotNull final String crateName) {
        final String previous = this.nexoKeys.putIfAbsent(nexoId, crateName);

        return previous == null || previous.equals(crateName) ? null : previous;
    }

    /**
     * Forgets every Nexo id link, before the crates are loaded again.
     */
    public final void clearNexoKeys() {
        this.nexoKeys.clear();
    }
}
