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

    /** Always opens /login, whatever the page shows, and waits for the login form. */
    @Given("^navigate to login page$")
    public void navigateToLoginPage() {
        String previousUrl = ctx.service().getPage().url();
        ctx.navigateToPath("/login");
        try {
            ctx.service().getLocator("login.email").waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(ctx.timeoutMillis())
            );
        } catch (TimeoutError e) {
            // A signed-in session sends /login back to the portal: the form never shows
            throw new AssertionError("No login form within " + ctx.timeoutMillis() + " ms after opening /login"
                    + " (was " + previousUrl + ", now " + ctx.service().getPage().url() + "). If a user is still"
                    + " signed in, log out first (step 'user logs out and re-login').", e);
        }
    }
}
