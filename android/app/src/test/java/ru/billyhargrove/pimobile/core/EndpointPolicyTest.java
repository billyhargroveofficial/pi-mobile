package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EndpointPolicyTest {

    @Test
    public void httpsIsAlwaysAccepted() {
        assertEquals(EndpointPolicy.Result.OK, EndpointPolicy.validate("https://billyhargrove.ru", false));
        assertEquals(EndpointPolicy.Result.OK, EndpointPolicy.validate("https://billyhargrove.ru:8443/gw", false));
        assertEquals(EndpointPolicy.Result.OK, EndpointPolicy.validate("https://example.com/path/", true));
    }

    @Test
    public void schemeIsOptionalAndDefaultsToHttps() {
        assertEquals(EndpointPolicy.Result.OK, EndpointPolicy.validate("billyhargrove.ru", false));
        assertEquals("https://billyhargrove.ru", EndpointPolicy.normalize("billyhargrove.ru"));
    }

    @Test
    public void cleartextOnlyAllowedForDebugLoopbackHosts() {
        assertEquals(EndpointPolicy.Result.CLEARTEXT_NOT_ALLOWED,
                EndpointPolicy.validate("http://10.0.2.2:8788", false));
        assertEquals(EndpointPolicy.Result.OK,
                EndpointPolicy.validate("http://10.0.2.2:8788", true));
        assertEquals(EndpointPolicy.Result.OK,
                EndpointPolicy.validate("http://localhost:8788", true));
        assertEquals(EndpointPolicy.Result.OK,
                EndpointPolicy.validate("http://127.0.0.1:8788", true));
        assertEquals(EndpointPolicy.Result.CLEARTEXT_NOT_ALLOWED,
                EndpointPolicy.validate("http://example.com", true));
        assertEquals(EndpointPolicy.Result.CLEARTEXT_NOT_ALLOWED,
                EndpointPolicy.validate("http://10.0.2.3:8788", true));
    }

    @Test
    public void credentialsAndQueriesAreRejected() {
        assertEquals(EndpointPolicy.Result.HAS_CREDENTIALS,
                EndpointPolicy.validate("https://user:pass@billyhargrove.ru", true));
        assertEquals(EndpointPolicy.Result.HAS_QUERY_OR_FRAGMENT,
                EndpointPolicy.validate("https://billyhargrove.ru?token=abc", true));
        assertEquals(EndpointPolicy.Result.HAS_QUERY_OR_FRAGMENT,
                EndpointPolicy.validate("https://billyhargrove.ru/#x", true));
    }

    @Test
    public void malformedInputsAreRejected() {
        assertEquals(EndpointPolicy.Result.EMPTY, EndpointPolicy.validate("", true));
        assertEquals(EndpointPolicy.Result.EMPTY, EndpointPolicy.validate(null, true));
        assertEquals(EndpointPolicy.Result.MALFORMED, EndpointPolicy.validate("ht tp://x", true));
        assertEquals(EndpointPolicy.Result.UNSUPPORTED_SCHEME, EndpointPolicy.validate("ftp://example.com", true));
        assertEquals(EndpointPolicy.Result.UNSUPPORTED_SCHEME, EndpointPolicy.validate("ws://example.com", true));
    }

    @Test
    public void outOfRangePortsCannotBeSavedAndCrashALaterSocket() {
        assertEquals(EndpointPolicy.Result.MALFORMED, EndpointPolicy.validate("https://example.com:0", true));
        assertEquals(EndpointPolicy.Result.MALFORMED, EndpointPolicy.validate("https://example.com:65536", true));
        assertEquals(EndpointPolicy.Result.OK, EndpointPolicy.validate("https://example.com:65535", false));
    }

    @Test
    public void normalizeCanonicalisesTheBaseUrl() {
        assertEquals("https://billyhargrove.ru", EndpointPolicy.normalize("https://billyhargrove.ru/"));
        assertEquals("https://billyhargrove.ru", EndpointPolicy.normalize("https://BILLYHARGROVE.ru:443"));
        assertEquals("http://10.0.2.2:8788", EndpointPolicy.normalize("http://10.0.2.2:8788/"));
        assertEquals("https://example.com/gw", EndpointPolicy.normalize("https://example.com/gw//"));
    }

    @Test
    public void apiUrlJoinsPathAndKeepsPrefix() {
        assertEquals("https://billyhargrove.ru/api/catalog",
                EndpointPolicy.apiUrl("https://billyhargrove.ru", "/api/catalog"));
        assertEquals("https://example.com/gw/api/catalog",
                EndpointPolicy.apiUrl("https://example.com/gw/", "api/catalog"));
    }

    @Test
    public void websocketUrlFollowsTheBaseScheme() {
        assertEquals("wss://billyhargrove.ru/ws", EndpointPolicy.wsUrl("https://billyhargrove.ru"));
        assertEquals("ws://10.0.2.2:8788/ws", EndpointPolicy.wsUrl("http://10.0.2.2:8788"));
        assertEquals("wss://example.com/gw/ws", EndpointPolicy.wsUrl("https://example.com/gw"));
    }

    @Test
    public void tokenNeverAppearsInGeneratedUrls() {
        String url = EndpointPolicy.apiUrl("https://billyhargrove.ru", "/api/sessions/abc/media/deadbeef");
        assertTrue(url.indexOf("token") < 0);
        assertNotEquals(-1, url.indexOf("/api/sessions/abc/media/deadbeef"));
    }
}
