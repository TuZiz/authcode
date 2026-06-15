package ym.authcode.model

enum class AuthState {
    CHECKING,
    CHECKING_PROXY,
    NEED_CODE,
    WAITING_REGISTER,
    NEED_LOGIN,
    AUTHENTICATED
}
