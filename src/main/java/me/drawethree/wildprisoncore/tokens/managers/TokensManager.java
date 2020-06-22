package me.drawethree.wildprisoncore.tokens.managers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.drawethree.wildprisoncore.database.MySQLDatabase;
import me.drawethree.wildprisoncore.tokens.WildPrisonTokens;
import me.lucko.helper.Events;
import me.lucko.helper.Schedulers;
import me.lucko.helper.item.ItemStackBuilder;
import me.lucko.helper.scheduler.Task;
import me.lucko.helper.text.Text;
import me.lucko.helper.time.Time;
import me.lucko.helper.utils.Players;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TokensManager {


    private WildPrisonTokens plugin;
    private String SPACER_LINE;
    private String TOP_FORMAT_BLOCKS;
    private String TOP_FORMAT_TOKENS;
    private HashMap<UUID, Long> tokensCache = new HashMap<>();
    private HashMap<UUID, Long> blocksCache = new HashMap<>();
    private HashMap<UUID, Long> blocksCacheWeekly = new HashMap<>();
    private LinkedHashMap<UUID, Long> top10Tokens = new LinkedHashMap<>();
    private LinkedHashMap<UUID, Long> top10Blocks = new LinkedHashMap<>();
    private LinkedHashMap<UUID, Long> top10BlocksWeekly = new LinkedHashMap<>();
    private LinkedHashMap<Long, BlockReward> blockRewards = new LinkedHashMap<>();
    private Task task;
    private boolean updating;
    private long nextResetWeekly;

    public TokensManager(WildPrisonTokens plugin) {
        this.plugin = plugin;
        this.SPACER_LINE = plugin.getMessage("top_spacer_line");
        this.TOP_FORMAT_BLOCKS = plugin.getMessage("top_format_blocks");
        this.TOP_FORMAT_TOKENS = plugin.getMessage("top_format_tokens");
        this.nextResetWeekly = plugin.getConfig().get().getLong("next-reset-weekly");

        Events.subscribe(PlayerJoinEvent.class)
                .handler(e -> {
                    this.addIntoTable(e.getPlayer());
                    this.loadPlayerData(e.getPlayer());
                }).bindWith(plugin.getCore());
        Events.subscribe(PlayerQuitEvent.class)
                .handler(e -> {
                    this.savePlayerData(e.getPlayer(), true, true);
                    e.getPlayer().getActivePotionEffects().forEach(effect -> e.getPlayer().removePotionEffect(effect.getType()));
                }).bindWith(plugin.getCore());

        this.loadPlayerDataOnEnable();
        this.loadBlockRewards();
        this.updateTop10();
    }

    private void loadBlockRewards() {
        this.blockRewards = new LinkedHashMap<>();
        for (String key : this.plugin.getBlockRewardsConfig().get().getConfigurationSection("block-rewards").getKeys(false)) {
            long blocksNeeded = Long.valueOf(key);
            String message = Text.colorize(this.plugin.getBlockRewardsConfig().get().getString("block-rewards." + key + ".message"));
            List<String> commands = this.plugin.getBlockRewardsConfig().get().getStringList("block-rewards." + key + ".commands");
            this.blockRewards.put(blocksNeeded, new BlockReward(blocksNeeded, commands, message));
        }
        this.plugin.getCore().getLogger().info("Loaded " + this.blockRewards.keySet().size() + " Block Rewards!");
    }

    public void stopUpdating() {
        this.plugin.getCore().getLogger().info("Stopping updating Top 10");
        task.close();
    }

    private void updateTop10() {
        this.updating = true;
        task = Schedulers.async().runRepeating(() -> {
            this.updating = true;
            Players.all().forEach(p -> savePlayerData(p, false, false));
            this.updateBlocksTop();
            this.updateBlocksTopWeekly();
            this.updateTokensTop();
            this.updating = false;
        }, 1, TimeUnit.MINUTES, 1, TimeUnit.HOURS);
    }

    private void savePlayerData(Player player, boolean removeFromCache, boolean async) {
        if (async) {
            Schedulers.async().run(() -> {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.TOKENS_DB_NAME + " SET " + MySQLDatabase.TOKENS_TOKENS_COLNAME + "=? WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?", tokensCache.get(player.getUniqueId()), player.getUniqueId().toString());
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", blocksCache.get(player.getUniqueId()), player.getUniqueId().toString());
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", blocksCacheWeekly.get(player.getUniqueId()), player.getUniqueId().toString());
                if (removeFromCache) {
                    tokensCache.remove(player.getUniqueId());
                    blocksCache.remove(player.getUniqueId());
                    blocksCacheWeekly.remove(player.getUniqueId());
                }
                this.plugin.getCore().getLogger().info(String.format("Saved data of player %s to database.", player.getName()));
            });
        } else {
            this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.TOKENS_DB_NAME + " SET " + MySQLDatabase.TOKENS_TOKENS_COLNAME + "=? WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?", tokensCache.get(player.getUniqueId()), player.getUniqueId().toString());
            this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", blocksCache.get(player.getUniqueId()), player.getUniqueId().toString());
            this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", blocksCacheWeekly.get(player.getUniqueId()), player.getUniqueId().toString());
            if (removeFromCache) {
                tokensCache.remove(player.getUniqueId());
                blocksCache.remove(player.getUniqueId());
                blocksCacheWeekly.remove(player.getUniqueId());
            }
            this.plugin.getCore().getLogger().info(String.format("Saved data of player %s to database.", player.getName()));
        }
    }

    public void savePlayerDataOnDisable() {
        this.plugin.getCore().getLogger().info("[PLUGIN DISABLE] Saving all player data");
        Schedulers.sync().run(() -> {
            for (UUID uuid : blocksCache.keySet()) {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", blocksCache.get(uuid), uuid.toString());
            }
            for (UUID uuid : tokensCache.keySet()) {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.TOKENS_DB_NAME + " SET " + MySQLDatabase.TOKENS_TOKENS_COLNAME + "=? WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?", tokensCache.get(uuid), uuid.toString());
            }
            for (UUID uuid : blocksCache.keySet()) {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", blocksCacheWeekly.get(uuid), uuid.toString());
            }
            tokensCache.clear();
            blocksCache.clear();
            blocksCacheWeekly.clear();
            this.plugin.getCore().getLogger().info("[PLUGIN DISABLE] Saved all player data to database");
        });
    }

    private void addIntoTable(Player player) {
        Schedulers.async().run(() -> {
            this.plugin.getCore().getSqlDatabase().execute("INSERT IGNORE INTO " + MySQLDatabase.TOKENS_DB_NAME + " VALUES(?,?)", player.getUniqueId().toString(), 0);
            this.plugin.getCore().getSqlDatabase().execute("INSERT IGNORE INTO " + MySQLDatabase.BLOCKS_DB_NAME + " VALUES(?,?)", player.getUniqueId().toString(), 0);
            this.plugin.getCore().getSqlDatabase().execute("INSERT IGNORE INTO " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " VALUES(?,?)", player.getUniqueId().toString(), 0);
        });
    }

    private void loadPlayerDataOnEnable() {
        Players.all().forEach(p -> loadPlayerData(p));
    }

    private void loadPlayerData(Player player) {
        Schedulers.async().run(() -> {
            try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); PreparedStatement statement = con.prepareStatement("SELECT * FROM " + MySQLDatabase.TOKENS_DB_NAME + " WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?")) {
                statement.setString(1, player.getUniqueId().toString());
                try (ResultSet tokens = statement.executeQuery()) {
                    if (tokens.next()) {
                        tokensCache.put(UUID.fromString(tokens.getString(MySQLDatabase.TOKENS_UUID_COLNAME)), tokens.getLong(MySQLDatabase.TOKENS_TOKENS_COLNAME));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); PreparedStatement statement = con.prepareStatement("SELECT * FROM " + MySQLDatabase.BLOCKS_DB_NAME + " WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?")) {
                statement.setString(1, player.getUniqueId().toString());
                try (ResultSet blocks = statement.executeQuery()) {
                    if (blocks.next()) {
                        blocksCache.put(UUID.fromString(blocks.getString(MySQLDatabase.BLOCKS_UUID_COLNAME)), blocks.getLong(MySQLDatabase.BLOCKS_BLOCKS_COLNAME));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); PreparedStatement statement = con.prepareStatement("SELECT * FROM " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?")) {
                statement.setString(1, player.getUniqueId().toString());
                try (ResultSet blocks = statement.executeQuery()) {
                    if (blocks.next()) {
                        blocksCacheWeekly.put(UUID.fromString(blocks.getString(MySQLDatabase.BLOCKS_UUID_COLNAME)), blocks.getLong(MySQLDatabase.BLOCKS_BLOCKS_COLNAME));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            this.plugin.getCore().getLogger().info(String.format("Loaded data of player %s from database", player.getName()));
        });
    }

    public void setTokens(OfflinePlayer p, long newAmount, CommandSender executor) {
        Schedulers.async().run(() -> {
            if (!p.isOnline()) {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.TOKENS_DB_NAME + " SET " + MySQLDatabase.TOKENS_TOKENS_COLNAME + "=? WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?", newAmount, p.getUniqueId().toString());
            } else {
                tokensCache.put(p.getUniqueId(), newAmount);
            }
            executor.sendMessage(plugin.getMessage("admin_set_tokens").replace("%player%", p.getName()).replace("%tokens%", String.format("%,d", newAmount)));
        });
    }

    public void giveTokens(OfflinePlayer p, long amount, CommandSender executor) {
        Schedulers.async().run(() -> {
            long currentTokens = getPlayerTokens(p);
            if (!p.isOnline()) {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.TOKENS_DB_NAME + " SET " + MySQLDatabase.TOKENS_TOKENS_COLNAME + "=? WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?", amount + currentTokens, p.getUniqueId().toString());
            } else {
                tokensCache.put(p.getUniqueId(), tokensCache.getOrDefault(p.getUniqueId(), (long) 0) + amount);
            }
            if (executor != null) {
                executor.sendMessage(plugin.getMessage("admin_give_tokens").replace("%player%", p.getName()).replace("%tokens%", String.format("%,d", amount)));
            }
        });
    }

    public void redeemTokens(Player p, ItemStack item, boolean shiftClick) {
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        displayName = displayName.replace(" TOKENS", "").replace(",", "");
        try {
            int tokenAmount = Integer.parseInt(displayName);
            int itemAmount = item.getAmount();
            if (shiftClick) {
                p.setItemInHand(null);
                this.giveTokens(p, tokenAmount * itemAmount, null);
                p.sendMessage(plugin.getMessage("tokens_redeem").replace("%tokens%", String.format("%,d", tokenAmount * itemAmount)));
            } else {
                this.giveTokens(p, tokenAmount, null);
                if (item.getAmount() == 1) {
                    p.setItemInHand(null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }
                p.sendMessage(plugin.getMessage("tokens_redeem").replace("%tokens%", String.format("%,d", tokenAmount)));
            }
        } catch (Exception e) {
            //Not a token item
            p.sendMessage(plugin.getMessage("not_token_item"));
        }
    }

    public void payTokens(Player executor, long amount, OfflinePlayer target) {
        Schedulers.async().run(() -> {
            if (getPlayerTokens(executor) >= amount) {
                this.removeTokens(executor, amount, null);
                this.giveTokens(target, amount, null);
                executor.sendMessage(plugin.getMessage("tokens_send").replace("%player%", target.getName()).replace("%tokens%", String.format("%,d", amount)));
                if (target.isOnline()) {
                    ((Player) target).sendMessage(plugin.getMessage("tokens_received").replace("%player%", executor.getName()).replace("%tokens%", String.format("%,d", amount)));
                }
            } else {
                executor.sendMessage(plugin.getMessage("not_enough_tokens"));
            }
        });
    }

    public void withdrawTokens(Player executor, long amount, int value) {
        Schedulers.async().run(() -> {
            long totalAmount = amount * value;

            if (this.getPlayerTokens(executor) < totalAmount) {
                executor.sendMessage(plugin.getMessage("not_enough_tokens"));
                return;
            }

            removeTokens(executor, totalAmount, null);

            ItemStack item = createTokenItem(amount, value);
            Collection<ItemStack> notFit = executor.getInventory().addItem(item).values();

            if (!notFit.isEmpty()) {
                notFit.forEach(itemStack -> {
                    this.giveTokens(executor, amount * item.getAmount(), null);
                });
            }

            executor.sendMessage(plugin.getMessage("withdraw_successful").replace("%amount%", String.format("%,d,", amount)).replace("%value%", String.format("%,d", value)));
        });
    }

    public long getPlayerTokens(OfflinePlayer p) {
        if (!p.isOnline()) {
            try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); PreparedStatement statement = con.prepareStatement("SELECT * FROM " + MySQLDatabase.TOKENS_DB_NAME + " WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?")) {
                statement.setString(1, p.getUniqueId().toString());
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        return set.getLong(MySQLDatabase.TOKENS_TOKENS_COLNAME);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            return tokensCache.getOrDefault(p.getUniqueId(), (long) 0);
        }
        return 0;
    }

    public long getPlayerBrokenBlocks(OfflinePlayer p) {
        if (!p.isOnline()) {
            try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); PreparedStatement statement = con.prepareStatement("SELECT * FROM " + MySQLDatabase.BLOCKS_DB_NAME + " WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?")) {
                statement.setString(1, p.getUniqueId().toString());
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        return set.getLong(MySQLDatabase.BLOCKS_BLOCKS_COLNAME);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            return blocksCache.getOrDefault(p.getUniqueId(), (long) 0);
        }
        return 0;
    }

    public long getPlayerBrokenBlocksWeekly(OfflinePlayer p) {
        if (!p.isOnline()) {
            try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); PreparedStatement statement = con.prepareStatement("SELECT * FROM " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?")) {
                statement.setString(1, p.getUniqueId().toString());
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        return set.getLong(MySQLDatabase.BLOCKS_BLOCKS_COLNAME);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            return blocksCacheWeekly.getOrDefault(p.getUniqueId(), (long) 0);
        }
        return 0;
    }

    public void removeTokens(OfflinePlayer p, long amount, CommandSender executor) {
        Schedulers.async().run(() -> {
            long currentTokens = getPlayerTokens(p);
            long finalTokens = currentTokens - amount;

            if (finalTokens < 0) {
                finalTokens = 0;
            }

            if (!p.isOnline()) {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.TOKENS_DB_NAME + " SET " + MySQLDatabase.TOKENS_TOKENS_COLNAME + "=? WHERE " + MySQLDatabase.TOKENS_UUID_COLNAME + "=?", finalTokens, p.getUniqueId().toString());
            } else {
                tokensCache.put(p.getUniqueId(), finalTokens);
            }
            if (executor != null) {
                executor.sendMessage(plugin.getMessage("admin_remove_tokens").replace("%player%", p.getName()).replace("%tokens%", String.format("%,d", amount)));
            }
        });
    }

    public static ItemStack createTokenItem(long amount, int value) {
        return ItemStackBuilder.of(Material.DOUBLE_PLANT).amount(value).name("&e&l" + String.format("%,d", amount) + " TOKENS").lore("&7Right-Click to Redeem").enchant(Enchantment.PROTECTION_ENVIRONMENTAL).flag(ItemFlag.HIDE_ENCHANTS).build();
    }

    public void sendInfoMessage(CommandSender sender, OfflinePlayer target, boolean tokens) {
        Schedulers.async().run(() -> {
            if (sender == target) {
                if (tokens) {
                    sender.sendMessage(plugin.getMessage("your_tokens").replace("%tokens%", String.format("%,d", this.getPlayerTokens(target))));
                } else {
                    sender.sendMessage(plugin.getMessage("your_blocks").replace("%blocks%", String.format("%,d", this.getPlayerBrokenBlocks(target))));
                }
            } else {
                if (tokens) {
                    sender.sendMessage(plugin.getMessage("other_tokens").replace("%tokens%", String.format("%,d", this.getPlayerTokens(target))).replace("%player%", target.getName()));
                } else {
                    sender.sendMessage(plugin.getMessage("other_blocks").replace("%blocks%", String.format("%,d", this.getPlayerBrokenBlocks(target))).replace("%player%", target.getName()));
                }
            }
        });
    }

    public void addBlocksBroken(Player player, int amount) {
        Schedulers.async().run(() -> {

            long currentBroken = getPlayerBrokenBlocks(player);
            long currentBrokenWeekly = getPlayerBrokenBlocksWeekly(player);

            BlockReward nextReward = getNextBlockReward(player);

            if (!player.isOnline()) {
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", currentBroken + amount, player.getUniqueId().toString());
                this.plugin.getCore().getSqlDatabase().execute("UPDATE " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " SET " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + "=? WHERE " + MySQLDatabase.BLOCKS_UUID_COLNAME + "=?", currentBrokenWeekly + amount, player.getUniqueId().toString());
            } else {
                blocksCache.put(player.getUniqueId(), currentBroken + amount);
                blocksCacheWeekly.put(player.getUniqueId(), currentBrokenWeekly + amount);

                if (nextReward != null && nextReward.getBlocksRequired() <= currentBroken + amount) {
                    nextReward.giveTo(player);
                }
            }
        });
    }

    private void updateTokensTop() {
        top10Tokens = new LinkedHashMap<>();
        this.plugin.getCore().getLogger().info("Starting updating TokensTop");
        try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); ResultSet set = con.prepareStatement("SELECT " + MySQLDatabase.TOKENS_UUID_COLNAME + "," + MySQLDatabase.TOKENS_TOKENS_COLNAME + " FROM " + MySQLDatabase.TOKENS_DB_NAME + " ORDER BY " + MySQLDatabase.TOKENS_TOKENS_COLNAME + " DESC LIMIT 10").executeQuery()) {
            while (set.next()) {
                top10Tokens.put(UUID.fromString(set.getString(MySQLDatabase.TOKENS_UUID_COLNAME)), set.getLong(MySQLDatabase.TOKENS_TOKENS_COLNAME));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        this.plugin.getCore().getLogger().info("TokensTop updated!");
    }

    private void updateBlocksTop() {
        top10Blocks = new LinkedHashMap<>();
        this.plugin.getCore().getLogger().info("Starting updating BlocksTop");
        try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); ResultSet set = con.prepareStatement("SELECT " + MySQLDatabase.BLOCKS_UUID_COLNAME + "," + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + " FROM " + MySQLDatabase.BLOCKS_DB_NAME + " ORDER BY " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + " DESC LIMIT 10").executeQuery()) {
            while (set.next()) {
                top10Blocks.put(UUID.fromString(set.getString(MySQLDatabase.BLOCKS_UUID_COLNAME)), set.getLong(MySQLDatabase.BLOCKS_BLOCKS_COLNAME));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        this.plugin.getCore().getLogger().info("BlocksTop updated!");
    }

    private void updateBlocksTopWeekly() {
        top10BlocksWeekly = new LinkedHashMap<>();
        this.plugin.getCore().getLogger().info("Starting updating BlocksTop - Weekly");
        try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); ResultSet set = con.prepareStatement("SELECT " + MySQLDatabase.BLOCKS_UUID_COLNAME + "," + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + " FROM " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME + " ORDER BY " + MySQLDatabase.BLOCKS_BLOCKS_COLNAME + " DESC LIMIT 10").executeQuery()) {
            while (set.next()) {
                top10BlocksWeekly.put(UUID.fromString(set.getString(MySQLDatabase.BLOCKS_UUID_COLNAME)), set.getLong(MySQLDatabase.BLOCKS_BLOCKS_COLNAME));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        this.plugin.getCore().getLogger().info("BlocksTop updated!");
    }

    public void sendTokensTop(CommandSender sender) {
        Schedulers.async().run(() -> {
            sender.sendMessage(Text.colorize(SPACER_LINE));
            if (this.updating) {
                sender.sendMessage(this.plugin.getMessage("top_updating"));
                sender.sendMessage(Text.colorize(SPACER_LINE));
                return;
            }
            for (int i = 0; i < 10; i++) {
                try {
                    UUID uuid = (UUID) top10Tokens.keySet().toArray()[i];
                    OfflinePlayer player = Players.getOfflineNullable(uuid);
                    String name;
                    if (player.getName() == null) {
                        name = "Unknown Player";
                    } else {
                        name = player.getName();
                    }
                    long tokens = top10Tokens.get(uuid);
                    sender.sendMessage(TOP_FORMAT_TOKENS.replace("%position%", String.valueOf(i + 1)).replace("%player%", name).replace("%amount%", String.format("%,d", tokens)));
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            }
            sender.sendMessage(Text.colorize(SPACER_LINE));
        });
    }

    public void sendBlocksTop(CommandSender sender) {
        Schedulers.async().run(() -> {
            sender.sendMessage(Text.colorize(SPACER_LINE));
            if (this.updating) {
                sender.sendMessage(this.plugin.getMessage("top_updating"));
                sender.sendMessage(Text.colorize(SPACER_LINE));
                return;
            }
            for (int i = 0; i < 10; i++) {
                try {
                    UUID uuid = (UUID) top10Blocks.keySet().toArray()[i];
                    OfflinePlayer player = Players.getOfflineNullable(uuid);
                    String name;
                    if (player.getName() == null) {
                        name = "Unknown Player";
                    } else {
                        name = player.getName();
                    }
                    long blocks = top10Blocks.get(uuid);
                    sender.sendMessage(TOP_FORMAT_BLOCKS.replace("%position%", String.valueOf(i + 1)).replace("%player%", name).replace("%amount%", String.format("%,d", blocks)));
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            }
            sender.sendMessage(Text.colorize(SPACER_LINE));
        });
    }

    public void sendBlocksTopWeekly(CommandSender sender) {
        Schedulers.async().run(() -> {
            sender.sendMessage(Text.colorize(SPACER_LINE));
            sender.sendMessage(plugin.getMessage("top_weekly_reset").replace("%time%", this.getTimeLeftUntilWeeklyReset()));
            sender.sendMessage(Text.colorize(SPACER_LINE));

            if (this.updating || top10BlocksWeekly.isEmpty()) {
                sender.sendMessage(this.plugin.getMessage("top_updating"));
                sender.sendMessage(Text.colorize(SPACER_LINE));
                return;
            }

            for (int i = 0; i < 10; i++) {
                try {
                    UUID uuid = (UUID) top10BlocksWeekly.keySet().toArray()[i];
                    OfflinePlayer player = Players.getOfflineNullable(uuid);
                    String name;
                    if (player.getName() == null) {
                        name = "Unknown Player";
                    } else {
                        name = player.getName();
                    }
                    long blocks = top10BlocksWeekly.get(uuid);
                    sender.sendMessage(TOP_FORMAT_BLOCKS.replace("%position%", String.valueOf(i + 1)).replace("%player%", name).replace("%amount%", String.format("%,d", blocks)));
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            }
            sender.sendMessage(Text.colorize(SPACER_LINE));
        });
    }

    public BlockReward getNextBlockReward(OfflinePlayer p) {
        long blocksBroken = this.getPlayerBrokenBlocks(p);

        for (long l : this.blockRewards.keySet()) {
            if (l > blocksBroken) {
                return this.blockRewards.get(l);
            }
        }
        return null;
    }

    public void resetBlocksTopWeekly(CommandSender sender) {
        Schedulers.async().run(() -> {
            sender.sendMessage(Text.colorize("&7&oStarting to reset BlocksTop - Weekly. This may take a while..."));
            this.top10BlocksWeekly.clear();
            this.nextResetWeekly = Time.nowMillis() + TimeUnit.DAYS.toMillis(7);
            try (Connection con = this.plugin.getCore().getSqlDatabase().getHikari().getConnection(); PreparedStatement statement = con.prepareStatement("DELETE FROM " + MySQLDatabase.BLOCKS_WEEKLY_DB_NAME)) {
                statement.execute();
                sender.sendMessage(Text.colorize("&aBlocksTop - Weekly - Resetted!"));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private String getTimeLeftUntilWeeklyReset() {

        if (System.currentTimeMillis() > nextResetWeekly) {
            return "RESET SOON";
        }


        long timeLeft = nextResetWeekly - System.currentTimeMillis();

        long days = timeLeft / (24 * 60 * 60 * 1000);
        timeLeft -= days * (24 * 60 * 60 * 1000);

        long hours = timeLeft / (60 * 60 * 1000);
        timeLeft -= hours * (60 * 60 * 1000);

        long minutes = timeLeft / (60 * 1000);
        timeLeft -= minutes * (60 * 1000);

        long seconds = timeLeft / (1000);

        timeLeft -= seconds * 1000;

        return new StringBuilder().append(days).append("d ").append(hours).append("h ").append(minutes).append("m ").append(seconds).append("s").toString();
    }

    public void saveWeeklyReset() {
        this.plugin.getConfig().set("next-reset-weekly", this.nextResetWeekly).save();

    }

    @AllArgsConstructor
    @Getter
    private class BlockReward {

        private long blocksRequired;
        private List<String> commandsToRun;
        private String message;

        public void giveTo(Player p) {

            Schedulers.sync().run(() -> {
                for (String s : this.commandsToRun) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s.replace("%player%", p.getName()));
                }
            });

            p.sendMessage(this.message);
        }


    }
}
