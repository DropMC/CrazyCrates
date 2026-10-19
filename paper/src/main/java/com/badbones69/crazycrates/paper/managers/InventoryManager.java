package com.badbones69.crazycrates.paper.managers;

import com.badbones69.crazycrates.paper.api.CrazyCratesPaper;
import com.ryderbelserion.fusion.paper.builders.items.types.custom.CustomBuilder;
import us.crazycrew.crazycrates.api.config.impl.ConfigManager;
import us.crazycrew.crazycrates.api.config.impl.types.config.crate.CrateKeys;
import us.crazycrew.crazycrates.api.config.impl.types.config.gui.GuiKeys;
import us.crazycrew.crazycrates.api.config.properties.PropertyManager;
import us.crazycrew.crazycrates.api.enums.messages.Message;
import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.enums.other.keys.ItemKeys;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.Tier;
import com.ryderbelserion.fusion.paper.builders.gui.types.paginated.PaginatedGui;
import com.ryderbelserion.fusion.paper.builders.items.ItemBuilder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("WhileLoopReplaceableByForEach")
public class InventoryManager {

    private final CrazyCrates plugin = CrazyCrates.getPlugin();

    private final CrazyCratesPaper platform = this.plugin.getPlatform();

    private final ConfigManager configManager = this.platform.getConfigManager();
    private final PropertyManager pluginConfig = this.configManager.getConfig();

    private final Server server = this.plugin.getServer();

    private ItemBuilder fillerButton;
    private ItemBuilder menuButton;
    private ItemBuilder nextButton;
    private ItemBuilder backButton;

    public void loadButtons() {
        Map.of(
                "menu-button", this.pluginConfig.getProperty(GuiKeys.gui_menu_button),
                "back-button", this.pluginConfig.getProperty(GuiKeys.gui_back_button),
                "next-button", this.pluginConfig.getProperty(GuiKeys.gui_next_button),
                "filler-button", this.pluginConfig.getProperty(GuiKeys.gui_filler_button)
        ).forEach((name, button) -> {
            final ItemBuilder builder = ItemBuilder.from(button.getItem())
                    .withDisplayName(button.getName())
                    .withDisplayLore(button.getLore());

            final CustomBuilder customBuilder = builder.asCustomBuilder();

            customBuilder.setItemModel(button.getNamespace(), button.getId());
            customBuilder.setCustomModelData(button.getModelData());

            customBuilder.build();

            switch (name) {
                case "filler-button" -> {
                    if (button.isEnabled()) {
                        this.fillerButton = builder;
                    }
                }
                case "menu-button" -> this.menuButton = builder;
                case "back-button" -> this.backButton = builder;
                case "next-button" -> this.nextButton = builder;
            }
        });
    }

    public final ItemStack getMenuButton(@NotNull final Player player) {
        return this.menuButton.asItemStack(player);
    }

    public final Optional<ItemStack> getFillerButton(@NotNull final Player player) {
        if (this.fillerButton == null) {
            return Optional.empty();
        }

        return Optional.of(this.fillerButton.asItemStack(player));
    }

    public final ItemStack getNextButton(@Nullable final Player player, @Nullable final Tier tier, @NotNull final PaginatedGui gui) {
        final ItemBuilder button = this.nextButton;

        button.addPlaceholder("{page}", String.valueOf(gui.getPageNumber()));

        if (tier != null) {
            button.setPersistentString(ItemKeys.crate_tier.getNamespacedKey(), tier.getName());
        }

        return button.asItemStack(player);
    }

    public final ItemStack getNextButton(@Nullable final Player player, @NotNull final PaginatedGui gui) {
        return getNextButton(player, null, gui);
    }

    public final ItemStack getBackButton(@Nullable final Player player, @Nullable final Tier tier, @NotNull final PaginatedGui gui) {
        final ItemBuilder button = this.backButton;

        button.addPlaceholder("{page}", String.valueOf(gui.getPageNumber()));

        if (tier != null) {
            button.setPersistentString(ItemKeys.crate_tier.getNamespacedKey(), tier.getName());
        }

        return button.asItemStack(player);
    }

    public final ItemStack getBackButton(@Nullable final Player player, @NotNull final PaginatedGui gui) {
        return getBackButton(player, null, gui);
    }

    public void openNewCratePreview(@NotNull final Player player, @NotNull final Crate crate) {
        if (crate.getCrateType() == CrateType.casino || crate.getCrateType() == CrateType.cosmic && crate.isPreviewTierToggle()) {
            crate.getTierPreview(player).open();

            return;
        }

        crate.getPreview(player).open();
    }

    /**
     * Copy on write because players join and leave previews from their own region threads, and because
     * {@link #closePreview()} closing an inventory removes its viewer from this list while the list is being walked.
     */
    private final List<UUID> previewViewers = new CopyOnWriteArrayList<>();

    public void addPreviewViewer(@NotNull final UUID uuid) {
        this.previewViewers.add(uuid);
    }

    public void removePreviewViewer(@NotNull final UUID uuid) {
        this.previewViewers.remove(uuid);
    }

    public final List<UUID> getPreviewViewers() {
        return Collections.unmodifiableList(this.previewViewers);
    }

    public final boolean hasPreviewViewer(@NotNull final UUID uuid) {
        return this.previewViewers.contains(uuid);
    }

    public void openPreview(@NotNull final Crate crate) {
        for (final UUID uuid : this.previewViewers) {
            final Player player = this.server.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                removePreviewViewer(uuid);

                continue;
            }

            onPlayerThread(player, () -> openNewCratePreview(player, crate));
        }
    }

    public void closePreview() {
        final boolean isPreviewExitMessageSent = this.pluginConfig.getProperty(CrateKeys.send_preview_taken_out_message);

        for (final UUID uuid : this.previewViewers) {
            final Player player = this.server.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                removePreviewViewer(uuid);

                continue;
            }

            onPlayerThread(player, () -> {
                player.closeInventory(InventoryCloseEvent.Reason.UNLOADED);

                if (isPreviewExitMessageSent) {
                    Message.preview_force_exit.sendMessage(player);
                }
            });
        }
    }

    /**
     * Runs the action on the thread that owns the player. A reload runs on the global region thread on Folia, and an
     * inventory may only be opened or closed from the player's own region.
     */
    private void onPlayerThread(@NotNull final Player player, @NotNull final Runnable action) {
        new FoliaScheduler(this.plugin, null, player) {
            @Override
            public void run() {
                action.run();
            }
        }.runNow();
    }
}