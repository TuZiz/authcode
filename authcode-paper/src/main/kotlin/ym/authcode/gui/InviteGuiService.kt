package ym.authcode.gui

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.config.ConfigManager
import ym.authcode.lang.LangKeys
import ym.authcode.message.MessageService
import ym.authcode.model.InviteCode
import ym.authcode.model.InviteCodeUse
import ym.authcode.scheduler.SchedulerAdapter
import ym.authcode.service.InviteCodeService
import ym.authcode.util.TimeParser
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class InviteGuiService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val inviteCodeService: InviteCodeService,
    private val messageService: MessageService,
    private val scheduler: SchedulerAdapter
) : Listener {
    private val guiConfig = InviteGuiConfig(plugin, configManager)
    private val itemFactory = GuiItemFactory(messageService)
    private val sessions = ConcurrentHashMap<UUID, GuiSession>()
    private val actions = ConcurrentHashMap<UUID, Map<Int, GuiClickAction>>()
    private val opening = ConcurrentHashMap.newKeySet<UUID>()

    fun openMain(player: Player) {
        if (!configManager.current().gui.enabled) {
            messageService.send(player, "admin.gui-disabled")
            return
        }
        scheduler.runAtEntity(player) {
            val screen = guiConfig.main()
            val inventory = Bukkit.createInventory(
                player,
                screen.rows * 9,
                messageService.renderRaw(screen.title)
            )
            val slotActions = mutableMapOf<Int, GuiClickAction>()
            renderStaticItems(inventory, screen, emptyMap(), slotActions)
            openInventory(player, inventory, GuiSession(GuiScreenType.MAIN), slotActions)
        }
    }

    fun openCodeList(player: Player, page: Int = 0) {
        if (!configManager.current().gui.enabled) {
            messageService.send(player, "admin.gui-disabled")
            return
        }
        inviteCodeService.list().whenComplete { codes, throwable ->
            if (throwable != null) {
                messageService.send(player, LangKeys.DATABASE_ERROR)
                return@whenComplete
            }
            scheduler.runAtEntity(player) {
                renderCodeList(player, codes.orEmpty(), page)
            }
        }
    }

    fun openPlayers(player: Player, code: String, page: Int = 0) {
        if (!configManager.current().gui.enabled) {
            messageService.send(player, "admin.gui-disabled")
            return
        }
        inviteCodeService.info(code).thenCombine(inviteCodeService.listUses(code)) { inviteCode, uses ->
            inviteCode to uses
        }.whenComplete { result, throwable ->
            if (throwable != null) {
                messageService.send(player, LangKeys.DATABASE_ERROR)
                return@whenComplete
            }
            val inviteCode = result?.first
            if (inviteCode == null) {
                messageService.send(player, "admin.code-not-found", mapOf("code" to code))
                return@whenComplete
            }
            scheduler.runAtEntity(player) {
                renderPlayerList(player, inviteCode, result.second, page)
            }
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[player.uniqueId] ?: return
        event.isCancelled = true
        if (event.rawSlot < 0 || event.rawSlot >= event.view.topInventory.size) {
            return
        }
        val action = actions[player.uniqueId]?.get(event.rawSlot) ?: return
        handleAction(player, session, action, event.click)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!sessions.containsKey(player.uniqueId)) {
            return
        }
        if (event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (opening.contains(player.uniqueId)) {
            return
        }
        sessions.remove(player.uniqueId)
        actions.remove(player.uniqueId)
    }

    private fun renderCodeList(player: Player, codes: List<InviteCode>, requestedPage: Int) {
        val screen = guiConfig.codeList()
        val contentSlots = screen.contentSlots()
        val pages = pageCount(codes.size, contentSlots.size)
        val page = requestedPage.coerceIn(0, pages - 1)
        val variables = pageVariables(page, pages, codes.size)
        val inventory = Bukkit.createInventory(
            player,
            screen.rows * 9,
            messageService.renderRaw(screen.title, variables)
        )
        val slotActions = mutableMapOf<Int, GuiClickAction>()
        renderStaticItems(inventory, screen, variables, slotActions)
        val itemConfig = screen.codeItem
        if (itemConfig != null && contentSlots.isNotEmpty()) {
            codes.drop(page * contentSlots.size)
                .take(contentSlots.size)
                .forEachIndexed { index, code ->
                    val slot = contentSlots[index]
                    inventory.setItem(slot, itemFactory.create(itemConfig, codeVariables(code)))
                    slotActions[slot] = GuiClickAction.CodeItem(
                        code = code.code,
                        leftAction = itemConfig.leftClickAction ?: itemConfig.action,
                        rightAction = itemConfig.rightClickAction
                    )
                }
        }
        openInventory(player, inventory, GuiSession(GuiScreenType.CODE_LIST, page), slotActions)
    }

    private fun renderPlayerList(player: Player, code: InviteCode, uses: List<InviteCodeUse>, requestedPage: Int) {
        val screen = guiConfig.playerInfo()
        val contentSlots = screen.contentSlots()
        val pages = pageCount(uses.size, contentSlots.size)
        val page = requestedPage.coerceIn(0, pages - 1)
        val variables = codeVariables(code) + pageVariables(page, pages, uses.size)
        val inventory = Bukkit.createInventory(
            player,
            screen.rows * 9,
            messageService.renderRaw(screen.title, variables)
        )
        val slotActions = mutableMapOf<Int, GuiClickAction>()
        renderStaticItems(inventory, screen, variables, slotActions)
        val itemConfig = screen.playerItem
        if (itemConfig != null && contentSlots.isNotEmpty()) {
            uses.drop(page * contentSlots.size)
                .take(contentSlots.size)
                .forEachIndexed { index, use ->
                    val slot = contentSlots[index]
                    val itemVariables = variables + playerVariables(use)
                    inventory.setItem(
                        slot,
                        itemFactory.create(
                            itemConfig,
                            itemVariables,
                            SkullOwner(use.playerUuid, use.playerName)
                        )
                    )
                    slotActions[slot] = GuiClickAction.Static(itemConfig.leftClickAction ?: itemConfig.action)
                }
        }
        openInventory(player, inventory, GuiSession(GuiScreenType.PLAYER_LIST, page, code.code), slotActions)
    }

    private fun renderStaticItems(
        inventory: Inventory,
        screen: GuiScreenConfig,
        variables: Map<String, String>,
        slotActions: MutableMap<Int, GuiClickAction>
    ) {
        screen.layout.forEachIndexed { row, line ->
            line.forEachIndexed { column, char ->
                if (char == screen.contentChar) {
                    return@forEachIndexed
                }
                val itemConfig = screen.items[char] ?: return@forEachIndexed
                val slot = row * 9 + column
                inventory.setItem(slot, itemFactory.create(itemConfig, variables))
                val action = itemConfig.leftClickAction ?: itemConfig.action
                if (action != null || itemConfig.rightClickAction != null) {
                    slotActions[slot] = GuiClickAction.Static(action, itemConfig.rightClickAction)
                }
            }
        }
    }

    private fun handleAction(player: Player, session: GuiSession, action: GuiClickAction, click: ClickType) {
        val actionName = when (action) {
            is GuiClickAction.Static -> action.forClick(click)
            is GuiClickAction.CodeItem -> action.forClick(click)
        } ?: return
        when (actionName.lowercase(Locale.ROOT)) {
            "code-list", "back-code-list" -> openCodeList(player)
            "back-main" -> openMain(player)
            "create-default" -> createDefaultCode(player)
            "view-players" -> if (action is GuiClickAction.CodeItem) openPlayers(player, action.code)
            "delete-code" -> if (action is GuiClickAction.CodeItem) deleteCode(player, action.code, session.page)
            "previous-page" -> openRelativePage(player, session, -1)
            "next-page" -> openRelativePage(player, session, 1)
            "close" -> player.closeInventory()
        }
    }

    private fun createDefaultCode(player: Player) {
        inviteCodeService.createDefault(player.name).whenComplete { code, throwable ->
            when {
                throwable != null -> messageService.send(player, LangKeys.DATABASE_ERROR)
                else -> messageService.send(player, "admin.code-random-created", mapOf("code" to code))
            }
            openCodeList(player)
        }
    }

    private fun deleteCode(player: Player, code: String, page: Int) {
        if (!player.hasPermission("authcode.admin") && !player.hasPermission("authcode.admin.delete")) {
            messageService.send(player, LangKeys.NO_PERMISSION)
            return
        }
        inviteCodeService.delete(code).whenComplete { deleted, throwable ->
            when {
                throwable != null -> messageService.send(player, LangKeys.DATABASE_ERROR)
                deleted == true -> messageService.send(player, "admin.code-deleted", mapOf("code" to code))
                else -> messageService.send(player, "admin.code-not-found", mapOf("code" to code))
            }
            openCodeList(player, page)
        }
    }

    private fun openRelativePage(player: Player, session: GuiSession, offset: Int) {
        val nextPage = (session.page + offset).coerceAtLeast(0)
        when (session.type) {
            GuiScreenType.MAIN -> Unit
            GuiScreenType.CODE_LIST -> openCodeList(player, nextPage)
            GuiScreenType.PLAYER_LIST -> session.code?.let { openPlayers(player, it, nextPage) }
        }
    }

    private fun openInventory(
        player: Player,
        inventory: Inventory,
        session: GuiSession,
        slotActions: Map<Int, GuiClickAction>
    ) {
        opening.add(player.uniqueId)
        try {
            player.openInventory(inventory)
        } finally {
            opening.remove(player.uniqueId)
        }
        sessions[player.uniqueId] = session
        actions[player.uniqueId] = slotActions
    }

    private fun codeVariables(code: InviteCode): Map<String, String> {
        val now = System.currentTimeMillis()
        return mapOf(
            "code" to code.code,
            "max" to code.maxUses.toString(),
            "remaining" to code.remainingUses().toString(),
            "used" to code.usedCount.toString(),
            "expired" to bool(code.isExpired(now)),
            "enabled" to bool(code.enabled),
            "creator" to code.createdBy,
            "created" to TimeParser.format(code.createdTime, never()),
            "expire" to TimeParser.format(code.expireTime, never())
        )
    }

    private fun playerVariables(use: InviteCodeUse): Map<String, String> {
        return mapOf(
            "code" to use.code,
            "player" to use.playerName,
            "uuid" to use.playerUuid.toString(),
            "ip" to use.ip,
            "use_time" to TimeParser.format(use.useTime, never())
        )
    }

    private fun pageVariables(page: Int, pages: Int, total: Int): Map<String, String> {
        return mapOf(
            "page" to (page + 1).toString(),
            "pages" to pages.toString(),
            "total" to total.toString()
        )
    }

    private fun pageCount(total: Int, pageSize: Int): Int {
        if (pageSize <= 0) {
            return 1
        }
        return max(1, (total + pageSize - 1) / pageSize)
    }

    private fun bool(value: Boolean): String {
        return messageService.plain(if (value) "admin.boolean-true" else "admin.boolean-false")
    }

    private fun never(): String {
        return messageService.plain("admin.time-never")
    }
}

private data class GuiSession(
    val type: GuiScreenType,
    val page: Int = 0,
    val code: String? = null
)

private enum class GuiScreenType {
    MAIN,
    CODE_LIST,
    PLAYER_LIST
}

private sealed interface GuiClickAction {
    data class Static(
        val leftAction: String?,
        val rightAction: String? = null
    ) : GuiClickAction

    data class CodeItem(
        val code: String,
        val leftAction: String?,
        val rightAction: String?
    ) : GuiClickAction
}

private fun GuiClickAction.Static.forClick(click: ClickType): String? {
    return if (click.isRightClick) rightAction ?: leftAction else leftAction
}

private fun GuiClickAction.CodeItem.forClick(click: ClickType): String? {
    return if (click.isRightClick) rightAction ?: leftAction else leftAction
}
