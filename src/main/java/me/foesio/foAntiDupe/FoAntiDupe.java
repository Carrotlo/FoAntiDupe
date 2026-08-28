package me.foesio.foAntiDupe;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.CommandVisibilityService;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoAntiDupe extends JavaPlugin {

    private FoCoreContext core;
    private ConfigStore configStore;
    private FoMessageService messageService;
    private RuleService ruleService;
    private AlertService alertService;
    private EditorManager editorManager;
    private FoFileLogger pluginLogService;
    private CommandVisibilityService commandVisibilityService;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;

    @Override
    public void onEnable() {
        this.pluginLogService = FoFileLogger.create(this);
        this.pluginLogService.configure(true, true);
        this.pluginLogService.info("FoAntiDupe " + getDescription().getVersion() + " enabling");

        this.configStore = new ConfigStore(this);
        this.configStore.load();
        recreateCore();
        this.sounds = core.createSounds();
        this.adminSounds = FoAdminSounds.create(sounds);
        this.editorSounds = FoEditorSounds.create(sounds);
        this.guiSounds = FoGuiSounds.create(sounds);

        this.messageService = FoMessageService.load(this, messageMigrations());

        this.ruleService = new RuleService(configStore);
        this.ruleService.reload();

        this.alertService = new AlertService(this, configStore, messageService, pluginLogService, guiSounds);
        this.editorManager = new EditorManager(this, configStore, messageService, ruleService, editorSounds);

        getServer().getPluginManager().registerEvents(new DetectionListener(this, configStore, ruleService, alertService), this);
        getServer().getPluginManager().registerEvents(editorManager, this);
        getServer().getPluginManager().registerEvents(alertService, this);

        FoAntiDupeCommand command = new FoAntiDupeCommand(this, messageService, alertService, editorManager);
        PluginCommand pluginCommand = getCommand("foantidupe");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        this.commandVisibilityService = CommandVisibilityService.builder(this)
                .hideWithout("foantidupe.admin", "foantidupe", "fad")
                .register();
        this.pluginLogService.info("FoAntiDupe enabled with " + ruleService.rules().size() + " rules");
    }

    @Override
    public void onDisable() {
        if (pluginLogService != null) {
            pluginLogService.info("FoAntiDupe disabling");
        }
        if (commandVisibilityService != null) {
            commandVisibilityService.close();
            commandVisibilityService = null;
        }
        if (core != null) {
            core.close();
            core = null;
        }
        if (pluginLogService != null) {
            pluginLogService.close();
        }
    }

    public FoReloadResult reloadFoAntiDupe() {
        FoReloadResult result = FoReloadRegistry.create()
                .add("config", configStore::load)
                .add("core", this::recreateCore)
                .add("sounds", sounds::reload)
                .addMessages(messageService)
                .add("rules", ruleService::reload)
                .reload();
        if (result.successful()) {
            pluginLogService.info("Reloaded config.yml, flag-rules.yml, messages.yml, and " + ruleService.rules().size() + " rules");
        } else {
            pluginLogService.warn("Reload failed at " + result.failedStep() + ": " + result.errorMessage());
        }
        return result;
    }

    public ConfigStore configStore() {
        return configStore;
    }

    public FoMessageService messages() {
        return messageService;
    }

    public RuleService rules() {
        return ruleService;
    }

    public FoFileLogger pluginLog() {
        return pluginLogService;
    }

    public FoCoreContext core() {
        return core;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public FoEditorSounds editorSounds() {
        return editorSounds;
    }

    public FoGuiSounds guiSounds() {
        return guiSounds;
    }

    public FoSoundService sounds() {
        return sounds;
    }

    private void recreateCore() {
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        core.warnIfNativeDialogsUnavailable();
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .add(this::migrateLegacyPrefix)
                .build();
    }

    private boolean migrateLegacyPrefix(FileConfiguration config) {
        String legacyPrefix = config.getString("prefix");
        if (legacyPrefix == null) {
            return false;
        }
        String tokenPrefix = config.getString("tokens.prefix");
        if (tokenPrefix == null
                || tokenPrefix.equals("{theme}FoAntiDupe &8» {muted}")
                || tokenPrefix.equals(FoStyle.defaultPrefixTemplate())) {
            config.set("tokens.prefix", legacyPrefix);
        }
        config.set("prefix", null);
        return true;
    }
}
