package ym.authcode.scheduler

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

class FoliaSchedulerAdapter(
    private val plugin: JavaPlugin
) : SchedulerAdapter {
    private val tasks = ConcurrentLinkedQueue<TaskHandle>()
    private val asyncScheduler: AsyncScheduler by lazy {
        plugin.server.asyncScheduler
    }
    private val globalRegionScheduler: GlobalRegionScheduler by lazy {
        plugin.server.globalRegionScheduler
    }

    override fun runAsync(task: () -> Unit): TaskHandle {
        return track(
            FoliaTaskHandle(
                asyncScheduler.runNow(plugin, Consumer<ScheduledTask> { runSafely(task) }),
                plugin
            )
        )
    }

    override fun runGlobal(task: () -> Unit): TaskHandle {
        return track(
            FoliaTaskHandle(
                globalRegionScheduler.run(plugin, Consumer<ScheduledTask> { runSafely(task) }),
                plugin
            )
        )
    }

    override fun runGlobalDelayed(delayTicks: Long, task: () -> Unit): TaskHandle {
        return track(
            FoliaTaskHandle(
                globalRegionScheduler.runDelayed(
                    plugin,
                    Consumer<ScheduledTask> { runSafely(task) },
                    delayTicks.coerceAtLeast(1L)
                ),
                plugin
            )
        )
    }

    override fun runAtEntity(entity: Entity, task: () -> Unit): TaskHandle {
        return track(
            FoliaTaskHandle(
                entity.scheduler.run(
                    plugin,
                    Consumer<ScheduledTask> { runSafely(task) },
                    Runnable {}
                ),
                plugin,
            )
        )
    }

    override fun runAtEntityDelayed(entity: Entity, delayTicks: Long, task: () -> Unit): TaskHandle {
        return track(
            FoliaTaskHandle(
                entity.scheduler.runDelayed(
                    plugin,
                    Consumer<ScheduledTask> { runSafely(task) },
                    Runnable {},
                    delayTicks.coerceAtLeast(1L)
                ),
                plugin,
            )
        )
    }

    override fun cancelAll() {
        tasks.forEach { it.cancel() }
        tasks.clear()
    }

    private fun track(handle: TaskHandle): TaskHandle {
        tasks.add(handle)
        return handle
    }

    private fun runSafely(task: () -> Unit) {
        try {
            task()
        } catch (throwable: Throwable) {
            plugin.logger.severe("Folia scheduled task failed: ${throwable.message}")
            throwable.printStackTrace()
        }
    }
}

private class FoliaTaskHandle(
    private val task: ScheduledTask?,
    private val plugin: JavaPlugin
) : TaskHandle {
    override fun cancel() {
        val scheduledTask = task ?: return
        runCatching {
            scheduledTask.cancel()
        }.onFailure {
            plugin.logger.warning("Failed to cancel Folia scheduled task: ${it.message}")
        }
    }
}
