package ru.billyhargrove.pimobile

import android.content.pm.ApplicationInfo
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.EndpointPolicy
import ru.billyhargrove.pimobile.net.HttpClients

/** Actual installed release/runtime policy; the release testBuildType compiles these constants too. */
@RunWith(AndroidJUnit4::class)
class ReleasePolicyTest {
    @Test fun installedReleaseDisablesDebuggingAndHttpLogging() {
        assumeFalse("Release-only gate", BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("release", BuildConfig.BUILD_TYPE)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        assertTrue(HttpClients.get().interceptors.none { it is HttpLoggingInterceptor })
    }
    @Test fun releaseRejectsTheDebugEmulatorCleartextOverrides() {
        assumeFalse("Release-only gate", BuildConfig.DEBUG)
        for (host in listOf("10.0.2.2", "localhost", "127.0.0.1", "example.com")) {
            assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host))
            assertEquals(EndpointPolicy.Result.CLEARTEXT_NOT_ALLOWED, EndpointPolicy.validate("http://$host:8788", BuildConfig.DEBUG))
        }
    }
}
