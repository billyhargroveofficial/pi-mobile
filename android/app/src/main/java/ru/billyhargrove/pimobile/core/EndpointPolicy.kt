package ru.billyhargrove.pimobile.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** HTTPS endpoints never contain credentials; debug HTTP is loopback-only. */
object EndpointPolicy {
    const val DEFAULT_BASE_URL = "https://billyhargrove.ru"
    @JvmField val DEBUG_HTTP_HOSTS: Set<String> = java.util.Collections.unmodifiableSet(setOf("10.0.2.2", "localhost", "127.0.0.1"))
    enum class Result { OK, EMPTY, MALFORMED, UNSUPPORTED_SCHEME, CLEARTEXT_NOT_ALLOWED, HAS_CREDENTIALS, HAS_QUERY_OR_FRAGMENT, MISSING_HOST }
    @JvmStatic fun validate(input: String?, debuggable: Boolean): Result {
        if (input.isNullOrBlank()) return Result.EMPTY
        val candidate = withScheme(input.trim())
        if (candidate.any { it in " \n\t" }) return Result.MALFORMED
        val uri = try { URI(candidate) } catch (_: URISyntaxException) { return Result.MALFORMED }
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme != "http" && scheme != "https") return Result.UNSUPPORTED_SCHEME
        if (uri.host.isNullOrEmpty()) return Result.MISSING_HOST
        if (!uri.userInfo.isNullOrEmpty()) return Result.HAS_CREDENTIALS
        if (uri.query != null || uri.fragment != null) return Result.HAS_QUERY_OR_FRAGMENT
        if (scheme == "http" && (!debuggable || uri.host.lowercase(Locale.ROOT) !in DEBUG_HTTP_HOSTS)) return Result.CLEARTEXT_NOT_ALLOWED
        return Result.OK
    }
    @JvmStatic fun normalize(input: String?): String {
        val uri = try { URI(withScheme(input?.trim().orEmpty())) }
            catch (_: URISyntaxException) { throw IllegalArgumentException("Некорректный URL: $input") }
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        require(host.isNotEmpty() && scheme.isNotEmpty()) { "Некорректный URL: $input" }
        val port = if ((scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) -1 else uri.port
        return "$scheme://$host" + (if (port != -1) ":$port" else "") + uri.path.orEmpty().trimEnd('/')
    }
    @JvmStatic fun apiUrl(baseUrl: String?, relativePath: String?): String {
        val base = normalize(baseUrl)
        val path = relativePath?.trim().orEmpty()
        return if (path.isEmpty()) base else base + (if (path.startsWith('/')) path else "/$path")
    }
    @JvmStatic fun wsUrl(baseUrl: String?): String {
        val base = normalize(baseUrl)
        return (if (base.substringBefore("://") == "https") "wss" else "ws") + "://" + base.substringAfter("://") + "/ws"
    }
    private fun withScheme(input: String) = if (input.isNotEmpty() && "://" !in input) "https://$input" else input
}
