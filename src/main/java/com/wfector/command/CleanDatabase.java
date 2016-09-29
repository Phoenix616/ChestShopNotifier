package com.wfector.command;

import com.wfector.notifier.ChestShopNotifier;
import com.wfector.util.Time;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CleanDatabase extends BukkitRunnable {

    private final ChestShopNotifier plugin;
    private final CommandSender sender;
    private boolean readOnly = true;
    private int cleanBefore = 0;
    private UUID user = null;

    public CleanDatabase(ChestShopNotifier plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    public void run() {
        Connection c = null;
        try {
            c = plugin.getConnection();
            Statement statement = c.createStatement();
            List<String> where = new ArrayList<>();
            if (readOnly) {
                where.add("`Unread`='0'");
            }
            if (cleanBefore > 0) {
                where.add("`Time`<" + (int) (Time.getEpochTime() - cleanBefore * 24 * 60 * 60));
            }
            if (user != null) {
                where.add("`ShopOwnerId`='" + user.toString() + "'");
            }
            StringBuilder whereStr = new StringBuilder();
            if (!where.isEmpty()) {
                whereStr.append(" WHERE ").append(where.get(0));
                for(int i = 1; i < where.size(); i++) {
                    whereStr.append(" AND ").append(where.get(i));
                }
            }
            statement.executeUpdate("DELETE FROM csnUUID" + whereStr + ";");

            sender.sendMessage(ChatColor.RED + "Cleaned database from " + (readOnly ? "read" : "all") + " entries!");
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "Database error while executing this command!");
            e.printStackTrace();
        } finally {
            ChestShopNotifier.close(c);
        }

    }

    public void cleanReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void cleanBefore(int days) {
        this.cleanBefore = days;
    }

    public void cleanUser(UUID user) {
        this.user = user;
    }

    public enum Parameter {
        OLDER_THAN("o", "<days>"),
        USER("u", "<username/uuid>"),
        READ_ONLY("r"),
        ALL("a");

        private final String shortName;
        private final String[] args;

        Parameter(String shortName) {
            this.shortName = shortName;
            this.args = new String[]{};
        }

        Parameter(String shortName, String... args) {
            this.shortName = shortName;
            this.args = args;
        }

        public String getShortName() {
            return shortName;
        }

        public String[] getArgs() {
            return args;
        }

        public String getUsage() {
            StringBuilder sb = new StringBuilder();
            for (String arg : args) {
                sb.append(" ").append(arg);
            }
            return "--" + toString().toLowerCase() + sb.toString();
        }

        public static Parameter getFromInput(String input) {
            for (Parameter param : values()) {
                if (input.startsWith("--")) {
                    if (param.toString().equalsIgnoreCase(input.substring(2))) {
                        return param;
                    }
                } else if (input.startsWith("-")) {
                    if (param.getShortName().equals(input.substring(1))) {
                        return param;
                    }
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return name().replace("_", "-");
        }
    }
}
