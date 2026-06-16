package ym.authcode.scheduler

import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentLinkedQueue

class PaperSchedulerAdapter(
    private val plugin: JavaPlugin
) : SchedulerAdapter {
    private val tasks = ConcurrentLinkedQueue<TaskHandle>()

    override fun runAsync(task: () -> Unit): TaskHandle {
        return track(
            PaperTaskHandle(
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    runSafely(task)
                }),
                plugin
            )
        )
    }

    override fun runGlobal(task: () -> Unit): TaskHandle {
        return track(
            PaperTaskHandle(
                plugin.server.scheduler.runTask(plugin, Runnable {
                    runSafely(task)
                }),
                plugin
            )
        )
    }

    override fun runGlobalDelayed(delayTicks: Long, task: () -> Unit): TaskHandle {
        return track(
            PaperTaskHandle(
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    runSafely(task)
                }, delayTicks.coerceAtLeast(1L)),
                plugin
            )
        )
    }

    override fun runAtEntity(entity: Entity, task: () -> Unit): TaskHandle {
        return runGlobal(task)
    }

    override fun runAtEntityDelayed(entity: Entity, delayTicks: Long, task: () -> Unit): TaskHandle {
        return runGlobalDelayed(delayTicks, task)
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
            plugin.logger.severe("Scheduled task failed: ${throwable.message}")
            throwable.printStackTrace()
        }
    }
}

private class PaperTaskHandle(
    private val task: BukkitTask,
    private val plugin: JavaPlugin
) : TaskHandle {
    override fun cancel() {
        runCatching {
            task.cancel()
        }.onFailure {
            plugin.logger.warning("Failed to cancel Paper scheduled task: ${it.message}")
        }
    }
}
