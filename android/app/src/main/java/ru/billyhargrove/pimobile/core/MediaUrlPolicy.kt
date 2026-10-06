package ru.billyhargrove.pimobile.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Authorization may follow only same-origin references, after path canonicalization. */
object MediaUrlPolicy {
    @JvmStatic fun resolve(baseUrl: String?, rawUrl: String?): String? {
        if (baseUrl.isNullOrBlank() || rawUrl == null) return null
        val raw = rawUrl.trim()
        if (raw.isEmpty()) return null
        val base = try { URI(EndpointPolicy.normalize(baseUrl)) } catch (_: Exception) { return null }
        val baseScheme = base.scheme.lowercase(Locale.ROOT)
        val baseHost = base.host?.lowercase(Locale.ROOT).orEmpty()
        if (baseHost.isEmpty()) return null
        val origin = try { URI(baseScheme, null, baseHost, base.port, "", null, null) } catch (_: URISyntaxException) { return null }
        val target = try { if (raw.startsWith('/')) origin.resolve(raw) else base.resolve(raw) } catch (_: IllegalArgumentException) { return null }
        val scheme = target.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme !in setOf("http", "https") || scheme != baseScheme || !target.userInfo.isNullOrEmpty()) return null
        val host = target.host?.lowercase(Locale.ROOT).orEmpty()
        if (host != baseHost || effectivePort(target) != effectivePort(base) || target.path.isNullOrEmpty()) return null
        val path = removeDotSegments(target.path).ifEmpty { "/" }
        return try { URI(scheme, null, host, target.port, path, target.query, null).toString() } catch (_: URISyntaxException) { null }
    }
    /** RFC3986 section5.2.4, including duplicate slashes and trailing dot segments. */
    @JvmStatic fun removeDotSegments(path: String?): String {
        var input = path.orEmpty()
        val output = StringBuilder(input.length)
        fun removeLast() { val slash = output.lastIndexOf("/"); output.setLength(if (slash < 0) 0 else slash) }
        while (input.isNotEmpty()) {
            when {
                input.startsWith("../") -> input = input.substring(3)
                input.startsWith("./") -> input = input.substring(2)
                input.startsWith("/./") -> input = "/" + input.substring(3)
                input == "/." -> input = "/"
                input.startsWith("/../") -> { input = "/" + input.substring(4); removeLast() }
                input == "/.." -> { input = "/"; removeLast() }
                input == "." || input == ".." -> input = ""
                else -> {
                    val next = input.indexOf('/', if (input.startsWith('/')) 1 else 0)
                    if (next < 0) { output.append(input); input = "" }
                    else { output.append(input, 0, next); input = input.substring(next) }
                }
            }
        }
        return output.toString()
    }
    @JvmStatic fun isSameOrigin(baseUrl: String?, rawUrl: String?) = resolve(baseUrl, rawUrl) != null
    private fun effectivePort(uri: URI) = if (uri.port != -1) uri.port else if (uri.scheme?.lowercase(Locale.ROOT) == "https") 443 else 80
}
