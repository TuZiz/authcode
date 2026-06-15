package ym.authcode.cache

import ym.authcode.model.AuthState
import ym.authcode.scheduler.TaskHandle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AuthSessionCache {
    private val sessions = ConcurrentHashMap<UUID, AuthSession>()

    fun start(uuid: UUID) {
        sessions[uuid] = AuthSession(AuthState.CHECKING)
    }

    fun state(uuid: UUID): AuthState? {
        return sessions[uuid]?.state
    }

    fun setState(uuid: UUID, state: AuthState) {
        sessions.compute(uuid) { _, current ->
            (current ?: AuthSession(state)).copy(state = state)
        }
    }

    fun authenticate(uuid: UUID) {
        sessions.compute(uuid) { _, current ->
            current?.timeoutTask?.cancel()
            (current ?: AuthSession(AuthState.AUTHENTICATED)).copy(
                state = AuthState.AUTHENTICATED,
                timeoutTask = null
            )
        }
    }

    fun isLocked(uuid: UUID): Boolean {
        return sessions[uuid]?.state?.let { it != AuthState.AUTHENTICATED } ?: false
    }

    fun setInvitedCode(uuid: UUID, code: String) {
        sessions.compute(uuid) { _, current ->
            (current ?: AuthSession(AuthState.WAITING_REGISTER)).copy(invitedByCode = code)
        }
    }

    fun invitedCode(uuid: UUID): String? {
        return sessions[uuid]?.invitedByCode
    }

    fun increaseAttempts(uuid: UUID): Int {
        var attempts = 1
        sessions.compute(uuid) { _, current ->
            val next = (current?.loginAttempts ?: 0) + 1
            attempts = next
            (current ?: AuthSession(AuthState.NEED_LOGIN)).copy(loginAttempts = next)
        }
        return attempts
    }

    fun setTimeoutTask(uuid: UUID, task: TaskHandle) {
        sessions.compute(uuid) { _, current ->
            current?.timeoutTask?.cancel()
            (current ?: AuthSession(AuthState.CHECKING)).copy(timeoutTask = task)
        }
    }

    fun remove(uuid: UUID) {
        sessions.remove(uuid)?.timeoutTask?.cancel()
    }

    fun clear() {
        sessions.values.forEach { it.timeoutTask?.cancel() }
        sessions.clear()
    }
}

data class AuthSession(
    val state: AuthState,
    val invitedByCode: String? = null,
    val loginAttempts: Int = 0,
    val timeoutTask: TaskHandle? = null
)
