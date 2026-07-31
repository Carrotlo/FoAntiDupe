package me.foesio.foAntiDupe;

import me.foesio.core.dialog.NativeDialogConfigDefaults;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class ConfigStore {

    private static final String FLAG_RULES_FILE = "flag-rules.yml";

    private final FoAntiDupe plugin;
    private File flagRulesFile;
    private YamlConfiguration flagRules;

    public ConfigStore(FoAntiDupe plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        boolean existingConfig = configFile.isFile();
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        NativeDialogConfigDefaults.addDefaults(plugin.getConfig());
        plugin.getConfig().set("editor", null);
        loadFlagRules(existingConfig);
        plugin.saveConfig();
    }

    public FileConfiguration config() {
        return plugin.getConfig();
    }

    public FileConfiguration flagRules() {
        return flagRules;
    }

    public void save() {
        plugin.saveConfig();
    }

    public void saveFlagRules() {
        try {
            flagRules.save(flagRulesFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + FLAG_RULES_FILE, exception);
        }
    }

    public boolean bool(String path, boolean fallback) {
        return config().getBoolean(path, fallback);
    }

    public int integer(String path, int fallback) {
        return config().getInt(path, fallback);
    }

    public long secondsAsMillis(String path, int fallbackSeconds) {
        return Math.max(0L, integer(path, fallbackSeconds)) * 1000L;
    }

    private void loadFlagRules(boolean existingConfig) {
        this.flagRulesFile = new File(plugin.getDataFolder(), FLAG_RULES_FILE);
        ConfigurationSection legacyRules = plugin.getConfig().getConfigurationSection("rules");
        if (legacyRules != null) {
            migrateLegacyRules(legacyRules);
            return;
        }
        if (!flagRulesFile.isFile()) {
            if (existingConfig) {
                saveEmptyFlagRules();
            } else {
                plugin.saveResource(FLAG_RULES_FILE, false);
            }
        }
        this.flagRules = loadYaml(flagRulesFile);
    }

    private void migrateLegacyRules(ConfigurationSection legacyRules) {
        this.flagRules = new YamlConfiguration();
        ConfigurationSection migratedRules = flagRules.createSection("rules");
        copySection(legacyRules, migratedRules);
        saveFlagRules();
        plugin.getConfig().set("rules", null);
    }

    private void saveEmptyFlagRules() {
        this.flagRules = new YamlConfiguration();
        flagRules.createSection("rules");
        saveFlagRules();
    }

    private YamlConfiguration loadYaml(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load " + file.getName(), exception);
        }
        return yaml;
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection child) {
                copySection(child, target.createSection(key));
            } else {
                target.set(key, value);
            }
        }
    }
}
