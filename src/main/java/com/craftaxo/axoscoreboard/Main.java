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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener {

    private File statsFile;
    private FileConfiguration statsConfig;
    private static Economy economy = null;
    
    private final Map<UUID, String[]> lastLines = new HashMap<>();

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

    private int getStreak(Player player) {
        return statsConfig.getInt("stats." + player.getUniqueId() + ".streak", 0);
    }

    private void addKill(Player player) {
        int kills = getKills(player);
        int streak = getStreak(player);
        statsConfig.set("stats." + player.getUniqueId() + ".kills", kills + 1);
        statsConfig.set("stats." + player.getUniqueId() + ".streak", streak + 1);
        saveStats();
    }

    private void addDeath(Player player) {
        int deaths = getDeaths(player);
        statsConfig.set("stats." + player.getUniqueId() + ".deaths", deaths + 1);
        statsConfig.set("stats." + player.getUniqueId() + ".streak", 0); // Ölünce seri sıfırlanır
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
        Objective objective = board.registerNewObjective("axoScore", Criteria.DUMMY, ChatColor.RED + "" + ChatColor.BOLD + "AXOCRAFT");
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

            int kills = getKills(player);
            int deaths = getDeaths(player);
            int streak = getStreak(player);

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
                } catch (Exception ignored) {}
            }

            // --- TRAP BİLGİLERİ ---
            String trapName = "Yok";
            String trapHealth = "-";
            String trapNo = "-";

            if (trapPlugin != null && trapPlugin.isEnabled()) {
                try {
                    Object trapInstance = null;

                    String[] playerMethodNames = {"getPlayerTrap", "getPlayersTrap", "getTrapByPlayer", "getPlayersActiveTrap"};
                    for (String mName : playerMethodNames) {
                        try {
                            Method m = trapPlugin.getClass().getMethod(mName, Player.class);
                            trapInstance = m.invoke(trapPlugin, player);
                            if (trapInstance != null) break;
                        } catch (Exception ignored) {}
                    }

                    if (trapInstance == null) {
                        try {
                            Method m = trapPlugin.getClass().getMethod("getTrap", java.util.UUID.class);
                            trapInstance = m.invoke(trapPlugin, player.getUniqueId());
                        } catch (Exception ignored) {}
                    }

                    if (trapInstance == null) {
                        Location loc = player.getLocation();
                        Chunk chunk = loc.getChunk();
                        String[] locMethodNames = {"getTrapByLocation", "getTrapAt", "getTrapByLoc", "getTrapByChunk"};
                        for (String mName : locMethodNames) {
                            try {
                                Method m = trapPlugin.getClass().getMethod(mName, Location.class);
                                trapInstance = m.invoke(trapPlugin, loc);
                                if (trapInstance != null) break;
                            } catch (Exception e1) {
                                try {
                                    Method m = trapPlugin.getClass().getMethod(mName, Chunk.class);
                                    trapInstance = m.invoke(trapPlugin, chunk);
                                    if (trapInstance != null) break;
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    if (trapInstance == null) {
                        String[] listMethodNames = {"getTraps", "getAllTraps", "getRegisteredTraps"};
                        for (String mName : listMethodNames) {
                            try {
                                Method m = trapPlugin.getClass().getMethod(mName);
                                Object result = m.invoke(trapPlugin);
                                if (result instanceof Collection) {
                                    for (Object t : (Collection<?>) result) {
                                        try {
                                            Method ownerM = t.getClass().getMethod("getOwner");
                                            Object ownerObj = ownerM.invoke(t);
                                            if (ownerObj != null && (ownerObj.toString().equalsIgnoreCase(player.getName()) || ownerObj.equals(player.getUniqueId()))) {
                                                trapInstance = t;
                                                break;
                                            }
                                        } catch (Exception ignored) {}

                                        try {
                                            Method membersM = t.getClass().getMethod("getMembers");
                                            Object membersObj = membersM.invoke(t);
                                            if (membersObj instanceof Collection && ((Collection<?>) membersObj).contains(player.getName())) {
                                                trapInstance = t;
                                                break;
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                                if (trapInstance != null) break;
                            } catch (Exception ignored) {}
                        }
                    }

                    if (trapInstance != null) {
                        String[] nameMethods = {"getName", "getTrapName", "getTitle"};
                        for (String nm : nameMethods) {
                            try {
                                Method m = trapInstance.getClass().getMethod(nm);
                                Object val = m.invoke(trapInstance);
                                if (val != null) {
                                    trapName = val.toString();
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }

                        String[] healthMethods = {"getHealth", "getCurrentHealth", "getHp", "getMaxHealth"};
                        for (String hm : healthMethods) {
                            try {
                                Method m = trapInstance.getClass().getMethod(hm);
                                Object val = m.invoke(trapInstance);
                                if (val != null) {
                                    trapHealth = val.toString();
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }

                        String[] idMethods = {"getId", "getTrapId", "getNumber", "getIdentifier"};
                        for (String im : idMethods) {
                            try {
                                Method m = trapInstance.getClass().getMethod(im);
                                Object val = m.invoke(trapInstance);
                                if (val != null) {
                                    trapNo = val.toString();
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            // --- İSTEDİĞİN SIRALAMA ---
            String[] newLines = new String[] {
                ChatColor.DARK_GRAY + "------------------",
                ChatColor.RED + "İsim: " + ChatColor.WHITE + prefix + player.getName(),
                ChatColor.RED + "Rütbe: " + ChatColor.WHITE + rank,
                ChatColor.RED + "Dinar: " + ChatColor.GREEN + moneyStr + " | " + ChatColor.RED + "Seri: " + ChatColor.YELLOW + streak,
                ChatColor.RED + "Ölüm: " + ChatColor.WHITE + deaths + " | " + ChatColor.RED + "Öldürme: " + ChatColor.WHITE + kills,
                ChatColor.DARK_GRAY + "--- Trap Bilgileri ---",
                ChatColor.RED + "Trap Adı: " + ChatColor.WHITE + trapName,
                ChatColor.RED + "Trap Canı: " + ChatColor.GREEN + trapHealth,
                ChatColor.RED + "Trap No: " + ChatColor.WHITE + trapNo,
                ChatColor.YELLOW + "oyna.axocraft.com"
            };

            for (String entry : board.getEntries()) {
                board.resetScores(entry);
            }

            for (int i = 0; i < newLines.length; i++) {
                int scoreVal = newLines.length - i;
                String lineText = newLines[i];
                
                for (String existingEntry : board.getEntries()) {
                    if (existingEntry.equals(lineText)) {
                        lineText += ChatColor.values()[i % ChatColor.values().length] + "" + ChatColor.RESET;
                    }
                }

                objective.getScore(lineText).setScore(scoreVal);
            }

            lastLines.put(player.getUniqueId(), newLines);
        }
    }
}
