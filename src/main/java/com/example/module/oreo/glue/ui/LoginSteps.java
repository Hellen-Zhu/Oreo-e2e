package com.example.module.oreo.glue.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;

public class LoginSteps {

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }

    @Given("^navigate to login page$")
    public void navigateToLoginPage() {
        String currentUrl = ctx.service().getPage().url();
        if (currentUrl != null && currentUrl.contains("/login")) {
            waitForLoginForm(ctx.timeoutMillis());
            return;
        }

        if (isLoginFormVisible(3000)) {
            return;
        }

        ctx.navigateToPath("/login");
        try {
            waitForLoginForm(ctx.timeoutMillis());
        } catch (TimeoutError e) {
            // A signed-in session sends /login back to the portal: the form never shows
            throw new AssertionError("No login form within " + ctx.timeoutMillis() + " ms after opening /login"
                    + " (was " + currentUrl + ", now " + ctx.service().getPage().url() + "). If a user is still"
                    + " signed in, log out first (step 'user logs out and re-login').", e);
        }
    }

    private boolean isLoginFormVisible(double timeoutMs) {
        try {
            waitForLoginForm(timeoutMs);
            return true;
        } catch (TimeoutError ignored) {
            return false;
        }
    }

    private void waitForLoginForm(double timeoutMs) {
        ctx.service().getLocator("login.email").waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(timeoutMs)
        );
    }

    /**
     * Confirms successful sign-in by waiting for the Trade Portal container to become visible.
     * This avoids brittle coupling to specific post-login API calls.
     */
    @Given("^confirm login and wait for trade portal ready$")
    public void confirmLoginAndWaitForTradePortalReady() {
        ctx.clickAndWaitForResponse("login.submit", "/api/v1/blotter/trades", "POST");
    }
}
