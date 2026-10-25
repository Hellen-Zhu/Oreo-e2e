package com.example.module.oreo.glue.ui;

import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.testdata.TestData;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;

public class UserPreferenceSteps {
    private RequestResponseHelper helper;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    @Given("^atomic user preferences are set to application defaults$")
    public void setUserPreferencesToDefaults() {
        String payload = TestData.setup("user-preference-defaults").request();
        RestApiUtil.put(helper, "/uc/api/user-preferences", "user@example.com", payload);
    }
}
