package com.example.module.oreo.glue.ui;

import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.microsoft.playwright.TimeoutError;
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
        ctx.clickAndWaitForResponse("login.submit", "/api/v1/blotter/trades", "POST");
        ctx.write("Signed in as " + user.role() + " (" + email + ")");
    }
}
