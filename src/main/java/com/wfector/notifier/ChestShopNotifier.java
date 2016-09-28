package com.wfector.notifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;

import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import com.wfector.command.CommandRunner;
import com.wfector.util.Time;

import org.bukkit.scheduler.BukkitRunnable;

import static com.Acrobot.Breeze.Utils.MaterialUtil.getSignName;

public class ChestShopNotifier extends JavaPlugin implements Listener {

    private HikariDataSource ds;

    private ArrayList<String> batch = new ArrayList<String>();

    private boolean verboseEnabled;
    private boolean joinNotificationEnabled;
    private int joinNotificationDelay;

    public boolean pluginEnabled = false;
    public boolean logAdminShop = true;

    public void onEnable() {
        getCommand("csn").setExecutor(new CommandRunner(this));

        saveDefaultConfig();
        updateConfiguration(null);

        getServer().getPluginManager().registerEvents(this, this);
    }

    public void onDisable() {
        if(batch.size() > 0) {
            getLogger().log(Level.INFO, "Database queue is not empty. Uploading now...");
            new BatchRunner(this).run();
            getLogger().log(Level.INFO, "Done uploading database queue!");
        }
        ds.close();

    }

    public boolean isPluginEnabled() {
        return isEnabled() && pluginEnabled;
    }

    public void updateConfiguration(final CommandSender sender) {
        verboseEnabled = getConfig().getBoolean("debugging.verbose");
        joinNotificationEnabled = getConfig().getBoolean("notifications.notify-on-user-join");
        joinNotificationDelay = getConfig().getInt("notifications.delay-seconds");
        logAdminShop = getConfig().getBoolean("logging.admin-shop");

        String dbHost = getConfig().getString("database.host");
        int dbPort = getConfig().getInt("database.port");
        String dbName = getConfig().getString("database.dbname");
        String dbUsername = getConfig().getString("database.username");
        String dbPassword = getConfig().getString("database.password");

        ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName);
        ds.setUsername(dbUsername);
        ds.setPassword(dbPassword);
        ds.setConnectionTimeout(5000);

        getLogger().log(Level.INFO, "Connecting to the database...");

        new BukkitRunnable() {
            public void run() {
                Connection c = null;
                try {
                    c = getConnection();
                    Statement statement = c.createStatement();

                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS csnUUID (Id int(11) AUTO_INCREMENT, ShopOwnerId VARCHAR(36), CustomerId VARCHAR(36), ItemId VARCHAR(1000), Mode INT(11), Amount FLOAT(53), Quantity INT(11), Time INT(11), Unread INT(11), PRIMARY KEY (Id))");

                    pluginEnabled = true;
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    ChestShopNotifier.close(c);
                }

                if(pluginEnabled) {
                    getLogger().log(Level.WARNING, "Database connected!");
                    sender.sendMessage(ChatColor.LIGHT_PURPLE + "ChestShop Notifier // " + ChatColor.GREEN + "Reloaded!");
                    sender.sendMessage(ChatColor.LIGHT_PURPLE + "ChestShop Notifier // " + ChatColor.GREEN + "Database connected!");

                } else {
                    getLogger().log(Level.WARNING, "Failed to connect to the database! Disabling connections!");
                    sender.sendMessage(ChatColor.LIGHT_PURPLE + "ChestShop Notifier // " + ChatColor.GREEN + "Reloaded!");
                    sender.sendMessage(ChatColor.LIGHT_PURPLE + "ChestShop Notifier // " + ChatColor.RED + "Database failed to connect!");
                }
            }
        }.runTaskAsynchronously(this);
    }

    public static void close(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets a message from the config file.
     * @param string The name of the message to get
     * @return The message or null if it doesn't exist
     */
    public String getMessage(String string) {
        return (getConfig().contains("messages." + string)) ? ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + string)) : null;
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent e) {
        if(!joinNotificationEnabled) {
            debug("Join notifications are " + joinNotificationEnabled + ", skipping...");
            return;
        }

        debug("User joined. Checking for updates...");

        if(!isPluginEnabled()) {
            debug("Cannot notify user. Plugin is disabled.");
            return;
        }

        final Player p = e.getPlayer();

        new LoginRunner(this, p.getUniqueId()).runTaskLaterAsynchronously(this, joinNotificationDelay * 20);
    }

    @EventHandler
    public boolean onChestShopTransaction(TransactionEvent e) {
        UUID ownerId = e.getOwner().getUniqueId();

        if(!this.logAdminShop && NameManager.isAdminShop(ownerId)) return true;

        TransactionType f = e.getTransactionType();

        Integer mode = (f == TransactionType.BUY) ? 1 : 2;

        double price = e.getPrice();
        UUID clientId = e.getClient().getUniqueId();

        StringBuilder items = new StringBuilder(50);
        int itemQuantities = 0;

        for (ItemStack item : e.getStock()) {
            items.append(getSignName(item));
            itemQuantities = item.getAmount();
        }

        String itemId = items.toString();

        batch.add("('" + ownerId.toString() + "', '" + clientId.toString() + "', '" + itemId + "', '" + mode.toString() + "', '" + String.valueOf(price) + "', '" + Time.GetEpochTime() + "', '" + String.valueOf(itemQuantities) + "', '0')");

        debug("Item added to batch.");
        new BatchRunner(this).runTaskAsynchronously(this);

        return true;
    }

    public void debug(String d) {
        if(verboseEnabled)
            getLogger().log(Level.INFO, d);
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();

    }

    public ArrayList<String> getBatch() {
        return batch;
    }
}