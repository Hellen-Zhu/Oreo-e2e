package com.example.module.oreo.glue.api;

import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.utils.CleanupTestDataUtil;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;

public class BlotterApiSteps {

    private RestServiceResolver.Context ctx;
    private RequestResponseHelper helper;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    @After(value = "@api and @ADO#trade_blotter", order = 2)
    public void deleteBlotterAfterTest() {
        String blotterId = ctx.find("newBlotterId");
        CleanupTestDataUtil.deleteBlotter(helper, blotterId, "maker@example.com");

        String importedId = ctx.find("importedBlotterId");
        CleanupTestDataUtil.deleteBlotter(helper, importedId, "user@example.com");
    }
}
