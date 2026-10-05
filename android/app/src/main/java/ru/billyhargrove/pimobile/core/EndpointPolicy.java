package ru.billyhargrove.pimobile.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validation and canonicalisation of the gateway base URL.
 *
 * <p>Rules enforced here (and covered by unit tests):</p>
 * <ul>
 *   <li>only {@code http} and {@code https} are accepted;</li>
 *   <li>{@code https} is always allowed;</li>
 *   <li>{@code http} is allowed only in a debuggable build AND only for the
 *       emulator loopback alias {@code 10.0.2.2} or {@code localhost}/{@code 127.0.0.1};</li>
 *   <li>credentials in the authority, queries and fragments are rejected;</li>
 *   <li>the token is never a part of any URL – this class cannot even express one.</li>
 * </ul>
 */
public final class EndpointPolicy {

    /** Default production gateway shown pre-filled in the connect form. */
    public static final String DEFAULT_BASE_URL = "https://billyhargrove.ru";

    /** Hosts allowed to be used over cleartext, and only in debug builds. */
    public static final Set<String> DEBUG_HTTP_HOSTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("10.0.2.2", "localhost", "127.0.0.1")));

    public enum Result {
        OK,
        EMPTY,
        MALFORMED,
        UNSUPPORTED_SCHEME,
        CLEARTEXT_NOT_ALLOWED,
        HAS_CREDENTIALS,
        HAS_QUERY_OR_FRAGMENT,
        MISSING_HOST
    }

    private EndpointPolicy() {
    }

    public static Result validate(String input, boolean debuggable) {
        if (input == null || input.trim().isEmpty()) {
            return Result.EMPTY;
        }
        String candidate = withScheme(input.trim());
        if (candidate.indexOf(' ') >= 0 || candidate.indexOf('\n') >= 0 || candidate.indexOf('\t') >= 0) {
            return Result.MALFORMED;
        }
        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            return Result.MALFORMED;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Result.UNSUPPORTED_SCHEME;
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            // Catches things like "https:///path" and IPv6 typos.
            return Result.MISSING_HOST;
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            return Result.HAS_CREDENTIALS;
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            return Result.HAS_QUERY_OR_FRAGMENT;
        }
        if (scheme.equals("http")) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!debuggable || !DEBUG_HTTP_HOSTS.contains(host)) {
                return Result.CLEARTEXT_NOT_ALLOWED;
            }
        }
        return Result.OK;
    }

    /**
     * Canonical form: scheme + host (lowercase) + optional non-default port +
     * path without a trailing slash.
     *
     * @throws IllegalArgumentException when the input is not a valid endpoint
     */
    public static String normalize(String input) {
        String candidate = withScheme(input == null ? "" : input.trim());
        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Некорректный URL: " + input);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || scheme.isEmpty()) {
            throw new IllegalArgumentException("Некорректный URL: " + input);
        }
        int port = uri.getPort();
        if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) {
            port = -1;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != -1) {
            sb.append(':').append(port);
        }
        sb.append(path);
        return sb.toString();
    }

    /** Adds the REST path to the base URL, preserving any base path prefix. */
    public static String apiUrl(String baseUrl, String relativePath) {
        String base = normalize(baseUrl);
        String path = relativePath == null ? "" : relativePath.trim();
        if (path.isEmpty()) {
            return base;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    /** ws:// or wss:// URL of the gateway socket, derived from the base URL. */
    public static String wsUrl(String baseUrl) {
        String base = normalize(baseUrl);
        int schemeEnd = base.indexOf("://");
        String scheme = base.substring(0, schemeEnd);
        String rest = base.substring(schemeEnd + 3);
        String wsScheme = scheme.equals("https") ? "wss" : "ws";
        return wsScheme + "://" + rest + "/ws";
    }

    private static String withScheme(String input) {
        if (input.isEmpty()) {
            return input;
        }
        if (input.indexOf("://") < 0) {
            return "https://" + input;
        }
        return input;
    }
}
