package com.example.module.oreo.glue.common;

import com.example.genie.model.GenieScenario;
import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.glue.ScenarioContext;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Steps about the test users that @api and @playwright snippets share: they need neither the REST helper nor a
 * browser, only the scenario variables.
 */
public class TestUserSteps {

    private static final Logger log = LoggerFactory.getLogger(TestUserSteps.class);

    private ScenarioContext ctx;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = new ScenarioContext((GenieScenario) scenario) {
        };
    }

    /**
     * Stores the e-mail of a role as a scenario variable, so SQL can reference it as {@code \${name}}; the e-mail is
     * the one of the environment the run uses.
     *
     * <pre>And store the e-mail of user 'maker' as 'blotterUserEmail'</pre>
     */
    @Given("^store the e-mail of user '([^']*)' as '([^']*)'$")
    public void storeUserEmail(String role, String varName) {
        String email = TestUser.of(role).email();
        ctx.set(varName, email);
        ctx.write("[stored] " + varName + " = " + email);
        log.info("[TestUserSteps] stored '{}' = '{}'", varName, email);
    }
}
