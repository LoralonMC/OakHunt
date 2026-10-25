package dev.oakheart.oakhunt;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.oakheart.message.MessageManager;
import dev.oakheart.oakhunt.commands.HeadHuntCommand;
import dev.oakheart.oakhunt.config.ConfigManager;
import dev.oakheart.oakhunt.gui.ChatPrompt;
import dev.oakheart.oakhunt.gui.HuntActions;
import dev.oakheart.oakhunt.gui.MenuListener;
import dev.oakheart.oakhunt.gui.Menus;
import dev.oakheart.oakhunt.hook.CoreProtectHook;
import dev.oakheart.oakhunt.hook.ExecutableItemsHook;
import dev.oakheart.oakhunt.hook.HeadDatabaseHook;
import dev.oakheart.oakhunt.hook.NexoHook;
import dev.oakheart.oakhunt.hook.OakHuntExpansion;
import dev.oakheart.oakhunt.hook.OverflowHook;
import dev.oakheart.oakhunt.listeners.EditorListener;
import dev.oakheart.oakhunt.listeners.HeadListener;
import dev.oakheart.oakhunt.listeners.PacketClickListener;
import dev.oakheart.oakhunt.listeners.ProtectionListener;
import dev.oakheart.oakhunt.listeners.WorldListener;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.service.EditorActions;
import dev.oakheart.oakhunt.service.EditorService;
import dev.oakheart.oakhunt.service.FeedbackService;
import dev.oakheart.oakhunt.service.HuntManager;
import dev.oakheart.oakhunt.service.ImportService;
import dev.oakheart.oakhunt.service.ProgressService;
import dev.oakheart.oakhunt.service.RewardService;
import dev.oakheart.oakhunt.service.SkinService;
import dev.oakheart.oakhunt.service.WorldSync;
import dev.oakheart.oakhunt.storage.HuntDatabase;
import dev.oakheart.oakhunt.util.Messages;
import dev.oakheart.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public final class OakHunt extends JavaPlugin {

    private ConfigManager configManager;
    private DebugLogger debugLogger;
    private Messages messages;
    private HuntDatabase database;

    private HuntManager huntManager;
    private ProgressService progressService;
    private RewardService rewardService;
    private FeedbackService feedbackService;
    private WorldSync worldSync;
    private EditorService editorService;
    private EditorActions editorActions;
    private SkinService skinService;
    private ImportService importService;

    private Menus menus;
    private ChatPrompt chatPrompt;
    private HuntActions huntActions;

    private OverflowHook overflowHook;
    private ExecutableItemsHook executableItemsHook;
    private NexoHook nexoHook;
    private HeadDatabaseHook headDatabaseHook;
    private CoreProtectHook coreProtectHook;
    private PacketListenerCommon packetListener;

    @Override
    public void onEnable() {
        try {
            checkDependencies();
            initializeComponents();
            registerListeners();
            registerCommands();
            registerPlaceholders();
            startServices();
            getLogger().info("OakHunt enabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable OakHunt", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (packetListener != null) PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        if (editorService != null) editorService.stop();
        if (feedbackService != null) feedbackService.stop();
        if (huntManager != null) huntManager.stop();
        if (database != null) database.close();
        getLogger().info("OakHunt disabled");
    }

    private void checkDependencies() {
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            throw new IllegalStateException("PacketEvents is required (editor previews)");
        }
    }

    private void initializeComponents() throws Exception {
        configManager = new ConfigManager(this);
        configManager.load();
        debugLogger = new DebugLogger(getLogger(), () -> configManager.isDebug());
        MessageManager messageManager = new MessageManager(this, getLogger());
        messageManager.load();
        messages = new Messages(messageManager);

        overflowHook = new OverflowHook(getLogger());
        overflowHook.initialize();
        executableItemsHook = new ExecutableItemsHook(getLogger());
        executableItemsHook.initialize();
        nexoHook = new NexoHook(getLogger());
        nexoHook.initialize();
        headDatabaseHook = new HeadDatabaseHook(getLogger());
        headDatabaseHook.initialize();
        coreProtectHook = new CoreProtectHook(getLogger());
        coreProtectHook.initialize();

        database = new HuntDatabase(new File(getDataFolder(), "oakhunt.db"), getLogger());
        database.open();

        huntManager = new HuntManager(this, database);
        progressService = new ProgressService(this, database);
        rewardService = new RewardService(this);
        feedbackService = new FeedbackService(this);
        worldSync = new WorldSync(this);
        editorService = new EditorService(this);
        editorActions = new EditorActions(this);
        skinService = new SkinService(this);
        importService = new ImportService(this);
        menus = new Menus(this);
        chatPrompt = new ChatPrompt(this);
        huntActions = new HuntActions(this);

        huntManager.load();
        progressService.load();
    }

    private void registerListeners() {
        for (Listener listener : new Listener[]{new HeadListener(this), new ProtectionListener(this),
                new EditorListener(this), new WorldListener(this), new MenuListener(), chatPrompt}) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        packetListener = PacketEvents.getAPI().getEventManager()
                .registerListener(new PacketClickListener(this), PacketListenerPriority.NORMAL);
    }

    private void registerCommands() {
        new HeadHuntCommand(this).register();
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found; placeholders disabled");
            return;
        }
        new OakHuntExpansion(this).register();
        getLogger().info("PlaceholderAPI placeholders registered");
    }

    private void startServices() {
        huntManager.startScheduler();
        feedbackService.start();
        editorService.start();
        // Bring every head in line with its hunt's state (e.g. a scheduled end passed while down).
        Bukkit.getScheduler().runTask(this, () -> {
            for (Hunt hunt : huntManager.hunts()) worldSync.syncHunt(hunt);
        });
    }

    /**
     * Reloads config.yml and messages.yml and restarts config-dependent tasks.
     *
     * @return false if config.yml failed validation (the previous config stays)
     */
    public boolean reload() {
        boolean ok = configManager.reload();
        messages.manager().reload();
        if (ok) feedbackService.start();
        return ok;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DebugLogger getDebugLogger() { return debugLogger; }
    public Messages getMessages() { return messages; }
    public HuntDatabase getDatabase() { return database; }
    public HuntManager getHuntManager() { return huntManager; }
    public ProgressService getProgressService() { return progressService; }
    public RewardService getRewardService() { return rewardService; }
    public FeedbackService getFeedbackService() { return feedbackService; }
    public WorldSync getWorldSync() { return worldSync; }
    public EditorService getEditorService() { return editorService; }
    public EditorActions getEditorActions() { return editorActions; }
    public SkinService getSkinService() { return skinService; }
    public ImportService getImportService() { return importService; }
    public Menus getMenus() { return menus; }
    public ChatPrompt getChatPrompt() { return chatPrompt; }
    public HuntActions getHuntActions() { return huntActions; }
    public OverflowHook getOverflowHook() { return overflowHook; }
    public ExecutableItemsHook getExecutableItemsHook() { return executableItemsHook; }
    public NexoHook getNexoHook() { return nexoHook; }
    public HeadDatabaseHook getHeadDatabaseHook() { return headDatabaseHook; }
    public CoreProtectHook getCoreProtectHook() { return coreProtectHook; }
}
