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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener {

    private File statsFile;
    private FileConfiguration statsConfig;
    private static Economy economy = null;
    
    private final Map<UUID, String[]> lastLines = new HashMap<>();
    private int animationFrame = 0;

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
        Objective objective = board.registerNewObjective("axoScore", Criteria.DUMMY, "§x§5§B§F§5§D§0a§x§5§3§E§1§D§3x§x§4§B§C§E§D§6o §x§3§C§A§7§D§Bc§x§3§4§9§3§D§Eʀ§x§2§C§7§F§E§1a§x§2§4§6§C§E§3ꜰ§x§1§C§5§8§E§6ᴛ");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
    }

    public void updateScoreboards() {
        animationFrame++;
        Plugin trapPlugin = Bukkit.getPluginManager().getPlugin("TrapPlugin");
        Plugin luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");

        String[] titles = {
            "§x§5§B§F§5§D§0A§x§4§B§C§E§D§6X§x§3§C§A§7§D§BO §x§2§C§7§F§E§1C§x§1§C§5§8§E§6R§x§2§4§6§C§E§3A§x§3§4§9§3§D§EF§x§5§3§E§1§D§3T",
            "§x§3§C§A§7§D§B◇ §x§5§B§F§5§D§0AXO §x§2§C§7§F§E§1CRAFT §x§3§C§A§7§D§B◇",
            "§x§1§C§5§8§E§6◆ §x§2§4§6§C§E§3AXO CRAFT §x§1§C§5§8§E§6◆"
        };
        String currentTitle = titles[animationFrame % titles.length];

        ZonedDateTime trTime = ZonedDateTime.now(ZoneId.of("Europe/Istanbul"));
        String dateStr = trTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board == null || board.getObjective("axoScore") == null) {
                setupScoreboard(player);
                board = player.getScoreboard();
            }

            Objective objective = board.getObjective("axoScore");
            if (objective == null) continue;

            objective.setDisplayName(currentTitle);

            int kills = getKills(player);
            int deaths = getDeaths(player);

            String moneyStr = "0";
            if (economy != null) {
                try {
                    double balance = economy.getBalance(player);
                    moneyStr = String.format("%.0f", balance);
                } catch (Exception ignored) {}
            }

            String coloredNameAndTag = ChatColor.WHITE + player.getName();
            if (luckPermsPlugin != null && luckPermsPlugin.isEnabled()) {
                try {
                    Object lpProvider = net.luckperms.api.LuckPermsProvider.get();
                    Method getUserManagerMethod = lpProvider.getClass().getMethod("getUserManager");
                    Object userManager = getUserManagerMethod.invoke(lpProvider);
                    Method getUserMethod = userManager.getClass().getMethod("getUser", java.util.UUID.class);
                    Object user = getUserMethod.invoke(userManager, player.getUniqueId());

                    if (user != null) {
                        Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
                        Object cachedData = getCachedDataMethod.invoke(user);
                        Method getMetaDataMethod = cachedData.getClass().getMethod("getMetaData");
                        Object metaData = getMetaDataMethod.invoke(cachedData);
                        Method getPrefixMethod = metaData.getClass().getMethod("getPrefix");
                        Object prefixObj = getPrefixMethod.invoke(metaData);

                        String prefix = (prefixObj != null) ? ChatColor.translateAlternateColorCodes('&', prefixObj.toString()) : "";
                        coloredNameAndTag = prefix + " " + player.getName();
                    }
                } catch (Exception ignored) {}
            } else {
                coloredNameAndTag = ChatColor.GREEN + player.getName();
            }

            String trapOwner = "yok";
            String trapHealth = "-";
            String trapId = "-";

            if (trapPlugin != null && trapPlugin.isEnabled()) {
                try {
                    Object trapInstance = null;

                    String[] listMethodNames = {"getTraps", "getAllTraps", "getRegisteredTrabs", "getPluginTraps"};
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

                    if (trapInstance == null) {
                        Location loc = player.getLocation();
                        Chunk chunk = loc.getChunk();
                        String[] locMethodNames = {"getTrapByLocation", "getTrapAt", "getTrapByChunk"};
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

                    if (trapInstance != null) {
                        String[] ownerMethods = {"getOwnerName", "getOwner", "getOwnerString"};
                        for (String om : ownerMethods) {
                            try {
                                Method m = trapInstance.getClass().getMethod(om);
                                Object val = m.invoke(trapInstance);
                                if (val != null) {
                                    trapOwner = val.toString();
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
                                    trapId = val.toString();
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            // İstediğin özel renkli etiketler ve harf geçişleri
            String[] newLines = new String[] {
                ChatColor.GRAY + dateStr,
                ChatColor.WHITE + "",
                "&#FFFFFFꜱᴇɴ: " + coloredNameAndTag,
                "&#F8D913ᴘ&#F0BD18ᴀ&#E9A01Dʀ&#E18422ᴀ: " + ChatColor.GREEN + moneyStr,
                "&#F56B5BÖ&#F35E51ʟ&#F05146ᴅ&#EE443Cü&#EB3631ʀ&#E92927ᴍ&#E61C1Cᴇ " + ChatColor.GREEN + kills,
                "&#F56B5BÖ&#F05146ʟ&#EB3631ᴍ&#E61C1Cᴇ: " + ChatColor.RED + deaths,
                ChatColor.WHITE + " ",
                ChatColor.WHITE + ":&#FFFFFFᴛʀᴀᴘ ᴄᴀɴı " + ChatColor.BLUE + TrapPluginTrapHealt,
                ChatColor.WHITE + "&#FFFFFFᴛʀᴀᴘ ıᴅ: " + ChatColor.BLUE + TrapPluginTrapId,
                ChatColor.WHITE + "&#FFFFFFᴛʀᴀᴘ ꜱᴀʜɪʙɪ: " + ChatColor.BLUE + TrapPluginTrapOwner,
                ChatColor.WHITE + "  ",
                ChatColor.WHITE + "&#FFFFFFꜱᴜɴᴜᴄᴜ: " + ChatColor.GREEN + "trappvp",
                ChatColor.WHITE + "&#FFFFFFÇᴇᴠʀɪᴍɪçɪ: " + ChatColor.BLUE + onlinePlayers,
                ChatColor.WHITE + "   ",
                ChatColor.YELLOW + "&#1C4CE1ᴀ&#2652E0x&#2F59DFᴏ&#395FDEᴄ&#4365DCʀ&#4C6BDBᴀ&#5672DAꜰ&#6078D9ᴛ&#697ED8.&#7384D7ᴍ&#7D8BD6ᴄ&#8691D5ꜱ&#9097D3ʜ&#9A9DD2.&#A3A4D1ɪ&#ADAAD0ᴏ"
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
