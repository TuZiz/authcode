package ym.authcode.model

enum class AuthState {
    CHECKING,
    NEED_CODE,
    WAITING_REGISTER,
    NEED_LOGIN,
    AUTHENTICATED
}
