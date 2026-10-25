<!-- Transcribed from IMG_6424–IMG_6428; overlapping passages merged. Catalog wording matches the existing transcription through Screenshots. Only the photographed sections are included. Archival source text only. -->

# Genie Playwright Built-in Step Catalog

Full reference for all `genie-playwright` built-in steps.
Check this before writing any snippet or Java glue — the vast majority of UI interactions are covered here.

---

## Navigation

| Step | Purpose |
|---|---|
| `open baseurl` | Open the base URL in the browser. |
| `open '<url>'` | Open the provided URL. |
| `reset baseurl` | Reset the base URL to null. |
| `set baseurl as '<url>'` | Set the base URL for navigation. |
| `navigate backward` | Navigate back in browser history. |
| `navigate forward` | Navigate forward in browser history. |
| `refresh webpage` | Refresh the current page. |

## Page / Frame

| Step | Purpose |
|---|---|
| `assert that current url is '<url>'` | Assert the current URL matches exactly. |
| `assert that current url contains '<text>'` | Assert the current URL contains text. |
| `assert that current url is blank` | Assert the current URL is blank. |
| `assert that current url matches the regex '<pattern>'` | Assert URL matches a regex. |
| `assert that title is '<text>'` | Assert the page title matches exactly. |
| `assert that title contains '<text>'` | Assert the page title contains text. |
| `switch to frame with name '<name>'` | Switch to a frame by name attribute. |
| `switch to frame with CSS selector '<selector>'` | Switch to a frame by CSS selector. |
| `find and switch to frame with name '<name>'` | Auto-find and switch to a named frame. |
| `switch to root frame` | Return to the root frame (main content). |

## Multi-tab / Multi-context

| Step | Purpose |
|---|---|
| `a new page is created in current context` | Open a new tab in the current context. |
| `page <N> in current context is used` | Switch to tab at index N (0-based). |
| `the newest page in current context is used` | Switch to the most recently opened tab. |
| `the current page in current context is closed` | Close the current tab. |
| `the '<name>' playwright context is used` | Switch to (or create) a named browser context. |
| `the '<name>' playwright context is closed` | Close a named browser context. |
| `the current playwright context is closed` | Close the current browser context. |
| `the default playwright context is used` | Switch back to the default context. |

## Clicking

| Step | Purpose |
|---|---|
| `click '<element>'` | Click the element. |
| `double click '<element>'` | Double-click the element. |
| `context click '<element>'` | Right-click (context menu) the element. |
| `context click '<element>' and select menu index <N>` | Right-click and select a context menu item by index. |
| `hover on '<element>'` | Hover over the element. |
| `move to element '<element>'` | Move the mouse to the element. |

## Keyboard / Input

| Step | Purpose |
|---|---|
| `type '<text>' into '<element>'` | Type text into the element. |
| `type into '<element>'` | Type text from the following DocString into the element. |
| `press '<key>' on '<element>'` | Press a key (e.g. `Enter`, `Tab`) on the element. |
| `clear '<element>'` | Clear the element's value. |

## Dropdowns / Checkboxes

| Step | Purpose |
|---|---|
| `select '<optionText>' from '<element>'` | Select an option by visible text. |
| `select value '<value>' from '<element>'` | Select an option by value attribute. |
| `check '<element>'` | Check a checkbox or radio button. |
| `uncheck '<element>'` | Uncheck a checkbox. |

## Scrolling / Window

| Step | Purpose |
|---|---|
| `scroll element '<element>' into view` | Scroll the element into the viewport. |
| `set window size to <W>x<H>` | Set the browser window size. |
| `set window width to <W>` | Set the browser window width. |
| `set window height to <H>` | Set the browser window height. |

## Dialogs / Alerts

| Step | Purpose |
|---|---|
| `accept alert` | Accept an alert/dialog. |
| `dismiss alert` | Dismiss an alert/dialog. |
| `assert that alert has been raised` | Assert that an alert has been raised. |
| `assert that alert message is '<text>'` | Assert the alert message text. |

## Assertions — Visibility / State

| Step | Purpose |
|---|---|
| `assert that '<element>' is visible` | Assert the element is visible. |
| `assert that '<element>' is not visible` | Assert the element is not visible. |
| `assert that '<element>' is visible within parent '<parent>'` | Assert element visibility within a parent. |
| `assert that '<element>' is not visible within parent '<parent>'` | Assert element is not visible within a parent. |
| `assert that '<element>' is clickable` | Assert the element is clickable. |
| `assert that '<element>' is not clickable` | Assert the element is not clickable. |
| `assert that '<element>' is enabled` | Assert the element is enabled. |
| `assert that '<element>' is disabled` | Assert the element is disabled. |
| `assert that '<element>' is selected` | Assert a checkbox / radio is checked. |
| `assert that '<element>' is not selected` | Assert a checkbox / radio is not checked. |
| `assert that '<element>' is empty` | Assert the element text is empty. |
| `assert that '<element>' is not empty` | Assert the element text is not empty. |

## Assertions — Text / Attribute / Count

| Step | Purpose |
|---|---|
| `assert that '<element>' is '<text>'` | Assert the element has exact text. |
| `assert that '<element>' contains '<text>'` | Assert the element contains text. |
| `assert that attribute '<attr>' of '<element>' matches '<value>'` | Assert attribute equals value. |
| `assert that attribute '<attr>' of '<element>' contains '<value>'` | Assert attribute contains value. |
| `assert that attribute '<attr>' of '<element>' is empty` | Assert attribute is empty. |
| `assert that attribute '<attr>' of '<element>' is not empty` | Assert attribute is not empty. |
| `assert that there are exactly '<N>' visible '<element>' elements` | Assert exact visible count. |
| `assert that there are more than '<N>' visible '<element>' elements` | Assert count > N. |
| `assert that there are less than '<N>' visible '<element>' elements` | Assert count < N. |

## File Upload / Download

| Step | Purpose |
|---|---|
| `upload '<filename>' into '<element>'` | Upload a file to an element. |
| `upload '<filename>' from download directory into '<element>'` | Upload from the download directory. |
| `click '<element>' and wait for download` | Click and wait for a file download. |
| `click '<element>' and wait for download as '<filename>'` | Click and save download with a custom filename. |
| `assert that '<filename>' is downloaded` | Assert a file is downloaded. |
| `assert that the files below are downloaded:` | Assert multiple files are downloaded (DocString). |
| `clear download directory` | Clear all files from the download directory. |

## Screenshots

| Step | Purpose |
|---|---|
| `get screenshot` | Take a screenshot of the current page. |
| `get screenshot for element '<element>'` | Take a screenshot of a specific element. |
| `highlight '<element>'` | Highlight an element on the next screenshot. |
| `blur '<element>'` | Blur an element on the next screenshot. |

---
