package com.joaohickmann.reservafiti

sealed class LoginStatus
object LoggedOut : LoginStatus()
object LogginIn : LoginStatus()
data class LoggedIn(
    val email: String,
    val senha: String,
    val token: String,
    val idFilial: Int
) : LoginStatus()