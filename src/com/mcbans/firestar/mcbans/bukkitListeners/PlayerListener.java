package com.mcbans.firestar.mcbans.bukkitListeners;

import static com.mcbans.firestar.mcbans.I18n._;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.mcbans.firestar.mcbans.ActionLog;
import com.mcbans.firestar.mcbans.ConfigurationManager;
import com.mcbans.firestar.mcbans.I18n;
import com.mcbans.firestar.mcbans.MCBans;
import com.mcbans.firestar.mcbans.permission.Perms;
import com.mcbans.firestar.mcbans.request.DisconnectRequest;
import com.mcbans.firestar.mcbans.util.Util;

public class PlayerListener implements Listener {
    private final MCBans plugin;
    private final ActionLog log;
    private final ConfigurationManager config;

    public PlayerListener(final MCBans plugin) {
        this.plugin = plugin;
        this.log = plugin.getLog();
        this.config = plugin.getConfigs();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLoginEvent(final AsyncPlayerPreLoginEvent event) {
        try {
            int check = 1;
            while (plugin.apiServer == null) {
                // waiting for server select
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                check++;
                if (check > 5) {
                    // can't reach mcbans servers
                    if (config.isFailsafe()){
                        log.warning("Can't reach MCBans API Servers! Kicked player: " + event.getName());
                        event.disallow(Result.KICK_BANNED, _("unavailable"));
                    }else{
                        log.warning("Can't reach MCBans API Servers! Check passed player: " + event.getName());
                    }
                    return;
                }
            }

            // get player information
            final String uriStr = "http://" + plugin.apiServer + "/v2/" + config.getApiKey() + "/login/"
                    + URLEncoder.encode(event.getName(), "UTF-8") + "/"
                    + URLEncoder.encode(String.valueOf(event.getAddress().getHostAddress()), "UTF-8") + "/"
                    + plugin.apiRequestSuffix;
            final URLConnection conn = new URL(uriStr).openConnection();

            conn.setConnectTimeout(config.getTimeoutInSec() * 1000);
            conn.setReadTimeout(config.getTimeoutInSec() * 1000);
            conn.setUseCaches(false);

            BufferedReader br = null;
            String response = null;
            try{
                br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                response = br.readLine();
            }finally{
                if (br != null) br.close();
            }
            if (response == null){
                if (config.isFailsafe()){
                    log.warning("Null response! Kicked player: " + event.getName());
                    event.disallow(Result.KICK_BANNED, _("unavailable"));
                }else{
                    log.warning("Null response! Check passed player: " + event.getName());
                }
                return;
            }

            plugin.debug("Response: " + response);
            String[] s = response.split(";");
            if (s.length == 6 || s.length == 7 || s.length == 8) {
                // check banned
                if (s[0].equals("l") || s[0].equals("g") || s[0].equals("t") || s[0].equals("i") || s[0].equals("s")) {
                    event.disallow(Result.KICK_BANNED, s[1]);
                    return;
                }
                // check reputation
                else if (config.getMinRep() > Double.valueOf(s[2])) {
                    event.disallow(Result.KICK_BANNED, _("underMinRep"));
                    return;
                }
                // check alternate accounts
                else if (config.isEnableMaxAlts() && config.getMaxAlts() < Integer.valueOf(s[3])) {
                    event.disallow(Result.KICK_BANNED, _("overMaxAlts"));
                    return;
                }
                // check passed, put data to playerCache
                else{
                    HashMap<String, String> tmp = new HashMap<String, String>();
                    if(s[0].equals("b")){
                        if (s.length == 8){
                            tmp.put("b", s[7]);
                        }else{
                            tmp.put("b", null);
                        }
                    }
                    if(Integer.parseInt(s[3]) > 0){
                        tmp.put("a", s[3]);
                        tmp.put("al", s[6]);
                    }
                    if(s[4].equals("y")){
                        tmp.put("m", "y");
                    }
                    if(Integer.parseInt(s[5]) > 0){
                        tmp.put("d", s[5]);
                    }
                    if (s.length == 8){
                       
                    }
                    plugin.playerCache.put(event.getName(), tmp);
                }
                plugin.debug(event.getName() + " authenticated with " + s[2] + " rep");
            }else{
                if (response.toString().contains("Server Disabled")) {
                    Util.message(Bukkit.getConsoleSender(), ChatColor.RED + "This Server Disabled by MCBans Administration!");
                    return;
                }

                if (config.isFailsafe()){
                    log.warning("Invalid response!(" + s.length + ") Kicked player: " + event.getName());
                    event.disallow(Result.KICK_BANNED, _("unavailable"));
                }else{
                    log.warning("Invalid response!(" + s.length + ") Check passed player: " + event.getName());
                }
                log.warning("Response: " + response);
                return;
            }
        }
        catch (SocketTimeoutException ex){
            log.warning("Cannot connect MCBans API server: timeout");
            if (config.isFailsafe()){
                event.disallow(Result.KICK_BANNED, _("unavailable"));
            }
        }
        catch (IOException ex){
            log.warning("Cannot connect MCBans API server!");
            if (config.isDebug()) ex.printStackTrace();

            if (config.isFailsafe()){
                event.disallow(Result.KICK_BANNED, _("unavailable"));
            }
        }
        catch (Exception ex){
            log.warning("Error occurred in AsyncPlayerPreLoginEvent. Please report this!");
            ex.printStackTrace();

            if (config.isFailsafe()){
                log.warning("Internal exception! Kicked player: " + event.getName());
                event.disallow(Result.KICK_BANNED, _("unavailable"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        final HashMap<String,String> pcache = plugin.playerCache.remove(player.getName());
        if(pcache == null) return;

        if(pcache.containsKey("b")){
            Util.message(player, ChatColor.RED + _("bansOnRecord"));
            
            if (!Perms.HIDE_VIEW.has(player)){
                Perms.VIEW_BANS.message(ChatColor.RED + _("previousBans", I18n.PLAYER, player.getName()));
                
                String prev = pcache.get("b");
                if (config.isSendDetailPrevBans() && prev != null){
                    prev = prev.trim();
                    String[] bans = prev.split(",");
                    for (String ban : bans){
                        String[] data = ban.split("\\$");
                        if (data.length == 3){
                            Perms.VIEW_BANS.message(ChatColor.WHITE+ data[1] + ChatColor.GRAY + " .:. " + ChatColor.WHITE + data[0] + ChatColor.GRAY +  " (by " + data[2] + ")");
                        }
                    }
                }
            }
        }
        if(pcache.containsKey("d")){
            Util.message(player, ChatColor.RED + _("disputes", I18n.COUNT, pcache.get("d")));
        }
        if(pcache.containsKey("a")){
            if (!Perms.HIDE_VIEW.has(player))
                Perms.VIEW_ALTS.message(ChatColor.DARK_PURPLE + _("altAccounts", I18n.PLAYER, player.getName(), I18n.ALTS, pcache.get("al")));
        }
        if(pcache.containsKey("m")){
            //Util.broadcastMessage(ChatColor.AQUA + _("isMCBansMod", I18n.PLAYER, player.getName()));
            // notify to console, mcbans.view.staff, mcbans.admin, mcbans.ban.global players
            Util.message(Bukkit.getConsoleSender(), ChatColor.AQUA + player.getName() + " is a MCBans Staff member");
            
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable(){
                @Override
                public void run() {
                    Set<Player> players = Perms.VIEW_STAFF.getPlayers();
                    players.addAll(Perms.ADMIN.getPlayers());
                    players.addAll(Perms.BAN_GLOBAL.getPlayers());
                    for (final Player p : players){
                        if (p.canSee(player)){ // check joined player cansee
                            Util.message(p, ChatColor.AQUA + _("isMCBansMod", I18n.PLAYER, player.getName()));
                        }
                    }
                }
            }, 1L);
            
            // send information to mcbans staff
            Set<String> admins = new HashSet<String>();
            for (Player p : Perms.ADMIN.getPlayers()){
                admins.add(p.getName());
            }
            Util.message(player, ChatColor.AQUA + "You are a MCBans Staff Member! (ver " + plugin.getDescription().getVersion() + ")");
            Util.message(player, ChatColor.AQUA + "Online Admins: " + ((admins.size() > 0) ? Util.join(admins, ", ") : ChatColor.GRAY + "(none)"));
           
            // add online mcbans staff list array
            plugin.mcbStaff.add(player.getName());
        }

        if (config.isSendJoinMessage()){
            Util.message(player, ChatColor.DARK_GREEN + "Server secured by MCBans!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // send disconnect request
        new Thread(new DisconnectRequest(plugin, event.getPlayer().getName())).start();
        
        if (plugin.mcbStaff.contains(event.getPlayer().getName())){
            plugin.mcbStaff.remove(event.getPlayer().getName());
        }
    }
}