package ym.authcode.bootstrap

import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.cache.AuthSessionCache
import ym.authcode.cache.PremiumCache
import ym.authcode.command.RuntimePluginCommand
import ym.authcode.command.admin.AuthCodeCommand
import ym.authcode.command.admin.sub.CreateCodeSubCommand
import ym.authcode.command.admin.sub.DeleteCodeSubCommand
import ym.authcode.command.admin.sub.GuiSubCommand
import ym.authcode.command.admin.sub.IdentitySubCommand
import ym.authcode.command.admin.sub.InfoCodeSubCommand
import ym.authcode.command.admin.sub.ListCodeSubCommand
import ym.authcode.command.admin.sub.NameCheckSubCommand
import ym.authcode.command.admin.sub.PremiumSubCommand
import ym.authcode.command.admin.sub.RandomCodeSubCommand
import ym.authcode.command.admin.sub.ReloadSubCommand
import ym.authcode.command.player.ChangePasswordCommand
import ym.authcode.command.player.CodeCommand
import ym.authcode.command.player.LoginCommand
import ym.authcode.command.player.RegisterCommand
import ym.authcode.config.ConfigManager
import ym.authcode.gui.InviteGuiService
import ym.authcode.hook.PlaceholderHookRegistrar
import ym.authcode.lang.LangManager
import ym.authcode.listener.AuthLockListener
import ym.authcode.listener.IdentityDisplayListener
import ym.authcode.listener.PlayerJoinListener
import ym.authcode.listener.PlayerQuitListener
import ym.authcode.listener.ProxyAuthMessageListener
import ym.authcode.message.MessageService
import ym.authcode.platform.PlatformDetector
import ym.authcode.scheduler.FoliaSchedulerAdapter
import ym.authcode.scheduler.PaperSchedulerAdapter
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.service.AuthService
import ym.authcode.service.IdentityDisplayService
import ym.authcode.service.InviteCodeService
import ym.authcode.service.LockService
import ym.authcode.service.PasswordService
import ym.authcode.service.PlayerIdentityService
import ym.authcode.service.PremiumCheckService
import ym.authcode.service.ProxyNonceTracker
import ym.authcode.storage.Storage
import ym.authcode.storage.sqlite.SQLiteStorage
import java.util.Locale

class PluginBootstrap(
    private val plugin: JavaPlugin
) {
    private lateinit var configManager: ConfigManager
    private lateinit var langManager: LangManager
    private lateinit var scheduler: SchedulerAdapter
    private lateinit var messageService: MessageService
    private lateinit var storage: Storage
    private lateinit var sessionCache: AuthSessionCache
    private lateinit var identityService: PlayerIdentityService
    private lateinit var premiumCache: PremiumCache
    private lateinit var proxyNonceTracker: ProxyNonceTracker

    fun start() {
        configManager = ConfigManager(plugin)
        configManager.load()

        scheduler = if (PlatformDetector(plugin.server).isFolia()) {
            plugin.logger.info("Folia detected, using Folia scheduler adapter.")
            FoliaSchedulerAdapter(plugin)
        } else {
            plugin.logger.info("Using Paper scheduler adapter.")
            PaperSchedulerAdapter(plugin)
        }

        langManager = LangManager(plugin, configManager)
        langManager.load()
        messageService = MessageService(langManager, scheduler)

        sessionCache = AuthSessionCache()
        identityService = PlayerIdentityService()
        premiumCache = PremiumCache()
        proxyNonceTracker = ProxyNonceTracker()
        storage = SQLiteStorage(plugin, configManager, scheduler)

        storage.initialize().whenComplete { _, throwable ->
            if (throwable != null) {
                plugin.logger.severe("AuthCode database initialization failed: ${throwable.message}")
                scheduler.runGlobal { plugin.server.pluginManager.disablePlugin(plugin) }
                return@whenComplete
            }
            scheduler.runGlobal {
                registerRuntime()
                plugin.logger.info("AuthCode enabled.")
            }
        }
    }

    fun stop() {
        if (::sessionCache.isInitialized) {
            sessionCache.clear()
        }
        if (::identityService.isInitialized) {
            identityService.clear()
        }
        if (::premiumCache.isInitialized) {
            premiumCache.clear()
        }
        if (::proxyNonceTracker.isInitialized) {
            proxyNonceTracker.clear()
        }
        if (::storage.isInitialized) {
            storage.close()
        }
        if (::scheduler.isInitialized) {
            scheduler.cancelAll()
        }
    }

    private fun registerRuntime() {
        val inviteCodeService = InviteCodeService(storage, configManager)
        val inviteGuiService = InviteGuiService(plugin, configManager, inviteCodeService, messageService, scheduler)
        val passwordService = PasswordService(plugin, scheduler)
        val premiumCheckService = PremiumCheckService(plugin, configManager, premiumCache, scheduler)
        val lockService = LockService(configManager, sessionCache, messageService, scheduler)
        val identityDisplayService = IdentityDisplayService(configManager, messageService, scheduler)
        val authService = AuthService(
            plugin,
            configManager,
            storage,
            sessionCache,
            proxyNonceTracker,
            premiumCheckService,
            inviteCodeService,
            passwordService,
            identityService,
            identityDisplayService,
            lockService,
            messageService,
            scheduler
        )

        registerListeners(
            PlayerJoinListener(authService),
            PlayerQuitListener(lockService, identityService),
            AuthLockListener(configManager, sessionCache, messageService),
            IdentityDisplayListener(configManager, identityService, identityDisplayService),
            inviteGuiService
        )
        registerProxyChannel(authService)
        registerCommands(authService, inviteCodeService, inviteGuiService, premiumCheckService, identityDisplayService)
        registerPlaceholderHook(identityDisplayService)
    }

    private fun registerListeners(vararg listeners: Listener) {
        listeners.forEach { plugin.server.pluginManager.registerEvents(it, plugin) }
    }

    private fun registerProxyChannel(authService: AuthService) {
        val proxy = configManager.current().proxy
        if (!proxy.enabled || proxy.mode.name != "VELOCITY_PROXY_PLUGIN") {
            return
        }
        plugin.server.messenger.registerIncomingPluginChannel(
            plugin,
            proxy.channel,
            ProxyAuthMessageListener(authService)
        )
        plugin.logger.info("Registered AuthCode proxy channel: ${proxy.channel}")
    }

    private fun registerPlaceholderHook(identityDisplayService: IdentityDisplayService) {
        PlaceholderHookRegistrar.tryRegister(plugin, configManager, identityService, identityDisplayService)
    }

    private fun registerCommands(
        authService: AuthService,
        inviteCodeService: InviteCodeService,
        inviteGuiService: InviteGuiService,
        premiumCheckService: PremiumCheckService,
        identityDisplayService: IdentityDisplayService
    ) {
        registerCommand("code", CodeCommand(authService, messageService))
        registerCommand("reg", RegisterCommand(authService, messageService), aliases = listOf("register"))
        registerCommand("login", LoginCommand(authService, messageService), aliases = listOf("l"))
        registerCommand("changepass", ChangePasswordCommand(authService, messageService))

        val adminCommand = AuthCodeCommand(
            messageService,
            listOf(
                CreateCodeSubCommand(inviteCodeService, configManager, messageService),
                RandomCodeSubCommand(inviteCodeService, configManager, messageService),
                ListCodeSubCommand(inviteCodeService, messageService),
                InfoCodeSubCommand(inviteCodeService, messageService),
                DeleteCodeSubCommand(inviteCodeService, messageService),
                GuiSubCommand(inviteGuiService, messageService),
                ReloadSubCommand(
                    plugin,
                    configManager,
                    langManager,
                    messageService,
                    scheduler,
                    identityService,
                    identityDisplayService
                ),
                PremiumSubCommand(storage, messageService),
                IdentitySubCommand(identityService, identityDisplayService, storage, messageService),
                NameCheckSubCommand(
                    configManager,
                    premiumCheckService,
                    identityDisplayService,
                    storage,
                    messageService
                )
            )
        )
        registerCommand("authcode", adminCommand, adminCommand, aliases = listOf("acode"))
    }

    private fun registerCommand(
        name: String,
        executor: CommandExecutor,
        tabCompleter: TabCompleter? = null,
        aliases: List<String> = emptyList()
    ) {
        val command = plugin.getCommand(name)
        if (command == null) {
            plugin.server.commandMap.register(
                plugin.name.lowercase(Locale.ROOT),
                RuntimePluginCommand(name, aliases, executor, tabCompleter)
            )
            return
        }
        command.setExecutor(executor)
        if (tabCompleter != null) {
            command.tabCompleter = tabCompleter
        }
    }
}
