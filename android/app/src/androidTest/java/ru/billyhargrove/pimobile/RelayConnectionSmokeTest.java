package ru.billyhargrove.pimobile;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** Explicit read-only live smoke. No credentials in APK/arguments/output and no Pi commands. */
@RunWith(AndroidJUnit4.class)
public class RelayConnectionSmokeTest {
    @Test public void configuredRelayConnectsTheActualAndroidClient() throws Exception {
        Assume.assumeTrue("Opt in with relaySmoke=true and a private cache input",
            "true".equals(InstrumentationRegistry.getArguments().getString("relaySmoke")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String url = InstrumentationRegistry.getArguments().getString("relayUrl");
        assertNotNull("Relay URL required", url);
        File input = new File(context.getCacheDir(), "relay-smoke-token");
        String token;
        try { token = new String(Files.readAllBytes(input.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim(); }
        finally { input.delete(); }
        assertTrue("Private token input is missing or malformed", token.length() >= 32 && token.length() <= 512);
        PiApp app = PiApp.get(context);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            app.client().disconnect();
            app.settings().setBaseUrl(url);
            app.settings().setToken(token);
        });
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> app.client().connect(url, token));
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            assertTrue("Android WSS connection did not reach Connected",
                device.wait(Until.hasObject(By.res(context.getPackageName(), "connectionIndicator").desc("Connected")), 15000));
            assertTrue("Authenticated Android REST catalog is unavailable",
                app.api().fetchCatalog(url, app.settings().token()).workspaces().size() > 0);
        }
    }
}
