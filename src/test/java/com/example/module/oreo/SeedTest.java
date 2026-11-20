package com.example.module.oreo;

import com.example.genie.fork.model.ForkMode;
import com.example.genie.fork.model.ForkWorkingDirectoryPolicy;
import com.example.genie.junit.Genie;
import com.example.genie.junit.GenieOptions;
import com.example.module.oreo.testdata.SeedData;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

/**
 * The SIT seed: books the trades and events of {@code seed/catalog.json} for UAT and writes each booking to the
 * run's manifest, {@code target/seed/manifest-<run ID>.jsonl}, a new file per run; not a test suite. A plain {@code mvn test} never runs it (surefire excludes
 * it); run it on purpose, narrowed by tags or names:
 * {@code mvn -B test -Dtest=SeedTest "-Dcucumber.options=--tags @seed-trade"}. Details: {@code docs/sit-seed.md}.
 */
@RunWith(Genie.class)
@CucumberOptions(
        junit    = {"--step-notifications"},
        features = {"classpath:seed/features"},
        glue     = {
            "classpath:com.example.module.oreo.glue",
            "classpath:snippet"
        },
        tags     = {"not @wip"}
)
@GenieOptions(
        properties           = "src/test/resources/sit.properties",
        forkMode             = ForkMode.FEATURE,
        forkCount            = 3,
        forkWorkingDirPolicy = ForkWorkingDirectoryPolicy.DISCRETE
)
public class SeedTest {

    // Runs in the JVM that forks the features, before any of them: the forked JVMs find this run and share its manifest
    static {
        SeedData.startRun();
    }
}
