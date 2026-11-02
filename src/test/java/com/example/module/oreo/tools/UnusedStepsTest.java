package com.example.module.oreo.tools;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class UnusedStepsTest {

    private static final List<String> SNIPPETS = List.of(
            "@When \"^user books '([^']*)'$\"",
            "    When click button '$1'",
            "",
            "@When \"^nobody calls this$\"",
            "    When only a dead snippet calls this");

    private static final String JAVA = String.join("\n",
            "public class Steps {",
            "    @When(\"^click button 'book'$\")",
            "    public void clickBook() {}",
            "",
            "    @When(\"^only a dead snippet calls this$\")",
            "    public void dead() {}",
            "",
            "    // @When(\"^commented out$\")",
            "    @Given(\"^first text (\\\\d+)$\")",
            "    @When(\"^second text$\")",
            "    public void twoAnnotations(int n) {}",
            "}");

    private static List<UnusedSteps.Definition> definitions() {
        List<UnusedSteps.Definition> all = new ArrayList<>(UnusedSteps.snippets("a.snippet", SNIPPETS));
        all.addAll(UnusedSteps.javaSteps("Steps.java", JAVA));
        return all;
    }

    private static List<String> names(UnusedSteps.Report report) {
        return report.unused().stream().map(d -> d.file() + ":" + d.line()).toList();
    }

    @Test
    public void readsEveryAnnotationAndNoComment() {
        List<UnusedSteps.Definition> steps = UnusedSteps.javaSteps("Steps.java", JAVA);
        assertEquals(List.of("clickBook", "dead", "twoAnnotations", "twoAnnotations"),
                steps.stream().map(UnusedSteps.Definition::method).toList());
        assertEquals("^first text (\\d+)$", steps.get(2).pattern().pattern());
    }

    @Test
    public void walksSnippetBodiesWithTheCapturedValues() {
        UnusedSteps.Report report = UnusedSteps.analyze(definitions(), List.of("user books 'book'"));
        assertEquals(List.of("a.snippet:4", "Steps.java:5", "Steps.java:9", "Steps.java:10"), names(report));
        assertEquals(Set.of(), report.undefined());
    }

    @Test
    public void aStepNothingDefinesIsReported() {
        UnusedSteps.Report report = UnusedSteps.analyze(definitions(), List.of("a built-in step"));
        assertEquals(Set.of("a built-in step"), report.undefined());
    }

    @Test
    public void expandsAnOutlinePerRowAndIgnoresTheTable() {
        List<String> steps = UnusedSteps.featureSteps(List.of(
                "Feature: x",
                "  Background:",
                "    Given a login",
                "  Scenario Outline: [TC-1] y",
                "    # When a comment",
                "    When user books '<action>'",
                "    Then a fixed step",
                "    Examples:",
                "      | action |",
                "      | book   |",
                "      | save   |",
                "  Scenario: [TC-2] z",
                "    When user books 'save'"));
        assertEquals(List.of("a login", "user books 'book'", "user books 'save'", "a fixed step", "user books 'save'"),
                steps);
    }
}
