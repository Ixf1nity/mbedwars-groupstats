package me.infinity.groupstats.manager;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.j256.ormlite.table.TableUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import me.infinity.groupstats.GroupNode;
import me.infinity.groupstats.GroupStatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Getter
public class DatabaseInitiator {

    private final GroupStatsPlugin instance;

    private final HikariDataSource hikariDataSource;
    private Dao<GroupNode, UUID> profileDao;
    private ConnectionSource connectionSource;

    private String tableName;

    private String address, database, username, password;
    private int port;
    private boolean ssl;

    public DatabaseInitiator(GroupStatsPlugin instance) {
        this.instance = instance;
        this.loadCredentials();

        HikariConfig hikariConfig = this.getHikariConfig();
        this.hikariDataSource = new HikariDataSource(hikariConfig);
        DatabaseTableConfig<GroupNode> tableConfig = new DatabaseTableConfig<>();
        tableConfig.setDataClass(GroupNode.class);
        tableConfig.setTableName(tableName);

        try {
            this.connectionSource = new DataSourceConnectionSource(this.hikariDataSource, hikariConfig.getJdbcUrl());
            this.profileDao = DaoManager.createDao(this.connectionSource, tableConfig);
            TableUtils.createTableIfNotExists(this.connectionSource, tableConfig);
        } catch (Exception ex) {
            instance.getLogger().severe(ex.getMessage());
            Bukkit.getServer().getPluginManager().disablePlugin(instance);
        }
    }

    private @NotNull HikariConfig getHikariConfig() {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl("jdbc:mysql://" + this.address + ":" + this.port + "/" + this.database);
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8");
        hikariConfig.addDataSourceProperty("useUnicode", true);
        hikariConfig.addDataSourceProperty("useSSL", this.ssl);
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setUsername(this.username);
        hikariConfig.setPassword(this.password);
        hikariConfig.setPoolName("mcfleet-mbedwars-groupstats");
        return hikariConfig;
    }

    private void loadCredentials() {
        ConfigurationSection section = instance.getConfig().getConfigurationSection("DATABASE");
        this.tableName = section.getString("TABLE");
        this.address = section.getString("ADDRESS");
        this.port = section.getInt("PORT");
        this.database = section.getString("DATABASE");
        this.username = section.getString("USERNAME");
        this.password = section.getString("PASSWORD");
        this.ssl = section.getBoolean("SSL");
    }

    public void disconnect() {
        if (this.connectionSource != null) {
            try {
                this.connectionSource.close();
            } catch (Exception e) {
                instance.getLogger().severe(e.getMessage());
            }
        }
        if (this.hikariDataSource != null) this.hikariDataSource.close();
    }

}
