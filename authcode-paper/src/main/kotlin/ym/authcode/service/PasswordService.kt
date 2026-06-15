package ym.authcode.service

import at.favre.lib.crypto.bcrypt.BCrypt
import org.bukkit.plugin.java.JavaPlugin
import ym.authcode.scheduler.SchedulerAdapter
import java.util.concurrent.CompletableFuture

class PasswordService(
    private val plugin: JavaPlugin,
    private val scheduler: SchedulerAdapter
) {
    fun hash(password: String): CompletableFuture<String> {
        return supplyAsync {
            BCrypt.withDefaults().hashToString(12, password.toCharArray())
        }
    }

    fun verify(password: String, hash: String): CompletableFuture<Boolean> {
        return supplyAsync {
            BCrypt.verifyer().verify(password.toCharArray(), hash).verified
        }
    }

    private fun <T> supplyAsync(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        scheduler.runAsync {
            try {
                future.complete(block())
            } catch (throwable: Throwable) {
                plugin.logger.severe("Password operation failed: ${throwable.message}")
                throwable.printStackTrace()
                future.completeExceptionally(throwable)
            }
        }
        return future
    }
}
