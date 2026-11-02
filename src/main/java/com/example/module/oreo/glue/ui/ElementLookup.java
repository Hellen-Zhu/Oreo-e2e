package com.example.module.oreo.glue.ui;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.example.genie.module.playwright.core.PlaywrightElementRepository;
import com.example.genie.module.playwright.core.PlaywrightLocatorWrapper;
import com.example.genie.module.playwright.core.PlaywrightService;

import java.lang.reflect.Field;

/** Reads Genie's element repository, which has no public accessor: reflection on it stays in this class. */
final class ElementLookup {

    private final PlaywrightService service;

    ElementLookup(PlaywrightService service) {
        this.service = service;
    }

    Locator getLocatorWithParam(String name, String value) {
        try {
            PlaywrightElementRepository repo = elementRepository();

            Field frameCtxField = PlaywrightService.class.getDeclaredField("pageFrameContexts");
            frameCtxField.setAccessible(true);
            // [shown in the photo as "/unchecked/"]
            java.util.Map<Page, Frame> pageFrameContexts = (java.util.Map<Page, Frame>) frameCtxField.get(service);

            Page currentPage = service.getPage();
            Frame currentFrame = pageFrameContexts.get(currentPage);

            PlaywrightLocatorWrapper wrapper = repo.lookup(name);
            PlaywrightLocatorWrapper resolved = new PlaywrightLocatorWrapper(
                    wrapper.getFindBy(), wrapper.getValue().replace("{0}", value));
            return resolved.getLocator(currentPage, currentFrame);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("getLocatorWithParam failed for: " + name, e);
        }
    }

    /** The CSS selector of an element found by test id: {@code [data-testid="<value>"]}. */
    String testIdSelector(String name) {
        PlaywrightLocatorWrapper element;
        try {
            element = elementRepository().lookup(name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Element lookup failed for: " + name, e);
        }
        if (!String.valueOf(element.getFindBy()).replaceAll("[^A-Za-z]", "").equalsIgnoreCase("testId")) {
            throw new IllegalStateException(
                    "Element '" + name + "' must be found by testId, but is found by " + element.getFindBy());
        }
        return "[data-testid=\"" + element.getValue().replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
    }

    private PlaywrightElementRepository elementRepository() throws ReflectiveOperationException {
        Field repoField = PlaywrightService.class.getDeclaredField("playwrightElementRepository");
        repoField.setAccessible(true);
        return (PlaywrightElementRepository) repoField.get(service);
    }
}
