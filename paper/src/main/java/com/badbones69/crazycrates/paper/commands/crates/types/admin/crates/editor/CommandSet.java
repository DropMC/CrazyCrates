package com.badbones69.crazycrates.paper.commands.crates.types.admin.crates.editor;

import us.crazycrew.crazycrates.api.enums.messages.Message;
import com.badbones69.crazycrates.paper.commands.crates.types.BaseCommand;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.ArgName;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Suggestion;
import dev.triumphteam.cmd.core.annotations.Syntax;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

public class CommandSet extends BaseCommand {

    @Command("set")
    @Permission(value = "crazycrates.set", def = PermissionDefault.OP)
    @Syntax("/crazycrates set <crate_name>")
    public void set(final Player player, @ArgName("crate") @Suggestion("crates") final String crateName) {
        final Block block = player.getTargetBlock(null, 5);

        if (block.isEmpty()) {
            Message.must_be_looking_at_block.sendMessage(player);

            return;
        }

        this.crateManager.addCrateByLocation(player, block.getLocation(), this.crateManager.getCrateFromName(crateName));
    }

    @Command("delete")
    @Permission(value = "crazycrates.delete", def = PermissionDefault.OP)
    @Syntax("/crazycrates delete")
    public void delete(final Player player) {
        final Block block = player.getTargetBlock(null, 5);

        if (block.isEmpty()) {
            Message.must_be_looking_at_block.sendMessage(player);

            return;
        }

        this.crateManager.removeCrateByLocation(player, block.getLocation());
    }

    @Command("givecrate")
    @Permission(value = "crazycrates.givecrate", def = PermissionDefault.OP)
    @Syntax("/crazycrates givecrate <crate_name> <amount>")
    public void giveCrate(final Player player, @ArgName("crate") @Suggestion("crates") final String crateName, @ArgName("amount") @Suggestion("numbers") final int amount) {
        final Crate crate = this.crateManager.getCrateFromName(crateName);
        final ItemStack item = crate == null ? null : crate.getPlaceable().getItem(Math.max(1, amount));

        if (item == null) { // no crate, or a crate without a Placeable section.
            Message.not_a_crate.sendMessage(player, "{crate}", crateName);

            return;
        }

        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }
}
