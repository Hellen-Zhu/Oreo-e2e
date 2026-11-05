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
        submitAndWaitForFirstBlotterLoad();
        ctx.write("Signed in as " + user.role() + " (" + email + ")");
    }

    /**
     * Submits the filled login form and waits for the portal's first blotter load. After the click the portal loads
     * config, unread count, products, user preferences, blotters and availability, and only then sends
     * {@code POST /api/v1/blotter/trades}: the last call of the start-up and the one the blotter draws from. The wait is
     * registered before the click, so a fast load is not missed, and a request in flight before the click is not
     * taken. On a timeout the message gives the page: still on /login means the sign-in itself failed.
     *
     * <pre>And submit login and wait for the first blotter load</pre>
     */
    @Given("^submit login and wait for the first blotter load$")
    public void submitAndWaitForFirstBlotterLoad() {
        ctx.clickAndWaitForResponse("login.submit", "/api/v1/blotter/trades", "POST");
    }
}
