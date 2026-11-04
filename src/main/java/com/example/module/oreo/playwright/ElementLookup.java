package com.example.module.oreo.playwright;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.example.genie.module.playwright.core.PlaywrightElementRepository;
import com.example.genie.module.playwright.core.PlaywrightLocatorWrapper;
import com.example.genie.module.playwright.core.PlaywrightService;

import java.lang.reflect.Field;

/**
 * Finds elements through Genie's element repository, including table rows and cells. The repository has no public
 * accessor: reflection on it stays in this class.
 */
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

    /**
     * The row of {@code rowKey} whose cell {@code cellKey} (with {@code {0}} = {@code column}) shows exactly
     * {@code text}: a trade by its full ID, not by its position. A suffixed ID ({@code ...-ALLOC-1}) does not
     * match; two matching rows fail when the row is used.
     */
    Locator rowWithCell(String rowKey, String cellKey, String column, String text) {
        Locator cell = service.getPage().locator(locatorSelector(cellKey, column))
                .getByText(text, new Locator.GetByTextOptions().setExact(true));
        return service.getLocator(rowKey).filter(new Locator.FilterOptions().setHas(cell));
    }

    /** The cell {@code cellKey} (with {@code {0}} = {@code column}) inside {@code row}. */
    Locator cellOf(Locator row, String cellKey, String column) {
        return row.locator(locatorSelector(cellKey, column));
    }

    /**
     * The CSS selector of an element found by locator, with {@code {0}} replaced by {@code value}: for an element
     * looked up inside another one ({@code row.locator(selector)}).
     */
    private String locatorSelector(String name, String value) {
        PlaywrightLocatorWrapper element;
        try {
            element = elementRepository().lookup(name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Element lookup failed for: " + name, e);
        }
        if (!String.valueOf(element.getFindBy()).replaceAll("[^A-Za-z]", "").equalsIgnoreCase("locator")) {
            throw new IllegalStateException(
                    "Element '" + name + "' must be found by locator, but is found by " + element.getFindBy());
        }
        return element.getValue().replace("{0}", value);
    }

    private PlaywrightElementRepository elementRepository() throws ReflectiveOperationException {
        Field repoField = PlaywrightService.class.getDeclaredField("playwrightElementRepository");
        repoField.setAccessible(true);
        return (PlaywrightElementRepository) repoField.get(service);
    }
}
