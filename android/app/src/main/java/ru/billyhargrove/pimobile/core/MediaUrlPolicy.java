package ru.billyhargrove.pimobile.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Same-origin gate for media.
 *
 * <p>The gateway serves images at an authenticated relative URL
 * ({@code /api/sessions/<id>/media/<sha256>}) on the very same origin the user
 * configured. Media is fetched with the {@code Authorization} header, so the
 * bearer token must never follow a URL to another host. Every URL coming from
 * the server is passed through {@link #resolve(String, String)} and a
 * {@code null} result means "refuse to load".</p>
 */
public final class MediaUrlPolicy {

    private MediaUrlPolicy() {
    }

    /**
     * @return an absolute same-origin URL, or {@code null} when the reference is
     *         unusable or points somewhere else.
     */
    public static String resolve(String baseUrl, String rawUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty() || rawUrl == null) {
            return null;
        }
        String raw = rawUrl.trim();
        if (raw.isEmpty()) {
            return null;
        }
        URI base;
        try {
            base = new URI(EndpointPolicy.normalize(baseUrl));
        } catch (RuntimeException | URISyntaxException e) {
            return null;
        }
        String baseScheme = base.getScheme().toLowerCase(Locale.ROOT);
        String baseHost = base.getHost() == null ? "" : base.getHost().toLowerCase(Locale.ROOT);
        if (baseHost.isEmpty()) {
            return null;
        }
        URI origin;
        try {
            origin = new URI(baseScheme, null, baseHost, base.getPort(), "", null, null);
        } catch (URISyntaxException e) {
            return null;
        }
        URI target;
        try {
            // Root-relative media paths are resolved against the origin, so a
            // configured base path prefix never leaks into the media URL.
            target = raw.startsWith("/") ? origin.resolve(raw) : base.resolve(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (target == null) {
            return null;
        }
        String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        if (!scheme.equals(baseScheme)) {
            return null;
        }
        if (target.getUserInfo() != null && !target.getUserInfo().isEmpty()) {
            return null;
        }
        String host = target.getHost() == null ? "" : target.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals(baseHost)) {
            return null;
        }
        if (effectivePort(target) != effectivePort(base)) {
            return null;
        }
        if (target.getPath() == null || target.getPath().isEmpty()) {
            return null;
        }
        // Canonicalise the path with the RFC 3986 remove_dot_segments algorithm.
        // OkHttp canonicalises the same way before sending, so what we validate is
        // exactly what goes on the wire; a ".." segment can never change the origin
        // because the scheme/host/port checks above already ran.
        String path = removeDotSegments(target.getPath());
        if (path.isEmpty()) {
            path = "/";
        }
        try {
            return new URI(scheme, null, host, target.getPort(), path, target.getQuery(), null)
                    .toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** RFC 3986, section 5.2.4. */
    static String removeDotSegments(String path) {
        String input = path == null ? "" : path;
        StringBuilder output = new StringBuilder(input.length());
        while (!input.isEmpty()) {
            if (input.startsWith("../")) {
                input = input.substring(3);
            } else if (input.startsWith("./")) {
                input = input.substring(2);
            } else if (input.startsWith("/./")) {
                input = "/" + input.substring(3);
            } else if (input.equals("/.")) {
                input = "/";
            } else if (input.startsWith("/../")) {
                input = "/" + input.substring(4);
                removeLastSegment(output);
            } else if (input.equals("/..")) {
                input = "/";
                removeLastSegment(output);
            } else if (input.equals(".") || input.equals("..")) {
                input = "";
            } else {
                int searchFrom = input.startsWith("/") ? 1 : 0;
                int next = input.indexOf('/', searchFrom);
                if (next < 0) {
                    output.append(input);
                    input = "";
                } else {
                    output.append(input, 0, next);
                    input = input.substring(next);
                }
            }
        }
        return output.toString();
    }

    private static void removeLastSegment(StringBuilder output) {
        int slash = output.lastIndexOf("/");
        if (slash < 0) {
            output.setLength(0);
        } else {
            output.setLength(slash);
        }
    }

    public static boolean isSameOrigin(String baseUrl, String rawUrl) {
        return resolve(baseUrl, rawUrl) != null;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("https") ? 443 : 80;
    }
}
