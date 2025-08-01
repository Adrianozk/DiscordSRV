/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv;

import alexh.weak.Dynamic;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neovisionaries.ws.client.DualStackMode;
import com.neovisionaries.ws.client.ProxySettings;
import com.neovisionaries.ws.client.WebSocketFactory;
import github.scarsz.configuralize.DynamicConfig;
import github.scarsz.configuralize.Language;
import github.scarsz.configuralize.ParseException;
import github.scarsz.discordsrv.api.ApiManager;
import github.scarsz.discordsrv.api.events.*;
import github.scarsz.discordsrv.hooks.PluginHook;
import github.scarsz.discordsrv.hooks.VaultHook;
import github.scarsz.discordsrv.hooks.chat.ChatHook;
import github.scarsz.discordsrv.hooks.vanish.VanishHook;
import github.scarsz.discordsrv.hooks.world.WorldHook;
import github.scarsz.discordsrv.listeners.*;
import github.scarsz.discordsrv.modules.alerts.AlertListener;
import github.scarsz.discordsrv.modules.requirelink.RequireLinkModule;
import github.scarsz.discordsrv.modules.voice.VoiceModule;
import github.scarsz.discordsrv.objects.CancellationDetector;
import github.scarsz.discordsrv.objects.Lag;
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.objects.log4j.JdaFilter;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.objects.managers.CommandManager;
import github.scarsz.discordsrv.objects.managers.GroupSynchronizationManager;
import github.scarsz.discordsrv.objects.managers.IncompatibleClientManager;
import github.scarsz.discordsrv.objects.managers.link.JdbcAccountLinkManager;
import github.scarsz.discordsrv.objects.managers.link.file.AppendOnlyFileAccountLinkManager;
import github.scarsz.discordsrv.objects.proxy.AlwaysEnabledPluginDynamicProxy;
import github.scarsz.discordsrv.objects.threads.*;
import github.scarsz.discordsrv.util.*;
import lombok.Getter;
import me.scarsz.jdaappender.ChannelLoggingHandler;
import me.scarsz.jdaappender.LogItem;
import me.scarsz.jdaappender.LogLevel;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import okhttp3.*;
import okhttp3.internal.Util;
import okhttp3.internal.tls.OkHostnameVerifier;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Warning;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitWorker;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.minidns.DnsClient;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.record.Record;

import javax.net.ssl.SSLContext;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * DiscordSRV's main class, can be accessed via {@link #getPlugin()}.
 *
 * @see #getAccountLinkManager()
 * @see #sendJoinMessage(Player, String)
 * @see #sendLeaveMessage(Player, String)
 */
@SuppressWarnings({"unused", "WeakerAccess", "ConstantConditions"})
public class DiscordSRV extends JavaPlugin {

    public static final ApiManager api = new ApiManager();
    public static boolean isReady = false;
    public static boolean shuttingDown = false;
    public static boolean updateChecked = false;
    public static boolean invalidBotToken = false;
    private static boolean offlineUuidAvatarUrlNagged = false;
    public static boolean updateIsAvailable = false;
    public static String version = "";

    // Managers
    private AccountLinkManager accountLinkManager;
    @Getter private CommandManager commandManager = new CommandManager();
    @Getter private GroupSynchronizationManager groupSynchronizationManager = new GroupSynchronizationManager();
    @Getter private IncompatibleClientManager incompatibleClientManager = new IncompatibleClientManager();

    // Threads
    @Getter private ChannelTopicUpdater channelTopicUpdater;
    @Getter private ChannelUpdater channelUpdater;
    @Getter private NicknameUpdater nicknameUpdater;
    @Getter private PresenceUpdater presenceUpdater;
    @Getter private ServerWatchdog serverWatchdog;
    @Getter private ScheduledExecutorService updateChecker = null;

    // Modules
    @Getter private AlertListener alertListener = null;
    @Getter private RequireLinkModule requireLinkModule;
    @Getter private VoiceModule voiceModule;

    // Config
    @Getter private final Map<String, String> channels = new LinkedHashMap<>(); // <in-game channel name, discord channel>
    @Getter private final Map<String, String> roleAliases = new LinkedHashMap<>(); // key always lowercase
    @Getter private final Map<Pattern, String> consoleRegexes = new LinkedHashMap<>();
    @Getter private final Map<Pattern, String> gameRegexes = new LinkedHashMap<>();
    @Getter private final Map<Pattern, String> discordRegexes = new LinkedHashMap<>();
    @Getter private final Map<Pattern, String> webhookUsernameRegexes = new LinkedHashMap<>();
    private final DynamicConfig config;

    // Debugger
    @Getter private final Set<String> debuggerCategories = new CopyOnWriteArraySet<>();

    @Getter private final long startTime = System.currentTimeMillis();
    @Getter private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @SuppressWarnings("deprecation")
    @Getter private CancellationDetector<AsyncPlayerChatEvent> legacyCancellationDetector = null;
    @Getter private CancellationDetector<?> modernCancellationDetector = null;
    @Getter private boolean modernChatEventAvailable = false;
    @Getter private final Set<PluginHook> pluginHooks = new HashSet<>();

    // Files
    @Getter private final File configFile = new File(getDataFolder(), "config.yml");
    @Getter private final File messagesFile = new File(getDataFolder(), "messages.yml");
    @Getter private final File voiceFile = new File(getDataFolder(), "voice.yml");
    @Getter private final File linkingFile = new File(getDataFolder(), "linking.yml");
    @Getter private final File synchronizationFile = new File(getDataFolder(), "synchronization.yml");
    @Getter private final File alertsFile = new File(getDataFolder(), "alerts.yml");
    @Getter private final File debugFolder = new File(getDataFolder(), "debug");
    @Getter private final File logFolder = new File(getDataFolder(), "discord-console-logs");

    // JDA & JDA related
    @Getter private JDA jda = null;
    private ExecutorService callbackThreadPool;
    @Getter private ChannelLoggingHandler consoleAppender;
    private JdaFilter jdaFilter;

    public static DiscordSRV getPlugin() {
        return getPlugin(DiscordSRV.class);
    }
    public static DynamicConfig config() {
        return getPlugin().config;
    }
    public void reloadConfig() {
        try {
            config().loadAll();
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public void reloadAllowedMentions() {
        // set default mention types to never ping everyone/here
        MessageAction.setDefaultMentions(config().getStringList("DiscordChatChannelAllowedMentions").stream()
                .map(s -> {
                    try {
                        return Message.MentionType.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        DiscordSRV.error("Unknown mention type \"" + s + "\" defined in DiscordChatChannelAllowedMentions");
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet()));
        DiscordSRV.debug("Allowed chat mention types: " + MessageAction.getDefaultMentions().stream().map(Enum::name).collect(Collectors.joining(", ")));
    }

    public void reloadChannels() {
        synchronized (channels) {
            channels.clear();
            config().dget("Channels").children().forEach(dynamic ->
                    this.channels.put(dynamic.key().convert().intoString(), dynamic.convert().intoString()));
        }
    }
    public void reloadRoleAliases() {
        synchronized (roleAliases) {
            roleAliases.clear();
            config().dget("DiscordChatChannelRoleAliases").children().forEach(dynamic ->
                    this.roleAliases.put(dynamic.key().convert().intoString().toLowerCase(), dynamic.convert().intoString()));
        }
    }
    public void reloadRegexes() {
        synchronized (consoleRegexes) {
            consoleRegexes.clear();
            loadRegexesFromConfig(config().dget("DiscordConsoleChannelFilters"), consoleRegexes);
        }
        synchronized (gameRegexes) {
            gameRegexes.clear();
            loadRegexesFromConfig(config().dget("DiscordChatChannelGameFilters"), gameRegexes);
        }
        synchronized (discordRegexes) {
            discordRegexes.clear();
            loadRegexesFromConfig(config().dget("DiscordChatChannelDiscordFilters"), discordRegexes);
        }
        synchronized (webhookUsernameRegexes) {
            webhookUsernameRegexes.clear();
            loadRegexesFromConfig(config().dget("Experiment_WebhookChatMessageUsernameFilters"), webhookUsernameRegexes);
        }
    }
    private void loadRegexesFromConfig(final Dynamic dynamic, final Map<Pattern, String> map) {
        dynamic.children().forEach(d -> {
            String key = d.key().convert().intoString();
            if (StringUtils.isEmpty(key)) return;
            try {
                Pattern pattern = Pattern.compile(key, Pattern.DOTALL);
                map.put(pattern, d.convert().intoString());
            } catch (PatternSyntaxException e) {
                error("Invalid regex pattern: " + key + " (" + e.getDescription() + ")");
            }
        });
    }
    public String getMainChatChannel() {
        return channels.size() != 0 ? channels.keySet().iterator().next() : null;
    }
    public TextChannel getMainTextChannel() {
        if (channels.isEmpty() || jda == null) return null;
        String firstChannel = channels.values().iterator().next();
        if (StringUtils.isBlank(firstChannel)) return null;
        return DiscordUtil.getTextChannelById(firstChannel);
    }
    public Guild getMainGuild() {
        if (jda == null) return null;

        return getMainTextChannel() != null
                ? getMainTextChannel().getGuild()
                : getConsoleChannel() != null
                    ? getConsoleChannel().getGuild()
                    : jda.getGuilds().size() > 0
                        ? jda.getGuilds().get(0)
                        : null;
    }
    public TextChannel getConsoleChannel() {
        if (jda == null) return null;

        String consoleChannel = config.getString("DiscordConsoleChannelId");
        return StringUtils.isNotBlank(consoleChannel) && StringUtils.isNumeric(consoleChannel)
                ? DiscordUtil.getTextChannelById(consoleChannel)
                : null;
    }
    public TextChannel getDestinationTextChannelForGameChannelName(String gameChannelName) {
        Map.Entry<String, String> entry = channels.entrySet().stream().filter(e -> e.getKey().equals(gameChannelName)).findFirst().orElse(null);
        String value = entry != null ? entry.getValue() : null;
        if (!StringUtils.isBlank(value)) {
            return jda.getTextChannelById(value); // found case-sensitive channel
        }

        // no case-sensitive channel found, try case in-sensitive
        entry = channels.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(gameChannelName)).findFirst().orElse(null);
        value = entry != null ? entry.getValue() : null;
        if (!StringUtils.isBlank(value)) {
            return jda.getTextChannelById(value); // found case-insensitive channel
        }

        return null; // no channel found, case-insensitive or not
    }
    public String getDestinationGameChannelNameForTextChannel(TextChannel source) {
        if (source == null) return null;
        return channels.entrySet().stream()
                .filter(entry -> source.getId().equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }
    public File getLogFile() {
        String fileName = config().getString("DiscordConsoleChannelUsageLog");
        if (StringUtils.isBlank(fileName)) return null;
        fileName = fileName.replace("%date%", TimeUtil.date());
        return new File(this.getLogFolder(), fileName);
    }

    // log messages
    public static void logThrowable(Throwable throwable, Consumer<String> logger) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));

        for (String line : stringWriter.toString().split("\n")) logger.accept(line);
    }
    public static void info(LangUtil.InternalMessage message) {
        info(message.toString());
    }
    public static void info(String message) {
        getPlugin().getLogger().info(message);
    }
    public static void warning(LangUtil.InternalMessage message) {
        warning(message.toString());
    }
    public static void warning(String message) {
        getPlugin().getLogger().warning(message);
    }
    public static void error(LangUtil.InternalMessage message) {
        error(message.toString());
    }
    public static void error(String message) {
        getPlugin().getLogger().severe(message);
    }
    public static void error(Throwable throwable) {
        logThrowable(throwable, DiscordSRV::error);
    }
    public static void error(String message, Throwable throwable) {
        error(message);
        error(throwable);
    }
    public static void debug(String message) {
        debug(Debug.UNCATEGORIZED, message);
    }
    public static void debug(Debug type, String message) {
        if (type.isVisible()) {
            getPlugin().getLogger().info("[" + type.name() + " DEBUG] " + message
                    + (Debug.CALLSTACKS.isVisible() ? "\n" + DebugUtil.getStackTrace() : ""));
        }
    }
    public static void debug(Throwable throwable) {
        debug(Debug.UNCATEGORIZED, throwable);
    }
    public static void debug(Debug type, Throwable throwable) {
        logThrowable(throwable, DiscordSRV::debug);
    }
    public static void debug(Throwable throwable, String message) {
        debug(Debug.UNCATEGORIZED, throwable, message);
    }
    public static void debug(Debug type, Throwable throwable, String message) {
        debug(throwable);
        debug(message);
    }
    public static void debug(Collection<String> message) {
        message.forEach(DiscordSRV::debug);
    }
    public static void debug(Debug type, Collection<String> message) {
        message.forEach(msg -> debug(type, msg));
    }

    public DiscordSRV() {
        super();

        // load config
        getDataFolder().mkdirs();
        config = new DynamicConfig();
        config.addSource(DiscordSRV.class, "config", getConfigFile());
        config.addSource(DiscordSRV.class, "messages", getMessagesFile());
        config.addSource(DiscordSRV.class, "voice", getVoiceFile());
        config.addSource(DiscordSRV.class, "linking", getLinkingFile());
        config.addSource(DiscordSRV.class, "synchronization", getSynchronizationFile());
        config.addSource(DiscordSRV.class, "alerts", getAlertsFile());
        String languageCode = System.getProperty("user.language").toUpperCase();
        Language language = null;
        try {
            Language lang = Language.valueOf(languageCode);
            if (config.isLanguageAvailable(lang)) {
                language = lang;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            String lang = language != null ? language.getName() : languageCode.toUpperCase();
            getLogger().info("Unknown user language " + lang + ".");
            getLogger().info("If you fluently speak " + lang + " as well as English, see the GitHub repo to translate it!");
        }
        if (language == null) language = Language.EN;
        config.setLanguage(language);
        try {
            config.saveAllDefaults();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save default config files", e);
        }
        try {
            config.loadAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
        String forcedLanguage = config.getString("ForcedLanguage");
        if (StringUtils.isNotBlank(forcedLanguage) && !forcedLanguage.equalsIgnoreCase("none")) {
            Arrays.stream(Language.values())
                    .filter(lang -> lang.getCode().equalsIgnoreCase(forcedLanguage) ||
                            lang.getName().equalsIgnoreCase(forcedLanguage)
                    )
                    .findFirst().ifPresent(config::setLanguage);
        }

        // Make discordsrv.sync.x & discordsrv.sync.deny.x permissions denied by default
        try {
            PluginDescriptionFile description = getDescription();
            Class<?> descriptionClass = description.getClass();

            List<org.bukkit.permissions.Permission> permissions = new ArrayList<>(description.getPermissions());
            for (String s : getGroupSynchronizables().keySet()) {
                permissions.add(new org.bukkit.permissions.Permission("discordsrv.sync." + s, null, PermissionDefault.FALSE));
                permissions.add(new org.bukkit.permissions.Permission("discordsrv.sync.deny." + s, null, PermissionDefault.FALSE));
            }

            Field permissionsField = descriptionClass.getDeclaredField("permissions");
            permissionsField.setAccessible(true);
            permissionsField.set(description, ImmutableList.copyOf(permissions));

            Class<?> pluginClass = getClass().getSuperclass();
            Field descriptionField = pluginClass.getDeclaredField("description");
            descriptionField.setAccessible(true);
            descriptionField.set(this, description);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        if (++DebugUtil.initializationCount > 1) {
            DiscordSRV.error(ChatColor.RED + LangUtil.InternalMessage.PLUGIN_RELOADED.toString());
            PlayerUtil.getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission("discordsrv.admin"))
                    .forEach(player -> MessageUtil.sendMessage(player, ChatColor.RED + LangUtil.InternalMessage.PLUGIN_RELOADED.toString()));
        }

        ConfigUtil.migrate();
        ConfigUtil.logMissingOptions();
        DiscordSRV.debug("Language is " + config.getLanguage().getName());

        version = getDescription().getVersion();
        Thread initThread = new Thread(this::init, "DiscordSRV - Initialization");
        initThread.setUncaughtExceptionHandler((t, e) -> {
            // make DiscordSRV go red in /plugins
            disablePlugin();
            error(e);
            getLogger().severe("DiscordSRV failed to load properly: " + e.getMessage() + ". See " + github.scarsz.discordsrv.util.DebugUtil.run("DiscordSRV") + " for more information. Can't figure it out? Go to https://discordsrv.com/discord for help");
        });
        initThread.start();

        if (Bukkit.getWorlds().size() > 0) {
            playerDataFolder = new File(Bukkit.getWorlds().get(0).getWorldFolder().getAbsolutePath(), "/playerdata");
        }
    }

    public void disablePlugin() {
        SchedulerUtil.runTask(
                this,
                () -> Bukkit.getPluginManager().disablePlugin(this)
        );

        PluginCommand pluginCommand = getCommand("discordsrv");
        if (pluginCommand != null && pluginCommand.getPlugin() == this) {
            try {
                Field owningPlugin = pluginCommand.getClass().getDeclaredField("owningPlugin");
                if (!owningPlugin.isAccessible()) owningPlugin.setAccessible(true);

                // make the command's owning plugin always enabled (give a better error to the user)
                owningPlugin.set(pluginCommand, new AlwaysEnabledPluginDynamicProxy().getProxy(this));
            } catch (Throwable ignored) {}
        }
    }

    public void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlugMan")) {
            Plugin plugMan = Bukkit.getPluginManager().getPlugin("PlugMan");
            try {
                List<String> ignoredPlugins = (List<String>) plugMan.getClass().getMethod("getIgnoredPlugins").invoke(plugMan);
                if (!ignoredPlugins.contains("DiscordSRV")) {
                    ignoredPlugins.add("DiscordSRV");
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {}
        }

        // check if the person is trying to use the plugin without updating to ASM 5
        try {
            File specialSourceFile = new File("libraries/net/md-5/SpecialSource/1.7-SNAPSHOT/SpecialSource-1.7-SNAPSHOT.jar");
            if (!specialSourceFile.exists()) specialSourceFile = new File("bin/net/md-5/SpecialSource/1.7-SNAPSHOT/SpecialSource-1.7-SNAPSHOT.jar");
            if (specialSourceFile.exists() && DigestUtils.md5Hex(FileUtils.readFileToByteArray(specialSourceFile)).equalsIgnoreCase("096777a1b6098130d6c925f1c04050a3")) {
                DiscordSRV.warning(LangUtil.InternalMessage.ASM_WARNING.toString()
                        .replace("{specialsourcefolder}", specialSourceFile.getParentFile().getPath())
                );
            }
        } catch (IOException e) {
            error(e);
        }

        requireLinkModule = new RequireLinkModule();

        // start the update checker (will skip if disabled)
        if (!isUpdateCheckDisabled()) {
            if (updateChecker == null) {
                final ThreadFactory gatewayThreadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - Update Checker").build();
                updateChecker = Executors.newScheduledThreadPool(1);
            }
            updateChecker.schedule(() -> {
                DiscordSRV.updateIsAvailable = UpdateUtil.checkForUpdates();
                DiscordSRV.updateChecked = true;
            }, 0, TimeUnit.SECONDS);
            updateChecker.scheduleAtFixedRate(() ->
                    DiscordSRV.updateIsAvailable = UpdateUtil.checkForUpdates(false),
                    6, 6, TimeUnit.HOURS
            );
        }

        // shutdown previously existing jda if plugin gets reloaded
        if (jda != null) try { jda.shutdown(); jda = null; } catch (Exception e) { error(e); }

        reloadAllowedMentions();

        // set proxy just in case this JVM doesn't have a proxy selector for some reason
        if (ProxySelector.getDefault() == null) {
            ProxySelector.setDefault(new ProxySelector() {
                private final List<Proxy> DIRECT_CONNECTION = Collections.unmodifiableList(Collections.singletonList(Proxy.NO_PROXY));
                public void connectFailed(URI arg0, SocketAddress arg1, IOException arg2) {}
                public List<Proxy> select(URI uri) { return DIRECT_CONNECTION; }
            });
        }

        // set ssl to TLSv1.2
        if (config().getBoolean("ForceTLSv12")) {
            try {
                SSLContext context = SSLContext.getInstance("TLSv1.2");
                context.init(null, null, null);
                SSLContext.setDefault(context);
            } catch (Exception ignored) {}
        }

        // check log4j capabilities
        boolean serverIsLog4jCapable = false;
        boolean serverIsLog4j21Capable = false;
        try {
            serverIsLog4jCapable = Class.forName("org.apache.logging.log4j.core.Logger") != null;
        } catch (ClassNotFoundException e) {
            error("Log4j classes are NOT available, console channel will not be attached");
        }
        try {
            serverIsLog4j21Capable = Class.forName("org.apache.logging.log4j.core.Filter") != null;
        } catch (ClassNotFoundException e) {
            error("Log4j 2.1 classes are NOT available, JDA messages will NOT be formatted properly");
        }

        // add log4j filter for JDA messages
        if (serverIsLog4j21Capable && jdaFilter == null) {
            try {
                Class<?> jdaFilterClass = Class.forName("github.scarsz.discordsrv.objects.log4j.JdaFilter");
                jdaFilter = (JdaFilter) jdaFilterClass.newInstance();
                ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger()).addFilter((org.apache.logging.log4j.core.Filter) jdaFilter);
                debug("JdaFilter applied");
            } catch (Exception e) {
                error("Failed to attach JDA message filter to root logger", e);
            }
        }

        if (Debug.JDA.isVisible()) {
            LoggerContext config = ((LoggerContext) LogManager.getContext(false));
            config.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.ALL);
            config.updateLoggers();
        }

        if (Debug.JDA_REST_ACTIONS.isVisible()) {
            RestAction.setPassContext(true);
        }

        // http client for JDA
        Dns dns = Dns.SYSTEM;
        try {
            List<InetAddress> fallbackDnsServers = new CopyOnWriteArrayList<>(Arrays.asList(
                    // CloudFlare resolvers
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    // Google resolvers
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4")
            ));

            dns = new Dns() {
                // maybe drop minidns in favor of something else
                // https://github.com/dnsjava/dnsjava/blob/master/src/main/java/org/xbill/DNS/SimpleResolver.java
                // https://satreth.blogspot.com/2015/01/java-dns-query.html

                private final DnsClient client = new DnsClient();
                private int failedRequests = 0;
                @NotNull @Override
                public List<InetAddress> lookup(@NotNull String host) throws UnknownHostException {
                    int max = config.getInt("MaximumAttemptsForSystemDNSBeforeUsingFallbackDNS");
                    //  0 = everything falls back (would only be useful when the system dns literally doesn't work & can't be fixed)
                    // <0 = nothing falls back, everything uses system dns
                    // >0 = falls back if goes past that amount of failed requests in a row
                    if (max < 0 || (max > 0 && failedRequests < max)) {
                        try {
                            List<InetAddress> result = Dns.SYSTEM.lookup(host);
                            failedRequests = 0; // reset on successful lookup
                            return result;
                        } catch (Exception e) {
                            failedRequests++;
                            DiscordSRV.error("System DNS FAILED to resolve hostname " + host + ", " +
                                    (max == 0 ? "" : failedRequests >= max ? "using fallback DNS for this request" : "switching to fallback DNS servers") + "!");
                            if (max == 0) {
                                // not using fallback
                                if (e instanceof UnknownHostException) {
                                    throw e;
                                } else {
                                    return null;
                                }
                            }
                        }
                    }
                    return lookupPublic(host);
                }
                private List<InetAddress> lookupPublic(String host) throws UnknownHostException {
                    for (InetAddress dnsServer : fallbackDnsServers) {
                        try {
                            DnsMessage query = client.query(host, Record.TYPE.A, Record.CLASS.IN, dnsServer).response;
                            if (query.responseCode != DnsMessage.RESPONSE_CODE.NO_ERROR) {
                                DiscordSRV.error("DNS server " + dnsServer.getHostAddress() + " failed our DNS query for " + host + ": " + query.responseCode.name());
                            }

                            List<InetAddress> resolved = query.answerSection.stream()
                                    .map(record -> record.payloadData.toString())
                                    .map(s -> {
                                        try {
                                            return InetAddress.getByName(s);
                                        } catch (UnknownHostException e) {
                                            // impossible
                                            error(e);
                                            return null;
                                        }
                                    })
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .collect(Collectors.toList());
                            if (resolved.size() > 0) {
                                return resolved;
                            } else {
                                DiscordSRV.error("DNS server " + dnsServer.getHostAddress() + " failed to resolve " + host + ": no results");
                            }
                        } catch (Exception e) {
                            DiscordSRV.error("DNS server " + dnsServer.getHostAddress() + " failed to resolve " + host, e);
                        }

                        // this dns server gave us an error so we move this dns server to the end of the
                        // list, effectively making it the last resort for future requests
                        fallbackDnsServers.remove(dnsServer);
                        fallbackDnsServers.add(dnsServer);
                    }

                    // this sleep is here to prevent OkHTTP from repeatedly trying to query DNS servers with no
                    // delay of it's own when internet connectivity is lost. that's extremely bad because it'll be
                    // spitting errors into the console and consuming 100% cpu
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}

                    UnknownHostException exception = new UnknownHostException("All DNS resolvers failed to resolve hostname " + host + ". Not good.");
                    exception.setStackTrace(new StackTraceElement[]{exception.getStackTrace()[0]});
                    throw exception;
                }
            };
        } catch (Exception e) {
            DiscordSRV.error("Failed to make custom DNS client", e);
        }

        Optional<Boolean> noopHostnameVerifier = config().getOptionalBoolean("NoopHostnameVerifier");

        // Limit okhttp to 20 concurrent requests to avoid hogging every available thread
        Dispatcher dispatcher = new Dispatcher(
                new ThreadPoolExecutor(
                        2, 20, 5, TimeUnit.SECONDS,
                        new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", false))
        );
        dispatcher.setMaxRequests(20);
        dispatcher.setMaxRequestsPerHost(20); // most requests are to discord.com
        ConnectionPool connectionPool = new ConnectionPool(5, 10, TimeUnit.SECONDS);

        String proxyHost = config.getString("ProxyHost");
        int proxyPort = config.getInt("ProxyPort");
        String authUser = config.getString("ProxyUser");
        String authPassword = config.getString("ProxyPassword");

        WebSocketFactory websocketFactory = new WebSocketFactory()
                .setDualStackMode(DualStackMode.IPV4_ONLY);

        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .dns(dns)
                // more lenient timeouts (normally 10 seconds for these 3)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .hostnameVerifier(noopHostnameVerifier.isPresent() && noopHostnameVerifier.get()
                        ? (hostname, sslSession) -> true
                        : OkHostnameVerifier.INSTANCE);

        if (!proxyHost.isEmpty() && !proxyHost.equals("example.com")) {
            try {
                // This had to be set to empty string to avoid issue with basic auth
                // Reference: https://stackoverflow.com/questions/41806422/java-web-start-unable-to-tunnel-through-proxy-since-java-8-update-111
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost.trim(), proxyPort));
                httpClientBuilder = httpClientBuilder.proxy(proxy);

                ProxySettings proxySettings = websocketFactory.getProxySettings();
                proxySettings.setHost(proxyHost.trim());
                proxySettings.setPort(proxyPort);

                if (!authPassword.isEmpty()) {
                    String trimmedUsername = authUser.trim();
                    String trimmedPassword = authPassword.trim();

                    httpClientBuilder = httpClientBuilder.proxyAuthenticator((route, response) -> {
                        String credential = Credentials.basic(trimmedUsername, trimmedPassword);
                        return response.request().newBuilder().header("Proxy-Authorization", credential).build();
                    });

                    proxySettings.setCredentials(trimmedUsername, trimmedPassword);
                }
            } catch (Exception e) {
                DiscordSRV.error("Failed to generate a proxy from config options.", e);
            }
        }

        OkHttpClient httpClient = httpClientBuilder.build();

        // set custom RestAction failure handler
        Consumer<? super Throwable> defaultFailure = RestAction.getDefaultFailure();
        RestAction.setDefaultFailure(throwable -> {
            if (shuttingDown) {
                Throwable t = throwable;
                while (t != null) {
                    if (t instanceof InterruptedException || t instanceof InterruptedIOException) {
                        // Ignore interrupts when shutting down
                        return;
                    }
                    t = t.getCause();
                }
            }

            if (throwable instanceof HierarchyException) {
                DiscordSRV.error("DiscordSRV failed to perform an action due to being lower in hierarchy than the action's target: " + throwable.getMessage());
            } else if (throwable instanceof PermissionException) {
                DiscordSRV.error("DiscordSRV failed to perform an action because the bot is missing the " + ((PermissionException) throwable).getPermission().name() + " permission: " + throwable.getMessage());
            } else if (throwable instanceof RateLimitedException) {
                DiscordSRV.error("DiscordSRV encountered rate limiting. If you are running multiple DiscordSRV instances on the same token, this is considered API abuse and risks your server being IP banned from Discord. Make one bot per server.");
            } else if (throwable instanceof ErrorResponseException) {
                if (((ErrorResponseException) throwable).getErrorCode() == 50013) {
                    // Missing Permissions, too bad we don't know which one
                    DiscordSRV.error("DiscordSRV received a permission error response (50013) from Discord. Unfortunately the specific error isn't provided in that response.");
                    DiscordSRV.debug(Debug.JDA_REST_ACTIONS, throwable.getCause());
                    return;
                }

                Throwable cause = throwable.getCause();
                if (cause instanceof InterruptedIOException && jda != null) {
                    JDA.Status status = jda.getStatus();
                    if (status == JDA.Status.SHUTDOWN || status == JDA.Status.SHUTTING_DOWN) {
                        // Ignore InterruptedIOException's during shutdown, we can't hold up the server from stopping forever,
                        // so some requests are cancelled during shutdown. Logging errors for those request failures isn't important.
                        return;
                    }
                }
                DiscordSRV.error("DiscordSRV encountered an unknown Discord error: " + throwable.getMessage());
            } else {
                DiscordSRV.error("DiscordSRV encountered an unknown exception: " + throwable.getMessage() + "\n" + ExceptionUtils.getStackTrace(throwable));
            }

            if (Debug.JDA_REST_ACTIONS.isVisible()) {
                Throwable cause = throwable.getCause();
                error(cause);
            }
        });

        File tokenFile = new File(getDataFolder(), ".token");
        String token;
        if (StringUtils.isNotBlank(System.getProperty("DISCORDSRV_TOKEN"))) {
            token = System.getProperty("DISCORDSRV_TOKEN");
            DiscordSRV.debug("Using bot token supplied from JVM property DISCORDSRV_TOKEN");
        } else if (StringUtils.isNotBlank(System.getenv("DISCORDSRV_TOKEN"))) {
            token = System.getenv("DISCORDSRV_TOKEN");
            DiscordSRV.debug("Using bot token supplied from environment variable DISCORDSRV_TOKEN");
        } else if (tokenFile.exists()) {
            try {
                token = FileUtils.readFileToString(tokenFile, StandardCharsets.UTF_8);
                DiscordSRV.debug("Using bot token supplied from " + tokenFile.getPath());
            } catch (IOException e) {
                error(".token file could not be read: " + e.getMessage());
                token = null;
            }
        } else {
            token = config.getString("BotToken");
            DiscordSRV.debug("Using bot token supplied from config");
        }

        if (StringUtils.isBlank(token) || "BOTTOKEN".equalsIgnoreCase(token)) {
            disablePlugin();
            error("No bot token has been set in the config; a bot token is required to connect to Discord.");
            invalidBotToken = true;
            return;
        } else if (token.length() < 59) {
            disablePlugin();
            error("An invalid length bot token (" + token.length() + ") has been set in the config; a valid bot token is required to connect to Discord."
                    + (token.length() == 32 ? " Did you copy the \"Client Secret\" instead of the \"Bot Token\" into the config?" : ""));
            invalidBotToken = true;
            return;
        } else {
            // remove invalid characters
            token = token.replaceAll("[^\\w\\d-_.]", "");
        }

        callbackThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(), pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("DiscordSRV - JDA Callback " + worker.getPoolIndex());
            return worker;
        }, null, true);

        final ThreadFactory gatewayThreadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - JDA Gateway").build();
        final ScheduledExecutorService gatewayThreadPool = Executors.newSingleThreadScheduledExecutor(gatewayThreadFactory);

        final ThreadFactory rateLimitThreadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - JDA Rate Limit").build();
        final ScheduledExecutorService rateLimitThreadPool = new ScheduledThreadPoolExecutor(5, rateLimitThreadFactory);

        // log in to discord
        if (config.getBooleanElse("EnablePresenceInformation", false)) {
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_PRESENCES);
            DiscordSRV.api.requireCacheFlag(CacheFlag.ACTIVITY);
            DiscordSRV.api.requireCacheFlag(CacheFlag.CLIENT_STATUS);
        }
        try {
            // see ApiManager for our default intents & cache flags
            jda = JDABuilder.create(api.getIntents())
                    // we disable anything that isn't enabled (everything is enabled by default)
                    .disableCache(Arrays.stream(CacheFlag.values()).filter(cacheFlag -> !api.getCacheFlags().contains(cacheFlag)).collect(Collectors.toList()))
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setCallbackPool(callbackThreadPool, false)
                    .setGatewayPool(gatewayThreadPool, true)
                    .setRateLimitPool(rateLimitThreadPool, true)
                    .setWebsocketFactory(websocketFactory)
                    .setHttpClient(httpClient)
                    .setAutoReconnect(true)
                    .setBulkDeleteSplittingEnabled(false)
                    .setEnableShutdownHook(false)
                    .setToken(token)
                    .addEventListeners(new DiscordBanListener())
                    .addEventListeners(new DiscordChatListener())
                    .addEventListeners(new DiscordConsoleListener())
                    .addEventListeners(new DiscordAccountLinkListener())
                    .addEventListeners(new DiscordDisconnectListener())
                    .addEventListeners(api)
                    .addEventListeners(groupSynchronizationManager)
                    .setContextEnabled(false)
                    .build();
            jda.awaitReady(); // let JDA be assigned as soon as we can, but wait until it's ready

            for (Guild guild : jda.getGuilds()) {
                guild.retrieveOwner(true).queue();
                guild.loadMembers()
                        .onSuccess(members -> DiscordSRV.debug("Loaded " + members.size() + " members in guild " + guild))
                        .onError(throwable -> DiscordSRV.error("Failed to retrieve members of guild " + guild, throwable))
                        .get(); // block DiscordSRV startup until members are loaded
            }
        } catch (LoginException e) {
            disablePlugin();
            if (e.getMessage().toLowerCase().contains("the provided token is invalid")) {
                invalidBotToken = true;
                DiscordDisconnectListener.printDisconnectMessage(true, "The bot token is invalid");
            } else {
                DiscordDisconnectListener.printDisconnectMessage(true, e.getMessage());
            }
            return;
        } catch (Exception e) {
            if (e instanceof IllegalStateException && e.getMessage().equals("Was shutdown trying to await status")) {
                // already logged by JDA
                return;
            }
            DiscordSRV.error("An unknown error occurred building JDA...", e);
            return;
        }

        // start presence updater thread
        if (presenceUpdater != null) {
            if (presenceUpdater.getState() != Thread.State.NEW) {
                presenceUpdater.interrupt();
                presenceUpdater = new PresenceUpdater();
            }
            SchedulerUtil.runTaskLater(this, () -> presenceUpdater.start(), 5 * 20);
        } else {
            presenceUpdater = new PresenceUpdater();
            presenceUpdater.start();
        }

        // start nickname updater thread
        if (nicknameUpdater != null) {
            if (nicknameUpdater.getState() != Thread.State.NEW) {
                nicknameUpdater.interrupt();
                nicknameUpdater = new NicknameUpdater();
            }
            SchedulerUtil.runTaskLater(this, () -> nicknameUpdater.start(), 5 * 20);
        } else {
            nicknameUpdater = new NicknameUpdater();
            nicknameUpdater.start();
        }

        // show warning if bot wasn't in any guilds
        if (jda.getGuilds().size() == 0) {
            DiscordSRV.error(LangUtil.InternalMessage.BOT_NOT_IN_ANY_SERVERS);
            DiscordSRV.error(jda.getInviteUrl(Permission.ADMINISTRATOR));
            return;
        }

        // see if console channel exists; if it does, tell user where it's been assigned & add console appender
        if (serverIsLog4jCapable) {
            TextChannel consoleChannel = getConsoleChannel();
            if (consoleChannel != null) {
                DiscordSRV.info(LangUtil.InternalMessage.CONSOLE_FORWARDING_ASSIGNED_TO_CHANNEL + " " + consoleChannel);

                consoleAppender = new ChannelLoggingHandler(() -> {
                    TextChannel textChannel = DiscordSRV.getPlugin().getConsoleChannel();
                    return textChannel != null && textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_READ, Permission.MESSAGE_WRITE) ? textChannel : null;
                }, config -> {
                    config.setUseCodeBlocks(config().getBooleanElse("DiscordConsoleChannelUseCodeBlocks", true));
                    config.setLoggerNamePadding(config().getIntElse("DiscordConsoleChannelPadding", 0));
                    Set<LogLevel> configuredLevels = config().getStringList("DiscordConsoleChannelLevels").stream()
                            .map(value -> value.toUpperCase(Locale.ROOT))
                            .map(s -> {
                                try {
                                    return LogLevel.valueOf(s);
                                } catch (IllegalArgumentException e) {
                                    DiscordSRV.error("Invalid console logging level '" + s + "', valid options are " + Arrays.stream(LogLevel.values()).map(LogLevel::name).collect(Collectors.joining(", ")));
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    config.setLogLevels(!configuredLevels.isEmpty() ? EnumSet.copyOf(configuredLevels) : EnumSet.noneOf(LogLevel.class));
                    config.mapLoggerName("net.minecraft.server.MinecraftServer", "Server");
                    config.mapLoggerNameFriendly("net.minecraft.server", s -> "Server/" + s);
                    config.mapLoggerNameFriendly("net.minecraft", s -> "Minecraft/" + s);
                    config.mapLoggerName("github.scarsz.discordsrv.dependencies.jda", s -> "DiscordSRV/JDA/" + s);
                    config.addTransformer(logItem -> true, s -> MessageUtil.strip(DiscordUtil.aggressiveStrip(s))); // strip formatting
                    config.addTransformer(logItem -> true, line -> {
                        for (Map.Entry<Pattern, String> entry : consoleRegexes.entrySet()) {
                            line = entry.getKey().matcher(line).replaceAll(entry.getValue());
                            if (StringUtils.isBlank(line)) return null;
                        }
                        return line;
                    });

                    BiFunction<String, LogItem, String> placeholders = (key, item) -> {
                        String name = config.padLoggerName(config.resolveLoggerName(item.getLogger()));
                        String timestamp = TimeUtil.consoleTimeStamp(item.getTimestamp());
                        String value = config().getString(key);

                        // avoid processing placeholders if config value is empty
                        if (StringUtils.isBlank(value)) {
                            return "";
                        }

                        return PlaceholderUtil.replacePlaceholdersToDiscord(value)
                                .replace("{date}", timestamp)
                                .replace("{datetime}", timestamp)
                                .replace("{name}", StringUtils.isNotBlank(name) ? " " + name : "")
                                .replace("{level}", config.padLevelName(item.getLevel().name()));
                    };
                    config.setPrefixer(item -> placeholders.apply("DiscordConsoleChannelPrefix", item));
                    config.setSuffixer(item -> placeholders.apply("DiscordConsoleChannelSuffix", item));
                }).attachLog4jLogging().schedule();
            } else {
                DiscordSRV.info(LangUtil.InternalMessage.NOT_FORWARDING_CONSOLE_OUTPUT.toString());
            }
        }

        reloadChannels();
        reloadRegexes();
        reloadRoleAliases();

        // schedule slash commands to be updated later when plugins are ready (once the server had started up completely)
        SchedulerUtil.runTask(this, () -> SchedulerUtil.runTaskAsynchronously(this, api::updateSlashCommands));

        // warn if the console channel is connected to a chat channel
        if (getMainTextChannel() != null && getConsoleChannel() != null && getMainTextChannel().getId().equals(getConsoleChannel().getId())) DiscordSRV.warning(LangUtil.InternalMessage.CONSOLE_CHANNEL_ASSIGNED_TO_LINKED_CHANNEL);

        // send server startup message
        SchedulerUtil.runTaskLater(this, () -> {
            DiscordUtil.queueMessage(
                    getOptionalTextChannel("status"),
                    PlaceholderUtil.replacePlaceholdersToDiscord(LangUtil.Message.SERVER_STARTUP_MESSAGE.toString()),
                    true
            );
        }, 20);

        // big warning about respect chat plugins
        if (!config().getBooleanElse("RespectChatPlugins", true)) DiscordSRV.warning(LangUtil.InternalMessage.RESPECT_CHAT_PLUGINS_DISABLED);

        // extra enabled check before doing bukkit api stuff
        if (!isEnabled()) return;

        // start server watchdog
        if (!SchedulerUtil.isFolia()) { // watchdog isn't useful on folia
            if (serverWatchdog != null && serverWatchdog.getState() != Thread.State.NEW) serverWatchdog.interrupt();
            serverWatchdog = new ServerWatchdog();
            serverWatchdog.start();
        }

        // start lag (tps) monitor
        if (!SchedulerUtil.isFolia()) // cannot monitor global lag on folia
            Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Lag(), 100L, 1L);

        // cancellation detector
        reloadCancellationDetector();

        // load account links
        if (JdbcAccountLinkManager.shouldUseJdbc()) {
            try {
                accountLinkManager = new JdbcAccountLinkManager();
                ((JdbcAccountLinkManager) accountLinkManager).migrateFile();
            } catch (SQLException e) {
                StringBuilder stringBuilder = new StringBuilder("JDBC account link backend failed to initialize: ");

                Throwable selected = e;
                do {
                    stringBuilder.append("\n").append("Caused by: ").append(selected instanceof UnknownHostException ? "UnknownHostException" : ExceptionUtils.getMessage(selected));
                    selected = selected.getCause();
                } while (selected != null);

                String message = stringBuilder.toString()
                        .replace(config.getString("Experiment_JdbcAccountLinkBackend"), "<jdbc url>")
                        .replace(config.getString("Experiment_JdbcUsername"), "<jdbc username>");
                if (!StringUtils.isEmpty(config.getString("Experiment_JdbcPassword"))) {
                    message = message.replace(config.getString("Experiment_JdbcPassword"), "");
                }

                for (String line : message.split("\n")) {
                    DiscordSRV.warning(line);
                }
                DiscordSRV.warning("Account link manager falling back to file backend");
                accountLinkManager = new AppendOnlyFileAccountLinkManager();
            }
        } else {
            accountLinkManager = new AppendOnlyFileAccountLinkManager();
        }
        Bukkit.getPluginManager().registerEvents(accountLinkManager, this);

        // register events
        new PlayerBanListener();
        new PlayerDeathListener();
        new PlayerJoinLeaveListener();
        try {
            Class.forName("org.bukkit.event.player.PlayerAdvancementDoneEvent");
            new PlayerAdvancementDoneListener();
        } catch (Exception ignored) {
            new PlayerAchievementsListener();
        }

        // register incompatible client manager
//        Bukkit.getPluginManager().registerEvents(incompatibleClientManager, this);
//        Bukkit.getMessenger().registerIncomingPluginChannel(this, "lunarclient:pm", incompatibleClientManager);

        // plugin hooks
        for (String hookClassName : new String[]{
                // chat plugins
                "github.scarsz.discordsrv.hooks.chat.ChattyChatHook",
                "github.scarsz.discordsrv.hooks.chat.ChattyV3ChatHook",
                "github.scarsz.discordsrv.hooks.chat.FancyChatHook",
                "github.scarsz.discordsrv.hooks.chat.HerochatHook",
                "github.scarsz.discordsrv.hooks.chat.NChatHook", // nChat Hook needs to work before LegendChat
                "github.scarsz.discordsrv.hooks.chat.LegendChatHook",
                "github.scarsz.discordsrv.hooks.chat.LunaChatHook",
                "github.scarsz.discordsrv.hooks.chat.TownyChatHook",
                "github.scarsz.discordsrv.hooks.chat.VentureChatHook",
                // vanish plugins
                "github.scarsz.discordsrv.hooks.vanish.EssentialsHook",
                "github.scarsz.discordsrv.hooks.vanish.PhantomAdminHook",
                "github.scarsz.discordsrv.hooks.vanish.SuperVanishHook",
                "github.scarsz.discordsrv.hooks.vanish.VanishNoPacketHook",
                // dynmap
                "github.scarsz.discordsrv.hooks.DynmapHook",
                // luckperms
                "github.scarsz.discordsrv.hooks.permissions.LuckPermsHook",
                // world hooks
                "github.scarsz.discordsrv.hooks.world.MultiverseCoreV4Hook",
                "github.scarsz.discordsrv.hooks.world.MultiverseCoreV5Hook"
        }) {
            try {
                Class<?> hookClass = Class.forName(hookClassName);

                PluginHook pluginHook = (PluginHook) hookClass.getDeclaredConstructor().newInstance();
                if (pluginHook.isEnabled()) {
                    DiscordSRV.info(LangUtil.InternalMessage.PLUGIN_HOOK_ENABLING.toString().replace("{plugin}", pluginHook.getPlugin().getName()));
                    Bukkit.getPluginManager().registerEvents(pluginHook, this);
                    try {
                        pluginHook.hook();
                        pluginHooks.add(pluginHook);
                    } catch (Throwable t) {
                        error("Failed to hook " + hookClassName, t);
                    }
                }
            } catch (Throwable e) {
                // ignore class not found errors
                if (!(e instanceof ClassNotFoundException) && !(e instanceof NoClassDefFoundError)) {
                    DiscordSRV.error("Failed to load " + hookClassName, e);
                }
            }
        }
        if (pluginHooks.stream().noneMatch(pluginHook -> pluginHook instanceof ChatHook)) {
            DiscordSRV.debug(Debug.UNCATEGORIZED, LangUtil.InternalMessage.NO_CHAT_PLUGIN_HOOKED.toString());

            try {
                Class.forName("io.papermc.paper.event.player.AsyncChatEvent");

                getServer().getPluginManager().registerEvents(new ModernPlayerChatListener(), this);
                modernChatEventAvailable = true;
            } catch (ClassNotFoundException ignored) {}

            boolean configOption = config().getBoolean("UseModernPaperChatEvent");

            @SuppressWarnings("deprecation") Warning warning = AsyncPlayerChatEvent.class.getAnnotation(Warning.class);
            boolean isWarning = warning != null && getServer().getWarningState().printFor(warning); // check if the event has a nag

            Runnable registerLegacy = () -> getServer().getPluginManager().registerEvents(new PlayerChatListener(), this);
            if (isWarning) {
                // There will be a nag

                if (!configOption) {
                    // ... and we haven't been told to use the new event, let's tell them

                    if (modernChatEventAvailable) {
                        warning("AsyncPlayerChatEvent will be registered because the UseModernPaperChatEvent config option is set to false");
                        warning("You should enable UseModernPaperChatEvent if your chat plugins have updated to using the new event");
                    } else {
                        warning("AsyncPlayerChatEvent has a nag but Paper's modern PlayerChatEvent is not available.");
                        warning("Your server platform's chat event isn't supported currently");
                    }
                    registerLegacy.run();
                }
            } else {
                // there won't be a nag
                registerLegacy.run();
            }

            debug(Debug.MINECRAFT_TO_DISCORD, "Modern PlayerChatEvent (Paper) is " + (modernChatEventAvailable ? "" : "not ") + "available");
        }

        //noinspection deprecation
        pluginHooks.add(new VanishHook() {
            @SuppressWarnings("deprecation")
            @Override
            public boolean isVanished(Player player) {
                boolean vanished = false;
                for (MetadataValue metadataValue : player.getMetadata("vanished")) {
                    if (metadataValue.asBoolean()) {
                        vanished = true;
                        break;
                    }
                }
                return vanished;
            }

            @Override
            public Plugin getPlugin() {
                return null;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        });
        if (PluginUtil.pluginHookIsEnabled("PlaceholderAPI")) {
            try {
                DiscordSRV.info(LangUtil.InternalMessage.PLUGIN_HOOK_ENABLING.toString().replace("{plugin}", "PlaceholderAPI"));
                SchedulerUtil.runTask(this, () -> {
                    try {
                        if (me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().findExpansionByIdentifier("discordsrv").isPresent()) {
                            getLogger().warning("The DiscordSRV PlaceholderAPI expansion is no longer required.");
                            getLogger().warning("The expansion is now integrated in DiscordSRV.");
                        }
                        new github.scarsz.discordsrv.hooks.PlaceholderAPIExpansion().register();
                    } catch (Throwable ignored) {
                        getLogger().severe("Failed to hook into PlaceholderAPI, please check your PlaceholderAPI version");
                    }
                });
            } catch (Exception e) {
                if (!(e instanceof ClassNotFoundException)) {
                    DiscordSRV.error("Failed to load PlaceholderAPI expansion", e);
                }
            }
        }

        // start channel topic updater
        if (channelTopicUpdater != null) {
            if (channelTopicUpdater.getState() != Thread.State.NEW) {
                channelTopicUpdater.interrupt();
                channelTopicUpdater = new ChannelTopicUpdater();
            }
        } else {
            channelTopicUpdater = new ChannelTopicUpdater();
        }
        channelTopicUpdater.start();

        // start channel updater
        if (channelUpdater != null) {
            if (channelUpdater.getState() != Thread.State.NEW) {
                channelUpdater.interrupt();
                channelUpdater = new ChannelUpdater();
            }
        } else {
            channelUpdater = new ChannelUpdater();
        }
        channelUpdater.start();

        // enable metrics
        if (!config().getBooleanElse("MetricsDisabled", false)) {
            Metrics bStats = new Metrics(this, 387);
            bStats.addCustomChart(new SimplePie("linked_channels", () -> String.valueOf(channels.size())));
            bStats.addCustomChart(new AdvancedPie("hooked_plugins", () -> new HashMap<String, Integer>(){{
                if (pluginHooks.size() == 0) {
                    put("none", 1);
                } else {
                    for (PluginHook hookedPlugin : pluginHooks) {
                        Plugin plugin = hookedPlugin.getPlugin();
                        if (plugin == null) continue;
                        put(plugin.getName(), 1);
                    }
                }
            }}));
            bStats.addCustomChart(new SingleLineChart("minecraft-discord_account_links", () -> accountLinkManager.getLinkedAccountCount()));
            bStats.addCustomChart(new SimplePie("server_language", () -> DiscordSRV.config().getLanguage().getName()));
            bStats.addCustomChart(new AdvancedPie("features", () -> new HashMap<String, Integer>() {{
                if (getConsoleChannel() != null) put("Console channel", 1);
                if (StringUtils.isNotBlank(config().getString("DiscordChatChannelPrefixRequiredToProcessMessage"))) put("Chatting prefix", 1);
                if (JdbcAccountLinkManager.shouldUseJdbc(true)) put("JDBC", 1);
                if (config().getBoolean("Experiment_MCDiscordReserializer_ToMinecraft")) put("Discord -> MC Reserializer", 1);
                if (config().getBoolean("Experiment_MCDiscordReserializer_ToDiscord")) put("MC -> Discord Reserializer", 1);
                if (config().getBoolean("Experiment_MCDiscordReserializer_InBroadcast")) put("Broadcast Reserializer", 1);
                if (config().getBoolean("Experiment_WebhookChatMessageDelivery")) put("Webhooks", 1);
                if (config().getMap("GroupRoleSynchronizationGroupsAndRolesToSync").values().stream().anyMatch(s -> s.toString().replace("0", "").length() > 0)) put("Group -> role synchronization", 1);
                if (config().getBoolean("Voice enabled")) put("Voice", 1);
                if (config().getBoolean("Require linked account to play.Enabled")) {
                    put("Require linked account to play", 1);
                    if (config().getBoolean("Require linked account to play.Subscriber role.Require subscriber role to join")) {
                        put("Required subscriber role to play", 1);
                    }
                }
            }}));
            bStats.addCustomChart(new SingleLineChart("atleast_1player_online", () -> PlayerUtil.getOnlinePlayers().isEmpty() ? 0 : 1));
            bStats.addCustomChart(new SimplePie("better_online_mode", () -> {
                boolean onlineMode = Bukkit.getOnlineMode();
                try {
                    Class<?> spigotConfig = Class.forName("org.spigotmc.SpigotConfig");
                    Field bungee = spigotConfig.getField("bungee");

                    if (bungee.getBoolean(null)) {
                        return "bungee";
                    }
                } catch (Throwable ignored) {}

                try {
                    Class<?> paperConfig = Class.forName("com.destroystokyo.paper.PaperConfig");
                    Field velocitySupport = paperConfig.getField("velocitySupport");
                    Field velocityOnlineMode = paperConfig.getField("velocityOnlineMode");

                    if (velocitySupport.getBoolean(null)
                            && velocityOnlineMode.getBoolean(null)) {
                        return "velocity";
                    }
                } catch (Throwable ignored) {}

                return onlineMode ? "online" : "offline";
            }));
            bStats.addCustomChart(new DrilldownPie("server_plugins", () -> {
                int pluginCount = Bukkit.getPluginManager().getPlugins().length;

                Map<String, Integer> count = new HashMap<>();
                count.put(String.valueOf(pluginCount), 1);

                String key;
                if (pluginCount <= 5) {
                    key = "1-5";
                } else if (pluginCount <= 10) {
                    key = "6-10";
                } else if (pluginCount <= 20) {
                    key = "11-20";
                } else if (pluginCount <= 50) {
                    key = "21-50";
                } else if (pluginCount <= 100) {
                    key = "51-100";
                } else {
                    key = ((int) (Math.floor(pluginCount / 100F) * 100F)) + "+";
                }

                Map<String, Map<String, Integer>> plugins = new HashMap<>();
                plugins.put(key, count);
                return plugins;
            }));
        }

        // metrics file deprecated since v1.18.1
        File metricsFile = new File(getDataFolder(), "metrics.json");
        if (metricsFile.exists() && !metricsFile.delete()) metricsFile.deleteOnExit();

        // start the group synchronization task
        if (isGroupRoleSynchronizationEnabled()) {
            int cycleTime = DiscordSRV.config().getInt("GroupRoleSynchronizationCycleTime") * 20 * 60;
            if (cycleTime < 20 * 60) cycleTime = 20 * 60;
            try {
                groupSynchronizationManager.resync(GroupSynchronizationManager.SyncDirection.AUTHORITATIVE, GroupSynchronizationManager.SyncCause.TIMER);
            } catch (Exception e) {
                error("Failed to resync\n" + ExceptionUtils.getMessage(e));
            }
            Bukkit.getPluginManager().registerEvents(groupSynchronizationManager, this);
            SchedulerUtil.runTaskTimerAsynchronously(this,
                    () -> groupSynchronizationManager.resync(
                            GroupSynchronizationManager.SyncDirection.AUTHORITATIVE,
                            GroupSynchronizationManager.SyncCause.TIMER
                    ),
                    cycleTime,
                    cycleTime
            );
        }

        voiceModule = new VoiceModule();

        PluginCommand discordCommand = getCommand("discord");
        if (discordCommand != null && discordCommand.getPlugin() != this) {
            DiscordSRV.warning("/discord command is being handled by plugin other than DiscordSRV. You must use /discordsrv instead.");
        }

        alertListener = new AlertListener();
        jda.addEventListener(alertListener);
        api.subscribe(alertListener);

        // set ready status
        if (jda.getStatus() == JDA.Status.CONNECTED) {
            isReady = true;
            api.callEvent(new DiscordReadyEvent());
        }
    }

    @Override
    public void onDisable() {
        shuttingDown = true;

        final long shutdownStartTime = System.currentTimeMillis();

        // prepare the shutdown message
        String shutdownFormat = LangUtil.Message.SERVER_SHUTDOWN_MESSAGE.toString();

        // Check if the format contains a placeholder (Takes long to do cause the server is shutting down)
        // need to run this on the main thread
        if (Pattern.compile("%[^%]+%").matcher(shutdownFormat).find()) {
            shutdownFormat = PlaceholderUtil.replacePlaceholdersToDiscord(shutdownFormat);
        }

        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - Shutdown").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
        try {
            String finalShutdownFormat = shutdownFormat;
            executor.invokeAll(Collections.singletonList(() -> {
                // set server shutdown topics if enabled
                if (config().getBoolean("ChannelTopicUpdaterChannelTopicsAtShutdownEnabled")) {
                    String time = TimeUtil.timeStamp();
                    String serverVersion = Bukkit.getBukkitVersion();
                    String totalPlayers = Integer.toString(getTotalPlayerCount());
                    String shutdownTimestamp = Long.toString(System.currentTimeMillis() / 1000);
                    DiscordUtil.setTextChannelTopic(
                            getMainTextChannel(),
                            LangUtil.Message.CHAT_CHANNEL_TOPIC_AT_SERVER_SHUTDOWN.toString()
                                    .replaceAll("%time%|%date%", time)
                                    .replace("%serverversion%", serverVersion)
                                    .replace("%totalplayers%", totalPlayers)
                                    .replace("%timestamp%", shutdownTimestamp)
                    );
                    DiscordUtil.setTextChannelTopic(
                            getConsoleChannel(),
                            LangUtil.Message.CONSOLE_CHANNEL_TOPIC_AT_SERVER_SHUTDOWN.toString()
                                    .replaceAll("%time%|%date%", time)
                                    .replace("%serverversion%", serverVersion)
                                    .replace("%totalplayers%", totalPlayers)
                                    .replace("%timestamp%", shutdownTimestamp)
                    );
                }

                for (ChannelUpdater.UpdaterChannel updaterChannel : getChannelUpdater().getUpdaterChannels()) {
                    updaterChannel.updateToShutdownFormat();
                }

                // we're no longer ready
                isReady = false;

                // unregister event listeners because of garbage reloading plugins
                HandlerList.unregisterAll(this);

                // shutdown scheduler tasks
                SchedulerUtil.cancelTasks(this);
                if (!SchedulerUtil.isFolia()) { // not implemented yet on folia
                    for (BukkitWorker activeWorker : Bukkit.getScheduler().getActiveWorkers()) {
                        if (activeWorker.getOwner().equals(this)) {
                            List<String> stackTrace = Arrays.stream(activeWorker.getThread().getStackTrace()).map(StackTraceElement::toString).collect(Collectors.toList());
                            warning("a DiscordSRV scheduler task still active during onDisable: " + stackTrace.remove(0));
                            debug(stackTrace);
                        }
                    }
                }

                // stop alerts
                if (alertListener != null) alertListener.unregister();

                // shut down voice module
                if (voiceModule != null) voiceModule.shutdown();

                // kill channel topic updater
                if (channelTopicUpdater != null) channelTopicUpdater.interrupt();

                // kill channel updater
                if (channelUpdater != null) channelUpdater.interrupt();

                // kill presence updater
                if (presenceUpdater != null) presenceUpdater.interrupt();

                // kill nickname updater
                if (nicknameUpdater != null) nicknameUpdater.interrupt();

                // kill server watchdog
                if (serverWatchdog != null) serverWatchdog.interrupt();

                // shutdown the update checker
                if (updateChecker != null) updateChecker.shutdown();

                // close cancellation detectors
                if (legacyCancellationDetector != null) legacyCancellationDetector.close();
                if (modernCancellationDetector != null) modernCancellationDetector.close();

                // shutdown the console appender
                if (consoleAppender != null) consoleAppender.shutdown();

                // remove the jda filter
                if (jdaFilter != null) {
                    try {
                        org.apache.logging.log4j.core.Logger logger = ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger());

                        Field configField = null;
                        Class<?> targetClass = logger.getClass();

                        // get a field named config or privateConfig from the logger class or any of it's super classes
                        while (targetClass != null) {
                            try {
                                configField = targetClass.getDeclaredField("config");
                                break;
                            } catch (NoSuchFieldException ignored) {}

                            try {
                                configField = targetClass.getDeclaredField("privateConfig");
                                break;
                            } catch (NoSuchFieldException ignored) {}

                            targetClass = targetClass.getSuperclass();
                        }

                        if (configField != null) {
                            if (!configField.isAccessible()) configField.setAccessible(true);

                            Object config = configField.get(logger);
                            Field configField2 = config.getClass().getDeclaredField("config");
                            if (!configField2.isAccessible()) configField2.setAccessible(true);

                            Object config2 = configField2.get(config);
                            if (config2 instanceof org.apache.logging.log4j.core.filter.Filterable) {
                                ((org.apache.logging.log4j.core.filter.Filterable) config2).removeFilter(jdaFilter);
                                jdaFilter = null;
                                debug("JdaFilter removed");
                            }
                        }
                    } catch (Throwable t) {
                        getLogger().warning("Could not remove JDA Filter: " + t.toString());
                    }
                }

                // Clear JDA listeners
                if (jda != null) jda.getEventManager().getRegisteredListeners().forEach(listener -> jda.getEventManager().unregister(listener));

                // send server shutdown message
                DiscordUtil.sendMessageBlocking(getOptionalTextChannel("status"), finalShutdownFormat, true);

                // try to shut down jda gracefully
                if (jda != null) {
                    CompletableFuture<Void> shutdownTask = new CompletableFuture<>();
                    jda.addEventListener(new ListenerAdapter() {
                        @Override
                        public void onShutdown(@NotNull ShutdownEvent event) {
                            shutdownTask.complete(null);
                        }
                    });
                    jda.shutdownNow();
                    jda = null;
                    try {
                        shutdownTask.get(5, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        getLogger().warning("JDA took too long to shut down, skipping");
                    }
                }

                if (callbackThreadPool != null) callbackThreadPool.shutdownNow();

                DiscordSRV.info(LangUtil.InternalMessage.SHUTDOWN_COMPLETED.toString()
                        .replace("{ms}", String.valueOf(System.currentTimeMillis() - shutdownStartTime))
                );

                return null;
            }), 15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            error(e);
        }
        executor.shutdownNow();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        Supplier<Boolean> handle = () -> commandManager.handle(sender, args[0], Arrays.stream(args).skip(1).toArray(String[]::new));
        if (!isEnabled()) {
            if (args.length > 0 && args[0].equalsIgnoreCase("debug")) return handle.get(); // allow using debug

            if (invalidBotToken) {
                sender.sendMessage(ChatColor.RED + "DiscordSRV is disabled: your bot token is invalid.");
                sender.sendMessage(ChatColor.RED + "Please enter a valid token into your config.yml " +
                        "(" + ChatColor.GRAY + "/plugins/DiscordSRV/config.yml" + ChatColor.RED + ")" +
                        " and restart your server to get DiscordSRV to work.");
            } else if (DiscordDisconnectListener.mostRecentCloseCode == CloseCode.DISALLOWED_INTENTS) {
                sender.sendMessage(ChatColor.RED + "DiscordSRV is disabled: your DiscordSRV bot is lacking required intents.");
                sender.sendMessage(ChatColor.RED + "Please check your server log " +
                        "(" + ChatColor.GRAY + "/logs/latest.log" + ChatColor.RED + ")" +
                        " for a extended error message during DiscordSRV's startup to get DiscordSRV to work.");
            } else {
                sender.sendMessage(ChatColor.RED + "DiscordSRV is disabled, check your server log " +
                        "(" + ChatColor.GRAY + "/logs/latest.log" + ChatColor.RED + ")" +
                        " for errors during DiscordSRV's startup to find out why");
            }
            return true;
        }

        if (args.length == 0) {
            return commandManager.handle(sender, null, new String[] {});
        } else {
            return handle.get();
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command bukkitCommand, @NotNull String alias, String[] args) {
        if (!isEnabled()) return Collections.emptyList();

        String command = args[0];
        String[] commandArgs = Arrays.stream(args).skip(1).toArray(String[]::new);

        if (command.equals(""))
            return new ArrayList<String>() {{
                for (Map.Entry<String, Method> command : getCommandManager().getCommands().entrySet())
                    if (GamePermissionUtil.hasPermission(sender, command.getValue().getAnnotation(github.scarsz.discordsrv.commands.Command.class).permission()))
                        add(command.getKey());
            }};
        if (commandArgs.length == 0)
            return new ArrayList<String>() {{
                for (Map.Entry<String, Method> commandPair : getCommandManager().getCommands().entrySet())
                    if (commandPair.getKey().toLowerCase().startsWith(command.toLowerCase()))
                        if (GamePermissionUtil.hasPermission(sender, commandPair.getValue().getAnnotation(github.scarsz.discordsrv.commands.Command.class).permission()))
                            add(commandPair.getKey());
            }};
        return null;
    }

    @SuppressWarnings("deprecation")
    public void reloadCancellationDetector() {
        if (legacyCancellationDetector != null) {
            legacyCancellationDetector.close();
            legacyCancellationDetector = null;
        }
        if (modernCancellationDetector != null) {
            modernCancellationDetector.close();
            modernCancellationDetector = null;
        }

        if (Debug.MINECRAFT_TO_DISCORD.isVisible()) {
            try {
                legacyCancellationDetector = new CancellationDetector<>(this, AsyncPlayerChatEvent.class, (listener, event) -> {
                    Plugin plugin = listener.getPlugin();
                    DiscordSRV.info("Plugin " + plugin + " cancelled AsyncPlayerChatEvent (Bukkit) "
                                            + "(author: " + event.getPlayer().getName()
                                            + " | message: " + event.getMessage() + ")");
                });

                try {
                    Class.forName("io.papermc.paper.event.player.AsyncChatEvent");

                    modernCancellationDetector = new CancellationDetector<>(
                            this,
                            io.papermc.paper.event.player.AsyncChatEvent.class,
                            (listener, event) -> {
                                Plugin plugin = listener.getPlugin();
                                DiscordSRV.info("Plugin " + plugin + " cancelled AsyncChatEvent (Paper) " +
                                                        "(author: " + event.getPlayer().getName() + ")");
                            });
                } catch (ClassNotFoundException ignored) {}

                DiscordSRV.debug(LangUtil.InternalMessage.CHAT_CANCELLATION_DETECTOR_ENABLED.toString());
            } catch (Throwable t) {
                DiscordSRV.error("Could not initialize cancellation detector(s)", t);
            }
        }
    }

    /**
     * Gets the alias for the given world
     *
     * @param world The name of the world to get the alias for
     * @return The world's alias or the provided string if no alias or supported WorldHook was found
     */
    public String getWorldAlias(String world) {
        WorldHook worldHook = pluginHooks.stream()
                .filter(hook -> hook instanceof WorldHook)
                .map(hook -> (WorldHook) hook)
                .findAny()
                .orElse(null);

        if (worldHook == null) return world;
        return worldHook.getWorldAlias(world);
    }

    @Deprecated
    public void processChatMessage(Player player, String message, String channel, boolean cancelled) {
        this.processChatMessage(player, message, channel, cancelled, null);
    }

    public void processChatMessage(Player player, String message, String channel, boolean cancelled, org.bukkit.event.Event event) {
        this.processChatMessage(
                player,
                MessageUtil.toComponent(message, true),
                channel,
                cancelled,
                event
        );
    }

    @Deprecated
    public void processChatMessage(Player player, Component message, String channel, boolean cancelled) {
        this.processChatMessage(player, message, channel, cancelled, null);
    }

    @SuppressWarnings("deprecation") // Display names are legacy, Spigot is supported
    public void processChatMessage(Player player, Component message, String channel, boolean cancelled, org.bukkit.event.Event event) {
        // log debug message to notify that a chat message was being processed
        debug(Debug.MINECRAFT_TO_DISCORD, "Chat message received, canceled: " + cancelled + ", channel: " + channel);

        if (player == null) {
            debug(Debug.MINECRAFT_TO_DISCORD, "Received chat message was from a null sender, not processing message");
            return;
        }

        // return if player doesn't have permission
        if (!GamePermissionUtil.hasPermission(player, "discordsrv.chat")) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord due to lack of in-game permission (discordsrv.chat)");
            return;
        }

        // return if mcMMO is enabled and message is from party or admin chat
        if (PluginUtil.pluginHookIsEnabled("mcMMO")) {
            if (player.hasMetadata("mcMMO: Player Data")) {
                boolean usingAdminChat = com.gmail.nossr50.api.ChatAPI.isUsingAdminChat(player);
                boolean usingPartyChat = com.gmail.nossr50.api.ChatAPI.isUsingPartyChat(player);
                if (usingAdminChat || usingPartyChat) {
                    debug(Debug.MINECRAFT_TO_DISCORD, "Not processing message because message was from " + (usingAdminChat ? "admin" : "party") + " chat");
                    return;
                }
            }
        }

        // return if event canceled
        if (config().getBooleanElse("RespectChatPlugins", true) && cancelled) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because the chat event was canceled");
            return;
        }

        // return if should not send in-game chat
        if (!config().getBoolean("DiscordChatChannelMinecraftToDiscord")) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because DiscordChatChannelMinecraftToDiscord is false");
            return;
        }

        // return if doesn't match prefix filter
        String prefix = config().getString("DiscordChatChannelPrefixRequiredToProcessMessage");
        boolean blacklist = config.getBoolean("DiscordChatChannelPrefixActsAsBlacklist");

        String legacy = MessageUtil.toLegacy(message);
        if (MessageUtil.strip(legacy).startsWith(prefix) == blacklist) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because " + (blacklist ? "the message started with \"" + prefix : "the message didn't start with \"" + prefix) + "\" (DiscordChatChannelPrefixRequiredToProcessMessage): \"" + message + "\"");
            return;
        }

        GameChatMessagePreProcessEvent preEvent = api.callEvent(new GameChatMessagePreProcessEvent(channel, message, player, event));
        if (preEvent.isCancelled()) {
            debug(Debug.MINECRAFT_TO_DISCORD, "GameChatMessagePreProcessEvent was cancelled, message send aborted");
            return;
        }
        channel = preEvent.getChannel(); // update channel from event in case any listeners modified it
        message = preEvent.getMessageComponent(); // update message from event in case any listeners modified it

        String userPrimaryGroup = VaultHook.getPrimaryGroup(player);
        boolean hasGoodGroup = StringUtils.isNotBlank(userPrimaryGroup);

        // capitalize the first letter of the user's primary group to look neater
        if (hasGoodGroup) userPrimaryGroup = userPrimaryGroup.substring(0, 1).toUpperCase() + userPrimaryGroup.substring(1);

        boolean reserializer = DiscordSRV.config().getBoolean("Experiment_MCDiscordReserializer_ToDiscord");
        boolean webhookMessageDelivery = config().getBoolean("Experiment_WebhookChatMessageDelivery");

        String discordMessageContent;
        if (reserializer) {
            discordMessageContent = MessageUtil.reserializeToDiscord(message);
        } else {
            discordMessageContent = MessageUtil.strip(MessageUtil.toLegacy(message));
        }

        // Modify the message's content with the declared Regexes
        if (webhookMessageDelivery) {
            discordMessageContent = processRegex(discordMessageContent);
            if (discordMessageContent == null) return;
        }

        if (config().getBoolean("DiscordChatChannelTranslateMentions")) {
            discordMessageContent = DiscordUtil.convertMentionsFromNames(discordMessageContent, getMainGuild());
        } else {
            discordMessageContent = discordMessageContent.replace("@", "@\u200B"); // zero-width space
        }

        String processedMessage = discordMessageContent;

        if (!webhookMessageDelivery) {
            // If webhook delivery is not enable, we add all the placeholders
            String username = player.getName();
            if (!reserializer) username = DiscordUtil.escapeMarkdown(username);

            String displayName = MessageUtil.strip(player.getDisplayName());

            // Replace the internal placeholders in the message pattern
            String discordMessagePattern = (hasGoodGroup
                    ? LangUtil.Message.CHAT_TO_DISCORD.toString()
                    : LangUtil.Message.CHAT_TO_DISCORD_NO_PRIMARY_GROUP.toString())
                    .replace("%displayname%", DiscordUtil.escapeMarkdown(displayName))
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%username%", username)
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%channelname%", channel != null ? channel.substring(0, 1).toUpperCase() + channel.substring(1) : "")
                    .replace("%primarygroup%", userPrimaryGroup)
                    .replace("%usernamenoescapes%", MessageUtil.strip(player.getName()))
                    .replace("%world%", player.getWorld().getName())
                    .replace("%worldalias%", MessageUtil.strip(getWorldAlias(player.getWorld().getName())));
            // Replace the PAPI placeholders in the message pattern
            discordMessagePattern = PlaceholderUtil.replacePlaceholdersToDiscord(discordMessagePattern, player);

            /*
            // Reserialize the message pattern, in the case the placeholders added more color codes.
            // DiscordSRV is not ready for this yet. Leaving this here for when that faithful day comes upon us.
            if (reserializer){
                discordMessagePattern =
                        MessageUtil.reserializeToDiscord(
                                //Serialize into Components
                                LegacyComponentSerializer.legacySection().deserialize(
                                        //Replace $ with §
                                        discordMessagePattern.replace(
                                                LegacyComponentSerializer.AMPERSAND_CHAR,
                                                LegacyComponentSerializer.SECTION_CHAR)
                                ));
            } else*/
            // Strip the final message from any rouge color/style codes.
            if (!reserializer) {
                discordMessagePattern = MessageUtil.strip(discordMessagePattern);
            }

            // Replace the message after to avoid replacing rouge PAPI placeholders inside of the message's content
            discordMessagePattern = discordMessagePattern
                    .replace("%message%", discordMessageContent);

            discordMessagePattern = processRegex(discordMessagePattern);
            if (discordMessagePattern == null) return;

            processedMessage = discordMessagePattern;
        }

        // Send the post process message event
        GameChatMessagePostProcessEvent postEvent = api.callEvent(new GameChatMessagePostProcessEvent(channel, processedMessage, player, preEvent.isCancelled(), event));
        if (postEvent.isCancelled()) {
            debug(Debug.MINECRAFT_TO_DISCORD, "GameChatMessagePostProcessEvent was cancelled, message send aborted");
            return;
        }
        channel = postEvent.getChannel(); // update channel from event in case any listeners modified it
        processedMessage = postEvent.getProcessedMessage(); // update message from event in case any listeners modified it

        // If the channel is null, replace it with the global channel
        if (channel == null) channel = getOptionalChannel("global");
        TextChannel destinationChannel = getDestinationTextChannelForGameChannelName(channel);

        if (!webhookMessageDelivery) {
            DiscordUtil.sendMessage(destinationChannel, processedMessage);
        } else {

            if (destinationChannel == null) {
                debug(Debug.MINECRAFT_TO_DISCORD, "Failed to find Discord channel to forward message from game channel " + channel);
                return;
            }

            if (!DiscordUtil.checkPermission(destinationChannel, Permission.MANAGE_WEBHOOKS)) {
                DiscordSRV.error("Couldn't deliver chat message as webhook because the bot lacks the \"Manage Webhooks\" permission.");
                return;
            }

            WebhookUtil.deliverMessage(destinationChannel, player, processedMessage);
        }
    }

    private String processRegex(String discordMessage) {
        for (Map.Entry<Pattern, String> entry : getGameRegexes().entrySet()) {
            discordMessage = entry.getKey().matcher(discordMessage).replaceAll(entry.getValue());
            if (StringUtils.isBlank(discordMessage)) {
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because it was cleared by a filter: " + entry.getKey().pattern());
                return null;
            }
        }
        return discordMessage;
    }

    @Deprecated
    public void broadcastMessageToMinecraftServer(String channel, String message, User author) {
        // apply placeholder API values
        Player authorPlayer = null;
        UUID authorLinkedUuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(author.getId());
        if (authorLinkedUuid != null) authorPlayer = Bukkit.getPlayer(authorLinkedUuid);

        message = PlaceholderUtil.replacePlaceholders(message, authorPlayer);

        broadcastMessageToMinecraftServer(channel, MessageUtil.toComponent(message), author);
    }

    public void broadcastMessageToMinecraftServer(String channel, Component message, User author) {
        ChatHook chatHook = pluginHooks.stream()
                .filter(hook -> hook instanceof ChatHook)
                .map(hook -> (ChatHook) hook)
                .findAny().orElse(null);

        if (chatHook == null || channel == null) {
            if (channel != null && !channel.equalsIgnoreCase("global")) return; // don't send messages for non-global channels with no plugin hooks
            DiscordGuildMessagePreBroadcastEvent preBroadcastEvent = api.callEvent(new DiscordGuildMessagePreBroadcastEvent
                    (author, channel, message, PlayerUtil.getOnlinePlayers()));
            message = preBroadcastEvent.getMessage();
            channel = preBroadcastEvent.getChannel();
            MessageUtil.sendMessage(preBroadcastEvent.getRecipients(), message);
            PlayerUtil.notifyPlayersOfMentions(null, MessageUtil.toLegacy(message));
        } else {
            chatHook.broadcastMessageToChannel(channel, message);

            // hacky fix to avoid api breakage :/
            message = message.replaceText(TextReplacementConfig.builder()
                    .match("%channelcolor%")
                    .replacement("")
                    .build());
        }

        api.callEvent(new DiscordGuildMessagePostBroadcastEvent(channel, message));

        if (DiscordSRV.config().getBoolean("DiscordChatChannelBroadcastDiscordMessagesToConsole")) {
            DiscordSRV.info(LangUtil.InternalMessage.CHAT + ": " + MessageUtil.strip(MessageUtil.toLegacy(message).replace("»", ">")));
        }
    }

    /**
     * Triggers a join message for the given player to be sent to Discord. Useful for fake join messages.
     *
     * @param player the player
     * @param joinMessage the join message (that is usually provided by Bukkit's {@link PlayerJoinEvent#getJoinMessage()})
     * @see #sendLeaveMessage(Player, String)
     */
    public void sendJoinMessage(Player player, String joinMessage) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        MessageFormat messageFormat = player.hasPlayedBefore()
                ? getMessageFromConfiguration("MinecraftPlayerJoinMessage")
                : getMessageFromConfiguration("MinecraftPlayerFirstJoinMessage");
        if (messageFormat == null || !messageFormat.isAnyContent()) {
            debug("Not sending join message due to it being disabled");
            return;
        }

        TextChannel textChannel = getOptionalTextChannel("join");
        if (textChannel == null) {
            DiscordSRV.debug("Not sending join message, text channel is null");
            return;
        }

        final String displayName = StringUtils.isNotBlank(player.getDisplayName()) ? MessageUtil.strip(player.getDisplayName()) : "";
        final String message = StringUtils.isNotBlank(joinMessage) ? joinMessage : "";
        final String name = player.getName();
        final String avatarUrl = getAvatarUrl(player);
        final String botAvatarUrl = DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
        String botName = getMainGuild() != null ? getMainGuild().getSelfMember().getEffectiveName() : DiscordUtil.getJda().getSelfUser().getName();

        BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
            if (content == null) return null;
            content = content
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%message%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(message) : message))
                    .replace("%username%", needsEscape ? DiscordUtil.escapeMarkdown(name) : name)
                    .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(displayName) : displayName)
                    .replace("%usernamenoescapes%", name)
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%embedavatarurl%", avatarUrl)
                    .replace("%botavatarurl%", botAvatarUrl)
                    .replace("%botname%", botName);
            content = DiscordUtil.translateEmotes(content, textChannel.getGuild());
            content = PlaceholderUtil.replacePlaceholdersToDiscord(content, player);
            return content;
        };

        Message discordMessage = translateMessage(messageFormat, translator);
        if (discordMessage == null) return;

        String webhookName = translator.apply(messageFormat.getWebhookName(), false);
        String webhookAvatarUrl = translator.apply(messageFormat.getWebhookAvatarUrl(), false);

        if (messageFormat.isUseWebhooks()) {
            WebhookUtil.deliverMessage(textChannel, webhookName, webhookAvatarUrl,
                    discordMessage.getContentRaw(), discordMessage.getEmbeds().stream().findFirst().orElse(null));
        } else {
            DiscordUtil.queueMessage(textChannel, discordMessage, true);
        }
    }

    /**
     * Triggers a leave message for the given player to be sent to Discord. Useful for fake leave messages.
     *
     * @param player the player
     * @param quitMessage the leave/quit message (that is usually provided by Bukkit's {@link PlayerQuitEvent#getQuitMessage()})
     * @see #sendJoinMessage(Player, String)
     */
    public void sendLeaveMessage(Player player, String quitMessage) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        MessageFormat messageFormat = getMessageFromConfiguration("MinecraftPlayerLeaveMessage");
        if (messageFormat == null || !messageFormat.isAnyContent()) {
            debug("Not sending leave message due to it being disabled");
            return;
        }

        TextChannel textChannel = getOptionalTextChannel("leave");
        if (textChannel == null) {
            DiscordSRV.debug("Not sending quit message, text channel is null");
            return;
        }

        final String displayName = StringUtils.isNotBlank(player.getDisplayName()) ? MessageUtil.strip(player.getDisplayName()) : "";
        final String message = StringUtils.isNotBlank(quitMessage) ? quitMessage : "";
        final String name = player.getName();

        String avatarUrl = getAvatarUrl(player);
        String botAvatarUrl = DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
        String botName = getMainGuild() != null ? getMainGuild().getSelfMember().getEffectiveName() : DiscordUtil.getJda().getSelfUser().getName();

        BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
            if (content == null) return null;
            content = content
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%message%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(message) : message))
                    .replace("%username%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(name) : name))
                    .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(displayName) : displayName)
                    .replace("%usernamenoescapes%", name)
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%embedavatarurl%", avatarUrl)
                    .replace("%botavatarurl%", botAvatarUrl)
                    .replace("%botname%", botName);
            content = DiscordUtil.translateEmotes(content, textChannel.getGuild());
            content = PlaceholderUtil.replacePlaceholdersToDiscord(content, player);
            return content;
        };

        Message discordMessage = translateMessage(messageFormat, translator);
        if (discordMessage == null) return;

        String webhookName = translator.apply(messageFormat.getWebhookName(), false);
        String webhookAvatarUrl = translator.apply(messageFormat.getWebhookAvatarUrl(), false);

        if (messageFormat.isUseWebhooks()) {
            WebhookUtil.deliverMessage(textChannel, webhookName, webhookAvatarUrl,
                    discordMessage.getContentRaw(), discordMessage.getEmbeds().stream().findFirst().orElse(null));
        } else {
            DiscordUtil.queueMessage(textChannel, discordMessage, true);
        }
    }

    public MessageFormat getMessageFromConfiguration(String key) {
        return MessageFormatResolver.getMessageFromConfiguration(config(), key);
    }

    @CheckReturnValue
    public static Message translateMessage(MessageFormat messageFormat, BiFunction<String, Boolean, String> translator) {
        MessageBuilder messageBuilder = new MessageBuilder();
        Optional.ofNullable(messageFormat.getContent()).map(content -> translator.apply(content, true))
                .filter(StringUtils::isNotBlank).ifPresent(messageBuilder::setContent);

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setAuthor(
                Optional.ofNullable(messageFormat.getAuthorName())
                        .map(content -> translator.apply(content, false)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getAuthorUrl())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getAuthorImageUrl())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null)
        );
        embedBuilder.setThumbnail(Optional.ofNullable(messageFormat.getThumbnailUrl())
                .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null));
        embedBuilder.setImage(Optional.ofNullable(messageFormat.getImageUrl())
                .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null));
        embedBuilder.setDescription(Optional.ofNullable(messageFormat.getDescription())
                .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null));
        embedBuilder.setTitle(
                Optional.ofNullable(messageFormat.getTitle()).map(content -> translator.apply(content, false)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getTitleUrl()).map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null)
        );
        embedBuilder.setFooter(
                Optional.ofNullable(messageFormat.getFooterText())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getFooterIconUrl())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null)
        );
        if (messageFormat.getFields() != null) messageFormat.getFields().forEach(field ->
                embedBuilder.addField(translator.apply(field.getName(), true), translator.apply(field.getValue(), true), field.isInline()));
        embedBuilder.setColor(messageFormat.getColorRaw());
        embedBuilder.setTimestamp(messageFormat.getTimestamp());
        if (!embedBuilder.isEmpty()) messageBuilder.setEmbeds(embedBuilder.build());

        return messageBuilder.isEmpty() ? null : messageBuilder.build();
    }

    public static String getAvatarUrl(String username, UUID uuid) {
        String avatarUrl = constructAvatarUrl(username, uuid, "");
        avatarUrl = PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl);
        return avatarUrl;
    }
    private static String getAvatarUrl(OfflinePlayer player) {
        if (player.isOnline()) {
            return getAvatarUrl(player.getPlayer());
        } else {
            String avatarUrl = constructAvatarUrl(player.getName(), player.getUniqueId(), "");
            avatarUrl = PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl, player);
            return avatarUrl;
        }
    }
    public static String getAvatarUrl(Player player) {
        String avatarUrl = constructAvatarUrl(player.getName(), player.getUniqueId(), NMSUtil.getTexture(player));
        avatarUrl = PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl, player);
        return avatarUrl;
    }
    private static String constructAvatarUrl(String username, UUID uuid, String texture) {
        boolean offline = uuid == null || PlayerUtil.uuidIsOffline(uuid);
        OfflinePlayer player = null;
        if (StringUtils.isNotBlank(username) && offline) {
            // resolve username to player/uuid
            //TODO resolve name to online uuid when offline player is present
            // (can't do it by calling Bukkit.getOfflinePlayer(username).getUniqueId() because bukkit just returns the offline-mode CraftPlayer)
            player = Bukkit.getOfflinePlayer(username);
            uuid = player.getUniqueId();
            offline = PlayerUtil.uuidIsOffline(uuid);
        }
        if (StringUtils.isBlank(username) && uuid != null) {
            // resolve uuid to player/username
            player = Bukkit.getOfflinePlayer(uuid);
            username = player.getName();
        }
        if (StringUtils.isBlank(texture) && player != null && player.isOnline()) {
            // grab texture placeholder from player if online
            texture = NMSUtil.getTexture(player.getPlayer());
        }

        String avatarUrl = DiscordSRV.config().getString("AvatarUrl");
        String defaultUrl = "https://crafthead.net/helm/{uuid-nodashes}/{size}#{texture}";
        String offlineUrl = "https://crafthead.net/helm/{username}/{size}#{texture}";

        if (StringUtils.isBlank(avatarUrl)) {
            avatarUrl = !offline ? defaultUrl : offlineUrl;
        }

        if (avatarUrl.contains("://crafatar.com/")) {
            avatarUrl = !offline ? defaultUrl : offlineUrl;
            DiscordSRV.config().setRuntimeValue("AvatarUrl", avatarUrl);

            DiscordSRV.warning("Your AvatarUrl config option uses crafatar.com, which no longer allows usage with Discord. An alternative provider will be used.");
            DiscordSRV.warning("You should set your AvatarUrl (in config.yml) to an empty string (\"\") to get rid of this warning.");
        }

        if (offline && (avatarUrl.contains("{uuid}") || avatarUrl.contains("{uuid-nodashes}")) && !offlineUuidAvatarUrlNagged) {
            DiscordSRV.error("Your AvatarUrl config option contains {uuid} or {uuid-nodashes} but this server is using offline UUIDs.");
            offlineUuidAvatarUrlNagged = true;
        }

        if (username.startsWith("*")) {
            // geyser adds * to beginning of it's usernames
            username = username.substring(1);
        }
        try {
            username = URLEncoder.encode(username, "utf8");
        } catch (UnsupportedEncodingException ignored) {}

        String usedBaseUrl = avatarUrl;
        avatarUrl = avatarUrl
                .replace("{texture}", texture != null ? texture : "")
                .replace("{username}", username)
                .replace("{uuid}", uuid != null ? uuid.toString() : "")
                .replace("{uuid-nodashes}", uuid.toString().replace("-", ""))
                .replace("{size}", "128");

        DiscordSRV.debug("Constructed avatar url: " + avatarUrl + " from " + usedBaseUrl);
        DiscordSRV.debug("Avatar url is for " + (offline ? "**offline** " : "") + "uuid: " + uuid + ". The texture is: " + texture);

        return avatarUrl;
    }

    public static int getLength(Message message) {
        StringBuilder content = new StringBuilder();
        content.append(message.getContentRaw());

        message.getEmbeds().stream().findFirst().ifPresent(embed -> {
            if (embed.getTitle() != null) {
                content.append(embed.getTitle());
            }
            if (embed.getDescription() != null) {
                content.append(embed.getDescription());
            }
            if (embed.getAuthor() != null) {
                content.append(embed.getAuthor().getName());
            }
            for (MessageEmbed.Field field : embed.getFields()) {
                content.append(field.getName()).append(field.getValue());
            }
        });

        return content.toString().replaceAll("[^A-z]", "").length();
    }

    public List<Role> getSelectedRoles(Member member) {
        List<Role> selectedRoles;
        List<String> discordRolesSelection = DiscordSRV.config().getStringList("DiscordChatChannelRolesSelection");
        // if we have a whitelist in the config
        if (DiscordSRV.config().getBoolean("DiscordChatChannelRolesSelectionAsWhitelist")) {
            selectedRoles = member.getRoles().stream()
                    .filter(role -> discordRolesSelection.contains(DiscordUtil.getRoleName(role)) || discordRolesSelection.contains(role.getId()))
                    .collect(Collectors.toList());
        } else { // if we have a blacklist in the settings
            selectedRoles = member.getRoles().stream()
                    .filter(role -> !(discordRolesSelection.contains(DiscordUtil.getRoleName(role)) || discordRolesSelection.contains(role.getId())))
                    .collect(Collectors.toList());
        }
        selectedRoles.removeIf(role -> StringUtils.isBlank(role.getName()));
        return selectedRoles;
    }

    public Role getTopSelectedRole(Member member) {
        List<Role> selectedRoles = getSelectedRoles(member);
        if (selectedRoles.isEmpty()) return null;
        return member.getRoles().stream()
                .filter(selectedRoles::contains)
                .findFirst().orElse(null);
    }

    public Map<String, String> getGroupSynchronizables() {
        HashMap<String, String> map = new HashMap<>();
        config.dget("GroupRoleSynchronizationGroupsAndRolesToSync").children().forEach(dynamic ->
                map.put(dynamic.key().convert().intoString(), dynamic.convert().intoString()));
        return map;
    }

    public Map<String, String> getCannedResponses() {
        Map<String, String> responses = new HashMap<>();
        config.dget("DiscordCannedResponses").children()
                .forEach(dynamic -> {
                    String trigger = dynamic.key().convert().intoString();
                    if (StringUtils.isEmpty(trigger)) {
                        DiscordSRV.debug("Skipping canned response with empty trigger");
                        return;
                    }
                    responses.put(trigger, dynamic.convert().intoString());
                });
        return responses;
    }

    private static File playerDataFolder = null;
    public static int getTotalPlayerCount() {
        if (playerDataFolder == null) return 0;
        File[] playerFiles = playerDataFolder.listFiles(f -> f.getName().endsWith(".dat"));
        return playerFiles != null ? playerFiles.length : 0;
    }

    /**
     * @return Whether DiscordSRV should disable it's update checker. Doing so is dangerous and can lead to
     * security vulnerabilities. You shouldn't use this.
     */
    public static boolean isUpdateCheckDisabled() {
        return System.getenv("NoUpdateChecks") != null || System.getProperty("NoUpdateChecks") != null ||
                config().getBooleanElse("UpdateCheckDisabled", false);
    }

    /**
     * @return Whether DiscordSRV group role synchronization has been enabled in the configuration.
     */
    public boolean isGroupRoleSynchronizationEnabled() {
        return isGroupRoleSynchronizationEnabled(true);
    }

    /**
     * @return Whether DiscordSRV group role synchronization has been enabled in the configuration.
     * @param checkPermissions whether to check if Vault is available
     */
    public boolean isGroupRoleSynchronizationEnabled(boolean checkPermissions) {
        if (checkPermissions && groupSynchronizationManager.getPermissions() == null) return false;
        final Map<String, String> groupsAndRolesToSync = config.getMap("GroupRoleSynchronizationGroupsAndRolesToSync");
        if (groupsAndRolesToSync.isEmpty()) return false;
        for (Map.Entry<String, String> entry : groupsAndRolesToSync.entrySet()) {
            final String group = entry.getKey();
            if (!group.isEmpty()) {
                final String roleId = entry.getValue();
                if (!(roleId.isEmpty() || roleId.replace("0", "").trim().isEmpty())) return true;
            }
        }
        return false;
    }

    public String getOptionalChannel(String name) {
        return getChannels().containsKey(name)
                ? name
                : getMainChatChannel();
    }
    public TextChannel getOptionalTextChannel(String gameChannel) {
        return getDestinationTextChannelForGameChannelName(getOptionalChannel(gameChannel));
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
    public AccountLinkManager getAccountLinkManager() {
        return this.accountLinkManager;
    }
}
