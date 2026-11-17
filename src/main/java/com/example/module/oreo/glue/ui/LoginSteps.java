package com.example.module.oreo.glue.ui;

import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.microsoft.playwright.TimeoutError;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;

import java.util.LinkedHashMap;
import java.util.Map;

public class LoginSteps {

    /** Set in the tab's session storage once the sign-in is written: a reload or a logout never writes it again. */
    private static final String SIGNED_IN_MARK = "oreo-e2e-storage-sign-in";

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }

    /** Always opens /login, whatever the page shows, and waits for the login form. */
    @Given("^navigate to login page$")
    public void navigateToLoginPage() {
        String previousUrl = ctx.service().getPage().url();
        ctx.navigateToPath("/login");
        try {
            ctx.waitForVisible(ctx.service().getLocator("login.email"));
        } catch (TimeoutError e) {
            // A signed-in session sends /login back to the portal: the form never shows
            throw new AssertionError("No login form within " + ctx.timeoutMillis() + " ms after opening /login"
                    + " (was " + previousUrl + ", now " + ctx.service().getPage().url() + "). If a user is still"
                    + " signed in, log out first (step 'user logs out and re-login').", e);
        }
    }

    /**
     * Signs in on the open login page as a {@link TestUser} role. Java, not a snippet, so that the password comes from
     * the configuration and never appears in a step, the report or the log.
     *
     * <pre>And page login signs in as 'checker'</pre>
     */
    @Given("^page login signs in as '([^']*)'$")
    public void signInAs(String role) {
        TestUser user = TestUser.of(role);
        String email = user.email();
        ctx.innerInput("login.email").fill(email);
        ctx.innerInput("login.password").fill(user.password());
        ctx.clickAndWaitForResponse("login.submit", TradeApiClient.BLOTTER_TRADES_PATH, "POST");
        ctx.write("Signed in as " + user.role() + " (" + email + ")");
    }

    /**
     * Signs in as a {@link TestUser} role without the login page. The portal's legacy sign-in
     * ({@code fx-auth-mode=legacy}) is the user held in local storage; the context gets it before the portal loads.
     * The preferences come from the server: a role that needs the defaults gets them before it signs in
     * ({@code atomic preferences of '<role>' are set to application defaults}).
     * Written once per tab: after a logout the portal stays signed out, and signing in again goes through the login
     * page ({@code login through the login page as '<role>'}). Then opens the portal.
     *
     * <pre>Given page login signs in as 'maker' through the browser storage</pre>
     */
    @Given("^page login signs in as '([^']*)' through the browser storage$")
    public void signInThroughBrowserStorage(String role) {
        TestUser user = TestUser.of(role);
        String email = user.email();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("fx-auth-mode", "legacy");
        entries.put("fx-auth-session-version", "1");
        entries.put("fx-portal-user", Json.compact(Json.MAPPER.createObjectNode()
                .put("email", email).put("name", user.role())));
        String values = Json.compact(Json.MAPPER.valueToTree(entries));
        // about:blank has no storage: the try leaves it alone, the portal's first page writes the entries
        ctx.service().getPage().context().addInitScript("(() => { try {"
                + " if (window.sessionStorage.getItem('" + SIGNED_IN_MARK + "')) return;"
                + " const entries = " + values + ";"
                + " for (const key of Object.keys(entries)) window.localStorage.setItem(key, entries[key]);"
                + " window.sessionStorage.setItem('" + SIGNED_IN_MARK + "', '1');"
                + " } catch (e) { } })();");
        ctx.navigateToPath("/");
        ctx.write("Signed in as " + user.role() + " (" + email + ") through the browser storage");
    }
}
