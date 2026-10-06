package com.craftaxo.axoscoreboard;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

public final class Main extends JavaPlugin implements Listener {

    private File statsFile;
    private FileConfiguration statsConfig;
    private static Economy economy = null;

    @Override
    public void onEnable() {
        createStatsFile();
        getServer().getPluginManager().registerEvents(this, this);

        if (!setupEconomy()) {
            getLogger().info("Vault ekonomisi bulunamadi, para kismi devre disi kalacak!");
        }

        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 0L, 20L);
        getLogger().info("AxoScoreboard tam özellikli olarak aktif edildi!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AxoScoreboard devredisi birakildi.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void createStatsFile() {
        statsFile = new File(getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    private void saveStats() {
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getKills(Player player) {
        return statsConfig.getInt("stats." + player.getUniqueId() + ".kills", 0);
    }

    private int getDeaths(Player player) {
        return statsConfig.getInt("stats." + player.getUniqueId() + ".deaths", 0);
    }

    private void addKill(Player player) {
        int current = getKills(player);
        statsConfig.set("stats." + player.getUniqueId() + ".kills", current + 1);
        saveStats();
    }

    private void addDeath(Player player) {
        int current = getDeaths(player);
        statsConfig.set("stats." + player.getUniqueId() + ".deaths", current + 1);
        saveStats();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        setupScoreboard(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        addDeath(victim);

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            addKill(killer);
        }
    }

    public void setupScoreboard(Player player) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        Scoreboard board = sm.getNewScoreboard();
        Objective objective = board.registerNewObjective("axoScore", Criteria.DUMMY, ChatColor.GOLD + "" + ChatColor.BOLD + "AxoCraft");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
    }

    public void updateScoreboards() {
        Plugin trapPlugin = Bukkit.getPluginManager().getPlugin("TrapPlugin");
        Plugin luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");

        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board == null || board.getObjective("axoScore") == null) {
                setupScoreboard(player);
                board = player.getScoreboard();
            }

            Objective objective = board.getObjective("axoScore");
            if (objective == null) continue;

            for (String entry : board.getEntries()) {
                board.resetScores(entry);
            }

            int kills = getKills(player);
            int deaths = getDeaths(player);

            // Para Miktarı Çekme
            String moneyStr = "0.0";
            if (economy != null) {
                try {
                    double balance = economy.getBalance(player);
                    moneyStr = String.format("%.1f", balance);
                } catch (Exception ignored) {}
            }

            // LuckPerms Tag ve Rütbe Çekme
            String rank = "Oyuncu";
            String prefix = "";
            if (luckPermsPlugin != null && luckPermsPlugin.isEnabled()) {
                try {
                    Object lpProvider = net.luckperms.api.LuckPermsProvider.get();
                    Method getUserManagerMethod = lpProvider.getClass().getMethod("getUserManager");
                    Object userManager = getUserManagerMethod.invoke(lpProvider);
                    Method getUserMethod = userManager.getClass().getMethod("getUser", java.util.UUID.class);
                    Object user = getUserMethod.invoke(userManager, player.getUniqueId());

                    if (user != null) {
                        Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
                        Object primaryGroupObj = getPrimaryGroupMethod.invoke(user);
                        if (primaryGroupObj != null) {
                            String group = primaryGroupObj.toString();
                            rank = group.substring(0, 1).toUpperCase() + group.substring(1);
                        }

                        // Prefix (Tag) Çekme
                        Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
                        Object cachedData = getCachedDataMethod.invoke(user);
                        Method getMetaDataMethod = cachedData.getClass().getMethod("getMetaData");
                        Object metaData = getMetaDataMethod.invoke(cachedData);
                        Method getPrefixMethod = metaData.getClass().getMethod("getPrefix");
                        Object prefixObj = getPrefixMethod.invoke(metaData);
                        if (prefixObj != null) {
                            prefix = ChatColor.translateAlternateColorCodes('&', prefixObj.toString());
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // Trap Bilgileri (Senin kurduğun veya davet edildiğin her yerde çalışır)
            String trapOwner = "Yok";
            String trapId = "-";
            String trapHealth = "-";

            if (trapPlugin != null && trapPlugin.isEnabled()) {
                try {
                    Location loc = player.getLocation();
                    Chunk chunk = loc.getChunk();
                    
                    Method getTrapMethod = trapPlugin.getClass().getMethod("getTrapByChunk", Chunk.class);
                    Object trapInstance = getTrapMethod.invoke(trapPlugin, chunk);

                    if (trapInstance != null) {
                        Method getOwnerMethod = trapInstance.getClass().getMethod("getOwner");
                        Method getIdMethod = trapInstance.getClass().getMethod("getId");
                        Method getHealthMethod = trapInstance.getClass().getMethod("getHealth");

                        Object ownerObj = getOwnerMethod.invoke(trapInstance);
                        Object idObj = getIdMethod.invoke(trapInstance);
                        Object healthObj = getHealthMethod.invoke(trapInstance);

                        if (ownerObj != null) trapOwner = ownerObj.toString();
                        if (idObj != null) trapId = idObj.toString();
                        if (healthObj != null) trapHealth = healthObj.toString();
                    }
                } catch (Exception ignored) {
                }
            }

            // Scoreboard Satırları (Aşağıdan yukarıya doğru)
            Score line11 = objective.getScore(ChatColor.GRAY + "------------------");
            line11.setScore(11);

            Score line10 = objective.getScore(ChatColor.YELLOW + "Oyuncu: " + ChatColor.WHITE + prefix + player.getName());
            line10.setScore(10);

            Score line9 = objective.getScore(ChatColor.LIGHT_PURPLE + "Rütbe: " + ChatColor.YELLOW + rank);
            line9.setScore(9);

            Score line8 = objective.getScore(ChatColor.GOLD + "Para: " + ChatColor.GREEN + moneyStr + " TL");
            line8.setScore(8);

            Score line7 = objective.getScore(ChatColor.RED + "Öldürme: " + ChatColor.GREEN + kills);
            line7.setScore(7);

            Score line6 = objective.getScore(ChatColor.RED + "Ölüm: " + ChatColor.DARK_RED + deaths);
            line6.setScore(6);

            Score line5 = objective.getScore(ChatColor.GRAY + "==================");
            line5.setScore(5);

            Score line4 = objective.getScore(ChatColor.AQUA + "Trap Sahibi: " + ChatColor.WHITE + trapOwner);
            line4.setScore(4);

            Score line3 = objective.getScore(ChatColor.AQUA + "Trap ID: " + ChatColor.WHITE + trapId);
            line3.setScore(3);

            Score line2 = objective.getScore(ChatColor.GREEN + "Trap Canı: " + ChatColor.WHITE + trapHealth);
            line2.setScore(2);

            Score line1 = objective.getScore(ChatColor.YELLOW + "play.axocraft.com");
            line1.setScore(1);
        }
    }
}
