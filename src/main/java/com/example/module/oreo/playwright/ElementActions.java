package com.example.module.oreo.playwright;

import com.example.genie.module.playwright.core.PlaywrightService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.function.LongSupplier;

/** Operates the elements that need more than a plain click or fill. */
final class ElementActions {

    private static final String INNER_INPUT = "input[part='input'], textarea[part='input']";

    private final PlaywrightService service;
    private final LongSupplier timeoutMillis;

    ElementActions(PlaywrightService service, LongSupplier timeoutMillis) {
        this.service = service;
        this.timeoutMillis = timeoutMillis;
    }

    /** The input or textarea inside a UI component, which is what takes the value. */
    static Locator innerInput(Locator element) {
        return element.locator(INNER_INPUT);
    }

    void waitForVisible(Locator element) {
        element.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMillis.getAsLong()));
    }

    /**
     * Fills a React-controlled {@code <input type="date">} via {@code pressSequentially} and
     * a dispatched native {@code change} event, so React's synthetic onChange fires and any
     * {@code hasChanges} state becomes true. Use this instead of {@code locator.fill()} for
     * date inputs — {@code fill()} throws {@code Malformed value} at the Playwright layer
     * because Playwright validates the value format against the input type before writing it.
     *
     * @param elementKey element repository key of the date input
     * @param value      date string to type (format must match the input's expected format)
     */
    void fillDateInput(String elementKey, String value) {
        Locator input = service.getLocator(elementKey);
        waitForVisible(input);
        Boolean disabled = (Boolean) input.evaluate("el => !!el.disabled");
        if (Boolean.TRUE.equals(disabled)) {
            throw new IllegalStateException("Element is disabled (read-only state).");
        }
        input.evaluate(
                "(el, v) => {" +
                        "el.value = v;" +
                        "el.dispatchEvent(new CustomEvent('ui-change', {" +
                        "detail: { value: v }, bubbles: true, composed: true" +
                        "}));" +
                        "}",
                value
        );
    }

    /**
     * Clicks the option whose visible text matches {@code name},
     * with configurable exact-match behaviour.
     *
     * @param name the visible label of the option to click
     */
    void clickOption(String name) {
        // UI WebKit: ui-dropdown-input renders sl-menu-item elements inside its shadow DOM.
        // ui-dropdown-option elements exist in light DOM but have zero bounding rect and
        // cannot be clicked directly. We must traverse the shadow DOM to find sl-menu-item.
        Locator option = service.getPage().getByRole(
                AriaRole.MENUITEM,
                new Page.GetByRoleOptions()
                        .setName(name)
                        .setExact(true)
        );

        waitForVisible(option);
        option.click();
    }

    /** The trimmed visible text of {@code element}. */
    String text(Locator element) {
        return element.innerText(new Locator.InnerTextOptions().setTimeout(timeoutMillis.getAsLong())).trim();
    }
}
