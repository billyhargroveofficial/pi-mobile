package ru.billyhargrove.pimobile.net

class ApiException @JvmOverloads constructor(message: String?, private val httpCode: Int = 0, cause: Throwable? = null) : java.io.IOException(message, cause) {
    fun httpCode() = httpCode
    val isAuthFailure: Boolean get() = httpCode == 401 || httpCode == 403
}
