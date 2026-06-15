package ym.authcode.scheduler

import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

class FoliaSchedulerAdapter(
    private val plugin: JavaPlugin
) : SchedulerAdapter {
    private val tasks = ConcurrentLinkedQueue<TaskHandle>()
    private val asyncScheduler: Any by lazy {
        plugin.server.javaClass.getMethod("getAsyncScheduler").invoke(plugin.server)
    }
    private val globalRegionScheduler: Any by lazy {
        plugin.server.javaClass.getMethod("getGlobalRegionScheduler").invoke(plugin.server)
    }

    override fun runAsync(task: () -> Unit): TaskHandle {
        return track(invokeConsumerScheduler(asyncScheduler, "runNow", task))
    }

    override fun runGlobal(task: () -> Unit): TaskHandle {
        return track(invokeConsumerScheduler(globalRegionScheduler, "run", task))
    }

    override fun runGlobalDelayed(delayTicks: Long, task: () -> Unit): TaskHandle {
        val method = findMethod(globalRegionScheduler.javaClass, "runDelayed", 3)
        val handle = method.invoke(
            globalRegionScheduler,
            plugin,
            Consumer<Any> { runSafely(task) },
            delayTicks.coerceAtLeast(1L)
        )
        return track(ReflectionTaskHandle(handle))
    }

    override fun runAtEntity(entity: Entity, task: () -> Unit): TaskHandle {
        val scheduler = entity.javaClass.getMethod("getScheduler").invoke(entity)
        val runMethod = scheduler.javaClass.methods.firstOrNull {
            it.name == "run" && it.parameterCount == 3
        }
        if (runMethod != null) {
            val handle = runMethod.invoke(
                scheduler,
                plugin,
                Consumer<Any> { runSafely(task) },
                Runnable {}
            )
            return track(ReflectionTaskHandle(handle))
        }
        return executeEntityScheduler(scheduler, 1L, task)
    }

    override fun runAtEntityDelayed(entity: Entity, delayTicks: Long, task: () -> Unit): TaskHandle {
        val scheduler = entity.javaClass.getMethod("getScheduler").invoke(entity)
        val delayedMethod = scheduler.javaClass.methods.firstOrNull {
            it.name == "runDelayed" && it.parameterCount == 4
        }
        if (delayedMethod != null) {
            val handle = delayedMethod.invoke(
                scheduler,
                plugin,
                Consumer<Any> { runSafely(task) },
                Runnable {},
                delayTicks.coerceAtLeast(1L)
            )
            return track(ReflectionTaskHandle(handle))
        }
        return executeEntityScheduler(scheduler, delayTicks.coerceAtLeast(1L), task)
    }

    override fun cancelAll() {
        tasks.forEach { it.cancel() }
        tasks.clear()
    }

    private fun invokeConsumerScheduler(target: Any, methodName: String, task: () -> Unit): TaskHandle {
        val method = findMethod(target.javaClass, methodName, 2)
        val handle = method.invoke(target, plugin, Consumer<Any> { runSafely(task) })
        return ReflectionTaskHandle(handle)
    }

    private fun executeEntityScheduler(scheduler: Any, delayTicks: Long, task: () -> Unit): TaskHandle {
        val method = scheduler.javaClass.methods.firstOrNull {
            it.name == "execute" && it.parameterCount == 4
        } ?: return NoopTaskHandle
        method.invoke(scheduler, plugin as Plugin, Runnable { runSafely(task) }, Runnable {}, delayTicks)
        return NoopTaskHandle
    }

    private fun findMethod(type: Class<*>, name: String, parameterCount: Int): Method {
        return type.methods.first { it.name == name && it.parameterCount == parameterCount }
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

private class ReflectionTaskHandle(
    private val handle: Any?
) : TaskHandle {
    override fun cancel() {
        if (handle == null) {
            return
        }
        handle.javaClass.methods.firstOrNull {
            it.name == "cancel" && it.parameterCount == 0
        }?.invoke(handle)
    }
}
