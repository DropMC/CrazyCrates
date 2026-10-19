package com.badbones69.crazycrates.paper.commands.crates.types.admin.crates.migrator.types.plugins;

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

    private static final String DEFAULT_CRATE_TYPE = "CSGO";

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

            final ConfigurationSection prize = root.createSection("Prizes." + id);

            if (!writePrize(prize, source, reward)) {
                root.set("Prizes." + id, null);

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

            this.storageHolder.addCrateLocation(crateName, world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
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
        final String engine = section.getString("engine", "VANILLA_BLOCK");

        if (!engine.equals("VANILLA_BLOCK")) notMigrated.add("engine " + engine);

        if (section.contains("opening-animation")) notMigrated.add("its opening animation (now " + DEFAULT_CRATE_TYPE + ")");
        if (!section.getStringList("idle-effects").isEmpty()) notMigrated.add("idle effects");
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
