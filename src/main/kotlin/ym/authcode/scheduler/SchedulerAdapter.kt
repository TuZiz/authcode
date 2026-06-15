package ym.authcode.scheduler

import org.bukkit.entity.Entity

interface SchedulerAdapter {
    fun runAsync(task: () -> Unit): TaskHandle

    fun runGlobal(task: () -> Unit): TaskHandle

    fun runGlobalDelayed(delayTicks: Long, task: () -> Unit): TaskHandle

    fun runAtEntity(entity: Entity, task: () -> Unit): TaskHandle

    fun runAtEntityDelayed(entity: Entity, delayTicks: Long, task: () -> Unit): TaskHandle

    fun cancelAll()
}

interface TaskHandle {
    fun cancel()
}

object NoopTaskHandle : TaskHandle {
    override fun cancel() {
    }
}
