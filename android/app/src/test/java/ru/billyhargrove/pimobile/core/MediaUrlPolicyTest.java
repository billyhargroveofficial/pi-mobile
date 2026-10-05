package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaUrlPolicyTest {

    private static final String BASE = "https://billyhargrove.ru";

    @Test
    public void relativeMediaPathIsResolvedAgainstTheOrigin() {
        String url = MediaUrlPolicy.resolve(BASE, "/api/sessions/s1/media/abc123");
        assertEquals("https://billyhargrove.ru/api/sessions/s1/media/abc123", url);
    }

    @Test
    public void basePathPrefixDoesNotLeakIntoRootRelativeMedia() {
        String url = MediaUrlPolicy.resolve("https://example.com/gw", "/api/sessions/s1/media/abc");
        assertEquals("https://example.com/api/sessions/s1/media/abc", url);
    }

    @Test
    public void absoluteSameOriginUrlIsAccepted() {
        assertEquals("https://billyhargrove.ru/api/sessions/s1/media/abc",
                MediaUrlPolicy.resolve(BASE, "https://billyhargrove.ru/api/sessions/s1/media/abc"));
    }

    @Test
    public void foreignOriginsAreRejected() {
        assertNull(MediaUrlPolicy.resolve(BASE, "https://evil.example/api/sessions/s1/media/abc"));
        assertNull(MediaUrlPolicy.resolve(BASE, "//evil.example/api/sessions/s1/media/abc"));
        assertNull(MediaUrlPolicy.resolve(BASE, "http://billyhargrove.ru/api/x"));
        assertNull(MediaUrlPolicy.resolve(BASE, "https://sub.billyhargrove.ru/api/x"));
        assertNull(MediaUrlPolicy.resolve(BASE, "https://billyhargrove.ru:8443/api/x"));
    }

    @Test
    public void nonHttpSchemesAreRejected() {
        assertNull(MediaUrlPolicy.resolve(BASE, "javascript:alert(1)"));
        assertNull(MediaUrlPolicy.resolve(BASE, "data:image/png;base64,AAAA"));
        assertNull(MediaUrlPolicy.resolve(BASE, "file:///etc/passwd"));
    }

    @Test
    public void credentialsInTargetAreRejected() {
        assertNull(MediaUrlPolicy.resolve(BASE, "https://user:pass@billyhargrove.ru/api/x"));
    }

    @Test
    public void emptyOrBrokenInputsAreRejected() {
        assertNull(MediaUrlPolicy.resolve(BASE, null));
        assertNull(MediaUrlPolicy.resolve(BASE, "   "));
        assertNull(MediaUrlPolicy.resolve(BASE, ""));
        assertNull(MediaUrlPolicy.resolve("not a url", "/api/x"));
        assertNull(MediaUrlPolicy.resolve(null, "/api/x"));
    }

    @Test
    public void fragmentsAreStrippedAndQueryIsPreservedWithoutToken() {
        String url = MediaUrlPolicy.resolve(BASE, "/api/sessions/s1/media/abc#frag");
        assertEquals("https://billyhargrove.ru/api/sessions/s1/media/abc", url);
        String withQuery = MediaUrlPolicy.resolve(BASE, "/api/sessions/s1/media/abc?w=100");
        assertEquals("https://billyhargrove.ru/api/sessions/s1/media/abc?w=100", withQuery);
        assertTrue(withQuery.indexOf("token") < 0);
    }

    @Test
    public void relativeUrlWithoutLeadingSlashStillResolvesOnTheSameHost() {
        assertEquals("https://billyhargrove.ru/api/x",
                MediaUrlPolicy.resolve(BASE, "api/x"));
        // Dot segments are removed (RFC 3986) and can never leave the origin.
        assertEquals("https://billyhargrove.ru/api/x",
                MediaUrlPolicy.resolve(BASE + "/gw/", "../../api/x"));
        assertEquals("https://billyhargrove.ru/api/x",
                MediaUrlPolicy.resolve(BASE + "/gw", "../api/x"));
        assertEquals("https://billyhargrove.ru/api/x",
                MediaUrlPolicy.resolve(BASE, "/api/../api/x"));
        assertEquals("https://billyhargrove.ru/",
                MediaUrlPolicy.resolve(BASE, "/../../../.."));
    }

    @Test
    public void dotSegmentsCannotReachAnotherOrigin() {
        // A path-only reference is always resolved on the configured host.
        assertEquals("https://billyhargrove.ru/evil.example/x",
                MediaUrlPolicy.resolve(BASE, "https://billyhargrove.ru/../../evil.example/x"));
        assertEquals("https://billyhargrove.ru/x",
                MediaUrlPolicy.resolve(BASE + "/gw/", "../../../x"));
        assertNull(MediaUrlPolicy.resolve(BASE, "https://evil.example/x"));
    }

    @Test
    public void removeDotSegmentsImplementsRfc3986() {
        // Examples taken from RFC 3986 section 5.4 (normal and abnormal cases).
        assertEquals("/a/g", MediaUrlPolicy.removeDotSegments("/a/b/c/./../../g"));
        assertEquals("mid/6", MediaUrlPolicy.removeDotSegments("mid/content=5/../6"));
        assertEquals("/", MediaUrlPolicy.removeDotSegments("/./"));
        assertEquals("/", MediaUrlPolicy.removeDotSegments("/../"));
        assertEquals("/b", MediaUrlPolicy.removeDotSegments("/a/../b"));
        assertEquals("/a/b/", MediaUrlPolicy.removeDotSegments("/a/b/./"));
    }
}
