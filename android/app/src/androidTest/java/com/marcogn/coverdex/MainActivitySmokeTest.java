package com.marcogn.coverdex;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matchers.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matchers.ViewMatchers.withClassName;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.webkit.WebView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Minimal launch smoke test: MainActivity starts, reaches RESUMED, the
 * Capacitor WebView is displayed, and it has loaded the bundled app (a
 * non-empty URL). Not a substitute for the Vitest suite, which remains the
 * source of truth for app logic.
 */
@RunWith(AndroidJUnit4.class)
public class MainActivitySmokeTest {

    @Test
    public void mainActivityLaunchesAndWebViewLoads() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(scenario.getState().isAtLeast(Lifecycle.State.RESUMED));

            onView(withClassName(equalTo(WebView.class.getName()))).check(matches(isDisplayed()));

            AtomicReference<String> loadedUrl = new AtomicReference<>();
            scenario.onActivity(activity -> loadedUrl.set(activity.getBridge().getWebView().getUrl()));

            assertNotNull("Capacitor WebView never loaded a URL", loadedUrl.get());
        }
    }
}
