package com.mcbans.firestar.mcbans.commands;

import static com.mcbans.firestar.mcbans.I18n._;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.mcbans.firestar.mcbans.BanType;
import com.mcbans.firestar.mcbans.exception.CommandException;
import com.mcbans.firestar.mcbans.request.Ban;
import com.mcbans.firestar.mcbans.util.Util;

public class CommandBan extends BaseCommand{
    public CommandBan(){
        bePlayer = false;
        name = "ban";
        argLength = 1;
        usage = "ban player";
        banning = true;
    }

    @Override
    public void execute() throws CommandException {
        args.remove(0); //remove target

        // check BanType
        BanType type = BanType.LOCAL;
        if (args.size() > 0) {
            if (args.get(0).equalsIgnoreCase("g")){
                type = BanType.GLOBAL;
            }else if (args.get(0).equalsIgnoreCase("t")){
                type = BanType.TEMP;
            }
        }
        if (type != BanType.LOCAL){
            args.remove(0);
        }

        // check permission
        if (!type.getPermission().has(sender)){
            throw new CommandException(ChatColor.RED + _("permissionDenied"));
        }

        String reason = null;
        Ban banControl = null;
        switch (type){
            case LOCAL:
                reason = config.getDefaultLocal();
                if (args.size() > 0){
                    reason = Util.join(args, " ");
                }
                banControl = new Ban(plugin, type.getActionName(), target, targetIP, senderName, reason, "", "");
                break;

            case GLOBAL:
                if (args.size() == 0){
                    Util.message(sender, ChatColor.RED + _("formatError"));
                    return;
                }
                reason = Util.join(args, " ");
                banControl = new Ban(plugin, type.getActionName(), target, targetIP, senderName, reason, "", "");
                break;

            case TEMP:
                if (args.size() < 2){
                    Util.message(sender, ChatColor.RED + _("formatError"));
                    return;
                }
                final String duration = args.remove(0);
                final String measure = args.remove(0);
                reason = config.getDefaultTemp();
                if (args.size() > 0){
                    reason = Util.join(args, " ");
                }
                banControl = new Ban(plugin, type.getActionName(), target, targetIP, senderName, reason, duration, measure);
                break;
        }

        // Start
        if (banControl == null){
            Util.message(sender, ChatColor.RED + "Internal error! Please report console logs!");
            throw new RuntimeException("Undefined BanType: " + type.name());
        }
        Thread triggerThread = new Thread(banControl);
        triggerThread.start();
    }

    @Override
    public boolean permission(CommandSender sender) {
        return (BanType.GLOBAL.getPermission().has(sender) ||
                BanType.LOCAL.getPermission().has(sender)  ||
                BanType.TEMP.getPermission().has(sender));
    }
}
