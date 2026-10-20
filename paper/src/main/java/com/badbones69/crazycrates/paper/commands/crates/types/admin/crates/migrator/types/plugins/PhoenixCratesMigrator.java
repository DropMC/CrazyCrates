package com.badbones69.crazycrates.paper.commands.crates.types.admin.crates.migrator.types.plugins;

import java.util.LinkedHashMap;
import com.badbones69.crazycrates.paper.utils.CrateFacing;
import org.bukkit.block.BlockFace;
import com.badbones69.crazycrates.paper.api.enums.other.Plugins;
import com.badbones69.crazycrates.paper.commands.crates.types.admin.crates.migrator.ICrateMigrator;
import com.badbones69.crazycrates.paper.commands.crates.types.admin.crates.migrator.enums.MigrationType;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.files.enums.FileAction;
import com.ryderbelserion.fusion.paper.utils.ItemUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PhoenixCrates crates into CrazyCrates crates, reading PhoenixCrates' files directly so the plugin does not
 * have to be installed.
 * <p>
 * Every item is rebuilt as a real item stack (from PhoenixCrates' internal storage, from Nexo or from a plain
 * material, with its name, lore and enchantments applied) and written as base64 data, so a prize gives exactly what
 * PhoenixCrates gave. A crate whose key is a Nexo item keeps that item as its key ({@code nexo@<id>}), which is what
 * lets keys PhoenixCrates already handed out open the migrated crate.
 * <p>
 * Crates that already exist here are skipped, never overwritten. Anything PhoenixCrates does that has no equivalent
 * here (opening animations, idle effects, rerolls, costs, cooldowns, win limits) is listed in the console per crate.
 */
public class PhoenixCratesMigrator extends ICrateMigrator {

    private static final String DEFAULT_CRATE_TYPE = "Physical";

    private static final NamespacedKey NEXO_ID = new NamespacedKey("nexo", "id");

    private static final Pattern LEGACY_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"), Map.entry('r', "reset")
    );

    public PhoenixCratesMigrator(@NotNull final CommandSender sender) {
        super(sender, MigrationType.PHOENIX_CRATES);
    }

    @Override
    public void run() {
        final Path phoenix = this.dataPath.getParent().resolve("PhoenixCrates");
        final Path phoenixCrates = phoenix.resolve("crates");

        if (!Files.isDirectory(phoenixCrates)) {
            this.fusion.log(Level.WARNING, "There is no PhoenixCrates crates folder at %s.", phoenixCrates);

            sendMessage(List.of(), 0, 0);

            return;
        }

        final List<String> failed = new ArrayList<>();
        final List<String> success = new ArrayList<>();

        final YamlConfiguration locations = YamlConfiguration.loadConfiguration(phoenix.resolve("locations.yml").toFile());

        for (final Path file : this.fusion.getFilesByPath(phoenixCrates, ".yml")) {
            final String fileName = file.getFileName().toString();

            try {
                if (migrateCrate(phoenix, file, locations)) {
                    success.add("<green>⤷ " + fileName);
                } else {
                    failed.add("<red>⤷ " + fileName);
                }
            } catch (final RuntimeException | IOException exception) {
                this.fusion.log(Level.WARNING, "Failed to migrate the PhoenixCrates crate %s.", exception, fileName);

                failed.add("<red>⤷ " + fileName);
            }
        }

        this.crateManager.loadHolograms();
        this.crateManager.loadCrates();

        final List<String> files = new ArrayList<>(failed.size() + success.size());

        files.addAll(failed);
        files.addAll(success);

        sendMessage(files, success.size(), failed.size());
    }

    private boolean migrateCrate(@NotNull final Path phoenix, @NotNull final Path file, @NotNull final YamlConfiguration locations) throws IOException {
        final YamlConfiguration source = YamlConfiguration.loadConfiguration(file.toFile());

        final String crateName = source.getString("identifier", file.getFileName().toString().replace(".yml", ""));
        final Path output = getDirectory().resolve(crateName + ".yml");

        if (Files.exists(output)) {
            this.fusion.log(Level.WARNING, "Crate %s already exists in crates, skipping it.", crateName);

            return false;
        }

        final List<String> notMigrated = new ArrayList<>();
        final String displayName = toMiniMessage(source.getString("item.display-name", crateName));

        final YamlConfiguration target = new YamlConfiguration();
        final ConfigurationSection root = target.createSection("Crate");

        writeCrate(root, source, displayName);
        writeHologram(root, source, displayName);
        writeModel(root, source, notMigrated);
        writePlaceable(root, source, displayName);
        writeAnimation(root, source, notMigrated);

        if (!writeKey(root, phoenix, source, displayName, crateName)) return false;

        writePrizes(root, source, crateName, notMigrated);

        listUnsupported(source, notMigrated);

        Files.createDirectories(output.getParent());
        target.save(output.toFile());

        // The file is already on disk; without this the file manager looks for it inside the jar and throws.
        this.fileManager.addPaperFile(output, custom -> custom.addAction(FileAction.ALREADY_EXTRACTED));

        addLocations(crateName, locations);

        if (!notMigrated.isEmpty()) {
            this.fusion.log(Level.WARNING, "%s was migrated without: %s", crateName, String.join("; ", notMigrated));
        }

        return true;
    }

    private void writeCrate(@NotNull final ConfigurationSection root, @NotNull final YamlConfiguration source, @NotNull final String displayName) {
        set(root, "CrateType", DEFAULT_CRATE_TYPE);
        set(root, "StartingKeys", 0);
        set(root, "RequiredKeys", 0);
        set(root, "Max-Mass-Open", 10);
        set(root, "InGUI", false);
        set(root, "Slot", 0);
        set(root, "OpeningBroadCast", false);
        set(root, "BroadCast", "");

        set(root, "opening-command.toggle", false);
        set(root, "opening-command.commands", List.of());

        set(root, "sound.cycle-sound.toggle", true);
        set(root, "sound.cycle-sound.value", "block.note_block.xylophone");
        set(root, "sound.cycle-sound.volume", 1.0);
        set(root, "sound.cycle-sound.pitch", 1.0);
        set(root, "sound.click-sound.toggle", true);
        set(root, "sound.click-sound.value", "ui.button.click");
        set(root, "sound.click-sound.volume", 1.0);
        set(root, "sound.click-sound.pitch", 1.0);
        set(root, "sound.stop-sound.toggle", true);
        set(root, "sound.stop-sound.value", "entity.player.levelup");
        set(root, "sound.stop-sound.volume", 1.0);
        set(root, "sound.stop-sound.pitch", 1.0);

        set(root, "Prize-Message", List.of());
        set(root, "Prize-Commands", List.of());

        set(root, "Settings.Tracking-Crate-Opening", true);
        set(root, "Settings.Knock-Back", false);
        set(root, "Settings.Broadcast.Toggle", false);

        set(root, "Item", source.getString("block-material", "chest").toLowerCase());
        set(root, "Glowing", "none");
        set(root, "Name", displayName);
        set(root, "Lore", List.of());

        set(root, "Preview.Name", displayName);
        set(root, "Preview.Toggle", true);
        set(root, "Preview.Rows", 6);
        set(root, "Preview.Glass.Toggle", true);
        set(root, "Preview.Glass.Name", " ");
        set(root, "Preview.Glass.Item", "gray_stained_glass_pane");
    }

    /**
     * Turns a model engine, {@code MODEL_ENGINE;{model;open_1;open_2;open_3;x;y;z;idle;close}} or the same with
     * {@code BETTER_MODEL}, into a {@code Model} section shown through BetterModel, with the animations each opening phase
     * plays.
     */
    private void writeModel(@NotNull final ConfigurationSection root, @NotNull final YamlConfiguration source, @NotNull final List<String> notMigrated) {
        final String engine = source.getString("engine", "VANILLA_BLOCK");

        if (engine.equals("VANILLA_BLOCK")) return;

        final int open = engine.indexOf(";{");

        final String type = open < 0 ? engine : engine.substring(0, open);
        final String[] parts = open < 0 || !engine.endsWith("}") ? new String[0] : engine.substring(open + 2, engine.length() - 1).split(";");

        if (!type.equals("MODEL_ENGINE") && !type.equals("BETTER_MODEL") || parts.length < 9) {
            notMigrated.add("engine " + engine);

            return;
        }

        set(root, "Model.Toggle", true);
        set(root, "Model.Name", parts[0]);
        set(root, "Model.Idle-Animation", parts[7]);
        set(root, "Model.Range", 48);
        set(root, "Model.Offset.X", 0.5 + parseDouble(parts[4]));
        set(root, "Model.Offset.Y", parseDouble(parts[5]));
        set(root, "Model.Offset.Z", 0.5 + parseDouble(parts[6]));
        set(root, "Model.Open-Animations", List.of(parts[1], parts[2], parts[3]));
        set(root, "Model.Close-Animation", parts[8]);
    }

    /**
     * The crate's {@code item} becomes the item an admin places to set it down. A crate shown as a model leaves an
     * invisible barrier under it, so the block can still be clicked.
     */
    private void writePlaceable(@NotNull final ConfigurationSection root, @NotNull final YamlConfiguration source, @NotNull final String displayName) {
        final String blockMaterial = source.getString("block-material", "chest").toLowerCase();
        final ItemStack item = resolveItem(source, source.getString("item.material", blockMaterial));

        final boolean model = !source.getString("engine", "VANILLA_BLOCK").equals("VANILLA_BLOCK");

        // a model crate is placed as the barrier it stands on; any other item would show its own block under the model.
        final String itemName = model ? "barrier" : item != null && item.getType().isBlock() ? item.getType().getKey().getKey() : blockMaterial;

        set(root, "Placeable.Item", itemName);
        set(root, "Placeable.Name", displayName);
        set(root, "Placeable.Lore", List.of());
        set(root, "Placeable.Block", model ? "barrier" : blockMaterial);
    }

    /**
     * The three phases of {@code opening-animation} and the {@code idle-effects}, for the {@code Physical} crate type. A
     * phase is either a bare id or a section with an {@code id} and its {@code properties}; the ones this port lacks
     * fall back to the closest it has.
     */
    private void writeAnimation(@NotNull final ConfigurationSection root, @NotNull final YamlConfiguration source, @NotNull final List<String> notMigrated) {
        final String preOpen = phaseId(source, "pre-open");
        final String open = phaseId(source, "open");
        final String display = phaseId(source, "display");

        set(root, "Animation.Pre-Open.Type", switch (preOpen) {
            case "KEY_OPENER", "CRACK" -> preOpen;
            default -> "NONE";
        });

        set(root, "Animation.Open.Type", open.equals("COMPACT") ? "COMPACT" : "SWIRL");
        set(root, "Animation.Open.Color", source.getString("opening-animation.open.properties.color", "#FFFFFF"));

        set(root, "Animation.Display.Type", display.equals("PHYSICAL_ITEM") ? "PHYSICAL_ITEM" : "DEFAULT");
        set(root, "Animation.Display.Color", source.getString("opening-animation.display.properties.color", "#FFFFFF"));

        set(root, "Animation.Display-Item-Position.X", source.getDouble("extra-settings.animation.display-item-position.x", 0.0));
        set(root, "Animation.Display-Item-Position.Y", source.getDouble("extra-settings.animation.display-item-position.y", 2.0));
        set(root, "Animation.Display-Item-Position.Z", source.getDouble("extra-settings.animation.display-item-position.z", 0.0));

        if (!List.of("DEFAULT", "KEY_OPENER", "CRACK").contains(preOpen)) notMigrated.add("pre-open animation " + preOpen + " (now NONE)");
        if (!List.of("SWIRL", "COMPACT").contains(open)) notMigrated.add("open animation " + open + " (now SWIRL)");
        if (!List.of("DEFAULT", "PHYSICAL_ITEM").contains(display)) notMigrated.add("display animation " + display + " (now DEFAULT)");

        final List<Map<String, Object>> effects = new ArrayList<>();

        for (final String effect : source.getStringList("idle-effects")) {
            final Map<String, Object> parsed = idleEffect(effect);

            if (parsed == null) {
                notMigrated.add("idle effect " + effect);

                continue;
            }

            effects.add(parsed);
        }

        if (!effects.isEmpty()) set(root, "Idle-Effects", effects);
    }

    private @NotNull String phaseId(@NotNull final YamlConfiguration source, @NotNull final String phase) {
        final String path = "opening-animation." + phase;

        return (source.isConfigurationSection(path) ? source.getString(path + ".id", "DEFAULT") : source.getString(path, "DEFAULT")).toUpperCase();
    }

    /**
     * Reads {@code DEFAULT;{REDSTONE;#ff8a1f;0.0;0.0;0.0;1.0;0.1;2}}: the effect, then particle, color, offset x/y/z,
     * velocity, spread and amount. Only the {@code DEFAULT} effect (particles at the middle of the crate) is ported.
     */
    private @Nullable Map<String, Object> idleEffect(@NotNull final String effect) {
        final int open = effect.indexOf(";{");

        if (open < 0 || !effect.endsWith("}") || !effect.substring(0, open).equals("DEFAULT")) return null;

        final String[] parts = effect.substring(open + 2, effect.length() - 1).split(";");

        if (parts.length < 8) return null;

        final String particle = parts[0].equalsIgnoreCase("REDSTONE") ? "dust" : parts[0].toLowerCase();

        final Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("Particle", particle);
        parsed.put("Color", parts[1]);
        parsed.put("Offset", Map.of("X", parseDouble(parts[2]), "Y", parseDouble(parts[3]), "Z", parseDouble(parts[4])));
        parsed.put("Velocity", parseDouble(parts[5]));
        parsed.put("Spread", parseDouble(parts[6]));
        parsed.put("Amount", (int) parseDouble(parts[7]));

        return parsed;
    }

    private double parseDouble(@NotNull final String value) {
        try {
            return Double.parseDouble(value);
        } catch (final NumberFormatException _) {
            return 0.0;
        }
    }

    private void writeHologram(@NotNull final ConfigurationSection root, @NotNull final YamlConfiguration source, @NotNull final String displayName) {
        final List<String> lines = new ArrayList<>();

        source.getStringList("hologram.lines").forEach(line -> lines.add(toMiniMessage(line.replace("%name%", displayName))));

        set(root, "Hologram.Toggle", !lines.isEmpty());
        set(root, "Hologram.Height", 1.5);
        set(root, "Hologram.Range", 8);
        set(root, "Hologram.Update-Interval", -1);
        set(root, "Hologram.Color", "transparent");
        set(root, "Hologram.TextShadow", false);
        set(root, "Hologram.Message", lines);
    }

    /**
     * Writes the physical key from the first key PhoenixCrates links to the crate, keeping its name and lore.
     *
     * @return false when the crate has no key this can migrate
     */
    private boolean writeKey(@NotNull final ConfigurationSection root, @NotNull final Path phoenix, @NotNull final YamlConfiguration source,
                             @NotNull final String displayName, @NotNull final String crateName) {
        final List<String> keys = source.getStringList("linked-keys-ids");

        if (keys.isEmpty()) {
            this.fusion.log(Level.WARNING, "%s links no key in PhoenixCrates.", crateName);

            return false;
        }

        final Path keyFile = phoenix.resolve("keys").resolve(keys.getFirst() + ".yml");

        if (!Files.exists(keyFile)) {
            this.fusion.log(Level.WARNING, "%s links the key %s, which has no file at %s.", crateName, keys.getFirst(), keyFile);

            return false;
        }

        final YamlConfiguration key = YamlConfiguration.loadConfiguration(keyFile.toFile());

        if (key.getBoolean("virtual", false)) {
            this.fusion.log(Level.WARNING, "%s uses the virtual key %s; virtual key balances are not migrated.", crateName, keys.getFirst());
        }

        final String nexoId = nexoIdOf(key, key.getString("item.material", ""));

        if (nexoId != null) {
            set(root, "PhysicalKey.Item", "nexo@" + nexoId);
        } else {
            final ItemStack item = resolveItem(key, key.getString("item.material", "tripwire_hook"));

            set(root, "PhysicalKey.Item", item == null ? "tripwire_hook" : item.getType().getKey().getKey());

            if (item != null) set(root, "PhysicalKey.Data", ItemUtils.toBase64(item));
        }

        final List<String> lore = new ArrayList<>();

        key.getStringList("item.lore").forEach(line -> lore.add(toMiniMessage(line.replace("%crate%", displayName))));

        set(root, "PhysicalKey.Name", toMiniMessage(key.getString("item.display-name", "")));
        set(root, "PhysicalKey.Lore", lore);
        set(root, "PhysicalKey.Glowing", key.getBoolean("glow", false) ? "add_glow" : "none");

        return true;
    }

    private void writePrizes(@NotNull final ConfigurationSection root, @NotNull final YamlConfiguration source, @NotNull final String crateName,
                             @NotNull final List<String> notMigrated) {
        final ConfigurationSection rewards = source.getConfigurationSection("rewards");

        if (rewards == null) return;

        for (final String id : rewards.getKeys(false)) {
            final ConfigurationSection reward = rewards.getConfigurationSection(id);

            if (reward == null || !reward.getBoolean("enabled", true)) continue;

            // Named after the reward's identifier, the id other plugins (announcements, logs) already know it by.
            final String name = reward.getString("identifier", id);

            final ConfigurationSection prize = root.createSection("Prizes." + name);

            if (!writePrize(prize, source, reward)) {
                root.set("Prizes." + name, null);

                notMigrated.add("reward " + reward.getString("identifier", id) + " (an item could not be built)");

                continue;
            }

            listUnsupported(reward, notMigrated);
        }

        this.fusion.log(Level.INFO, "Migrated the rewards of %s.", crateName);
    }

    private boolean writePrize(@NotNull final ConfigurationSection prize, @NotNull final YamlConfiguration source, @NotNull final ConfigurationSection reward) {
        final ConfigurationSection display = reward.getConfigurationSection("display-item");

        if (display == null) return false;

        final ItemStack displayItem = resolveItem(source, display);

        if (displayItem == null) return false;

        final String name = display.contains("display-name")
                ? toMiniMessage(display.getString("display-name", ""))
                : MiniMessage.miniMessage().serialize(displayItem.effectiveName());

        set(prize, "DisplayName", name);
        set(prize, "DisplayItem", displayItem.getType().getKey().getKey());
        set(prize, "DisplayData", ItemUtils.toBase64(displayItem));
        set(prize, "DisplayAmount", displayItem.getAmount());
        set(prize, "Weight", reward.getDouble("weight", 1.0));
        set(prize, "Commands", reward.getStringList("win-commands"));
        set(prize, "BlackListed-Permissions", reward.getStringList("restricted-permissions"));

        if (!writeItems(prize, source, reward.getConfigurationSection("win-items"))) return false;

        final ConfigurationSection alternative = reward.getConfigurationSection("alternative-reward");

        if (alternative != null && alternative.getBoolean("enabled", false)) {
            set(prize, "Alternative-Prize.Toggle", true);
            set(prize, "Alternative-Prize.Messages", List.of());
            set(prize, "Alternative-Prize.Commands", alternative.getStringList("reward.commands"));

            final ConfigurationSection item = alternative.getConfigurationSection("reward.item");

            if (item != null) {
                final ItemStack stack = resolveItem(source, item);

                if (stack == null) return false;

                writeItem(prize, "Alternative-Prize.Items.1", stack);
            }
        }

        return true;
    }

    private boolean writeItems(@NotNull final ConfigurationSection prize, @NotNull final YamlConfiguration source, @Nullable final ConfigurationSection items) {
        if (items == null) return true;

        int index = 1;

        for (final String id : items.getKeys(false)) {
            final ConfigurationSection item = items.getConfigurationSection(id);

            if (item == null) continue;

            final ItemStack stack = resolveItem(source, item);

            if (stack == null) return false;

            writeItem(prize, "Items." + index++, stack);
        }

        return true;
    }

    private void writeItem(@NotNull final ConfigurationSection prize, @NotNull final String path, @NotNull final ItemStack stack) {
        set(prize, path + ".material", stack.getType().getKey().getKey());
        set(prize, path + ".data", ItemUtils.toBase64(stack));
    }

    private void addLocations(@NotNull final String crateName, @NotNull final YamlConfiguration locations) {
        for (final String location : locations.getStringList("locations." + crateName)) {
            final String[] parts = location.split(";");

            if (parts.length < 4) continue;

            final Key world = this.platform.asWorld(parts[0]);

            if (world == null) {
                this.fusion.log(Level.WARNING, "%s has a location in the unknown world %s, skipping it.", crateName, parts[0]);

                continue;
            }

            this.storageHolder.addCrateLocation(crateName, world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), yawOf(parts));
        }
    }

    /** PhoenixCrates stores where a crate faces as a fifth part, one of the 16 block faces ({@code SOUTH_WEST}). */
    private float yawOf(@NotNull final String[] parts) {
        if (parts.length < 5) return 0.0f;

        try {
            final Float yaw = CrateFacing.yawOf(BlockFace.valueOf(parts[4].toUpperCase()));

            return yaw == null ? 0.0f : yaw;
        } catch (final IllegalArgumentException _) {
            return 0.0f;
        }
    }

    /**
     * Builds the item a PhoenixCrates item section describes, with its amount, name, lore and enchantments.
     *
     * @return null when the material cannot be resolved
     */
    private @Nullable ItemStack resolveItem(@NotNull final YamlConfiguration storage, @NotNull final ConfigurationSection section) {
        final ItemStack item = resolveItem(storage, section.getString("material", ""));

        if (item == null) return null;

        item.setAmount(Math.max(1, section.getInt("amount", 1)));

        if (section.contains("display-name")) {
            item.editMeta(meta -> meta.customName(toComponent(section.getString("display-name", ""))));
        }

        if (section.contains("lore")) {
            final List<Component> lore = new ArrayList<>();

            section.getStringList("lore").forEach(line -> lore.add(toComponent(line)));

            item.editMeta(meta -> meta.lore(lore));
        }

        for (final String enchant : section.getStringList("enchants")) {
            final String[] parts = enchant.split(":");
            final Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0].toLowerCase()));

            if (enchantment == null) {
                this.fusion.log(Level.WARNING, "Unknown enchantment %s, skipping it.", parts[0]);

                continue;
            }

            item.addUnsafeEnchantment(enchantment, parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
        }

        return item;
    }

    /**
     * Resolves a PhoenixCrates material: {@code custom:<id>} from the file's internal storage, {@code nexo:<id>}
     * from Nexo, anything else as a vanilla material.
     */
    private @Nullable ItemStack resolveItem(@NotNull final YamlConfiguration storage, @NotNull final String material) {
        if (material.startsWith("custom:")) {
            final String id = material.substring("custom:".length());
            final ItemStack stored = storage.getItemStack("internal-storage.items." + id);

            if (stored == null) {
                this.fusion.log(Level.WARNING, "The item %s is not in the internal storage.", id);
            }

            return stored == null ? null : stored.clone();
        }

        if (material.startsWith("nexo:")) {
            final String id = material.substring("nexo:".length());

            if (!Plugins.nexo.isEnabled()) {
                this.fusion.log(Level.WARNING, "The item %s needs Nexo, which is not enabled.", material);

                return null;
            }

            final ItemBuilder builder = NexoItems.itemFromId(id);

            if (builder == null) {
                this.fusion.log(Level.WARNING, "Nexo has no item %s.", id);

                return null;
            }

            return builder.build();
        }

        final Material type = Material.matchMaterial(material);

        if (type == null || type.isAir()) {
            this.fusion.log(Level.WARNING, "Unknown material %s.", material);

            return null;
        }

        return new ItemStack(type);
    }

    /**
     * The Nexo id of a key's item, read from the stored item when there is one.
     */
    private @Nullable String nexoIdOf(@NotNull final YamlConfiguration key, @NotNull final String material) {
        if (material.startsWith("custom:")) {
            final ItemStack stored = key.getItemStack("internal-storage.items." + material.substring("custom:".length()));

            if (stored != null) {
                final String id = stored.getPersistentDataContainer().get(NEXO_ID, PersistentDataType.STRING);

                if (id != null) return id;
            }

            if (material.startsWith("custom:nexo:")) return material.substring("custom:nexo:".length());

            return null;
        }

        return material.startsWith("nexo:") ? material.substring("nexo:".length()) : null;
    }

    private void listUnsupported(@NotNull final ConfigurationSection section, @NotNull final List<String> notMigrated) {
        if (section.getBoolean("reroll.enabled", false)) notMigrated.add("rerolls");
        if (section.getDouble("open-money-cost", 0.0) > 0) notMigrated.add("the opening cost");
        if (section.getInt("open-cooldown", 0) > 0) notMigrated.add("the opening cooldown");
        if (section.getBoolean("permission.required", false)) notMigrated.add("the opening permission");
        if (section.getInt("max-win-rewards", 0) > 0) notMigrated.add("max-win-rewards");

        final String mode = section.getString("rewards-mode", "RANDOM");

        if (!mode.equals("RANDOM")) notMigrated.add("rewards-mode " + mode);

        final String reward = section.getString("identifier", section.getName());

        if (section.getInt("win-limits", -1) != -1) notMigrated.add(reward + " win limit");
        if (section.getInt("guaranteed-win", -1) != -1) notMigrated.add(reward + " guaranteed win");
        if (section.contains("required-keys") && section.getInt("required-keys", 1) != 1) notMigrated.add(reward + " required keys");
        if (section.getBoolean("share-players-online", false)) notMigrated.add(reward + " sharing with online players");
        if (section.getBoolean("broadcast", false)) notMigrated.add(reward + " broadcast");
    }

    private @NotNull Component toComponent(@NotNull final String text) {
        return MiniMessage.miniMessage().deserialize(toMiniMessage(text)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Turns PhoenixCrates text, which mixes legacy {@code &} codes, {@code &#RRGGBB} and MiniMessage, into MiniMessage.
     * <p>
     * A legacy colour code also clears bold, italic and the other decorations, which a MiniMessage colour tag does not,
     * so a colour that follows a decoration resets first.
     */
    private @NotNull String toMiniMessage(@NotNull final String text) {
        final String hex = LEGACY_HEX.matcher(text).replaceAll("<#$1>");
        final Matcher matcher = LEGACY_CODE.matcher(hex);
        final StringBuilder builder = new StringBuilder();

        boolean decorated = false;

        while (matcher.find()) {
            final char code = Character.toLowerCase(matcher.group(1).charAt(0));
            final String tag = LEGACY_TAGS.get(code);

            final boolean isColour = code <= 'f';
            final String replacement = isColour && decorated ? "<reset><" + tag + ">" : "<" + tag + ">";

            decorated = !isColour && code != 'r';

            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(builder);

        return builder.toString();
    }

    @Override
    public <T> void set(final ConfigurationSection section, final String path, T value) {
        section.set(path, value);
    }
}
