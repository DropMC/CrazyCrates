package com.badbones69.crazycrates.paper.api.objects.crates;

import com.badbones69.crazycrates.paper.api.enums.other.keys.ItemKeys;
import com.badbones69.crazycrates.paper.utils.ItemUtil;
import com.ryderbelserion.fusion.paper.builders.items.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The item an admin places to set a crate down, read from the crate's {@code Placeable} section. Placing it leaves
 * {@link #getBlock()} in the world, facing the admin, and breaking that block (sneaking) removes it, giving nothing back.
 */
public class CratePlaceable {

    private final ItemBuilder item;
    private final Material block;

    private CratePlaceable(@Nullable final ItemBuilder item, @NotNull final Material block) {
        this.item = item;
        this.block = block;
    }

    /**
     * Reads the placeable item from a crate's {@code Placeable} section.
     *
     * <pre>
     * Placeable:
     *   Item: light_gray_shulker_box   # must be a block, so the game lets it be placed; barrier for a model crate
     *   Name: "&lt;gray&gt;Caixa Comum"
     *   Lore: []
     *   Model: {Namespace: "", Id: ""} # optional item model
     *   Block: light_gray_shulker_box  # left in the world; barrier for a crate shown as a model
     * </pre>
     *
     * @param section the section, null when the crate has none
     * @param crateName the crate's name, stored on the item
     * @return the placeable item, disabled when the section is missing or names no item
     */
    public static @NotNull CratePlaceable from(@Nullable final ConfigurationSection section, @NotNull final String crateName) {
        if (section == null) return new CratePlaceable(null, Material.AIR);

        final String itemName = section.getString("Item", "").toLowerCase();

        if (itemName.isBlank()) return new CratePlaceable(null, Material.AIR);

        final ItemBuilder item = ItemBuilder.from(itemName);

        ItemUtil.addItemModel(item, section.getString("Model.Namespace", ""), section.getString("Model.Id", ""));

        item.withDisplayName(section.getString("Name", crateName))
                .withDisplayLore(section.getStringList("Lore"))
                .setPersistentString(ItemKeys.crate_block.getNamespacedKey(), crateName);

        final Material block = Material.matchMaterial(section.getString("Block", itemName));

        return new CratePlaceable(item, block != null && block.isBlock() ? block : Material.CHEST);
    }

    /**
     * Check if the crate can be placed as a block.
     *
     * @return true if the crate has a placeable item
     */
    public boolean isEnabled() {
        return this.item != null;
    }

    /**
     * Builds the item an admin places.
     *
     * @param amount how many
     * @return the item, or null when the crate cannot be placed
     */
    public @Nullable ItemStack getItem(final int amount) {
        return this.item == null ? null : this.item.setAmount(amount).asItemStack();
    }

    /**
     * Gets the block left in the world where the crate is placed.
     *
     * @return the block's material
     */
    public @NotNull Material getBlock() {
        return this.block;
    }
}
