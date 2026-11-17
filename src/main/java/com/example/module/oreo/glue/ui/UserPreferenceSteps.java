package com.example.module.oreo.glue.ui;

import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.config.TestUser;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;

public class UserPreferenceSteps {
    private RequestResponseHelper helper;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    /** Puts a role's preferences on the server back to the defaults of the setup {@code user-preference-defaults}. */
    @Given("^atomic preferences of '([^']*)' are set to application defaults$")
    public void setUserPreferencesToDefaults(String role) {
        String payload = TestData.setup("user-preference-defaults").request();
        RestApiUtil.put(helper, "/uc/api/user-preferences", TestUser.of(role).email(), payload);
    }
}
