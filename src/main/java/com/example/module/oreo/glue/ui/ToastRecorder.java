package com.example.module.oreo.glue.ui;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Records every toast the page shows, as it appears, so that a toast assertion does not depend on the toast still
 * being on screen when the assertion runs: a toast closes by itself after a few seconds, and under load the step
 * that checks it can start later than that.
 *
 * <p>A script in every document of the browser context (a {@code MutationObserver}) reports each toast element
 * through an exposed function: when it appears, when its type, title, description or visibility changes, and when
 * it leaves the page. A toast counts as shown once it is laid out, not hidden by CSS and not hidden by the toaster
 * (sonner's {@code data-mounted}, {@code data-visible} and {@code data-removed}, which are ignored when absent), the
 * same as the old check with {@code isVisible()}. A change of its content (a loading toast that turns into a success
 * one) is a new toast.
 *
 * <p>One recorder per browser context, since an exposed function cannot be registered twice; it starts empty for
 * every scenario. Playwright delivers the reports only while the scenario thread is inside a Playwright call, so a
 * wait for a toast polls with {@code page.waitForTimeout}, never {@code Thread.sleep}. Times are the browser's clock.
 */
public final class ToastRecorder {

    private static final Logger log = LoggerFactory.getLogger(ToastRecorder.class);
    private static final String FUNCTION = "__oreoToast";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Map<BrowserContext, ToastRecorder> BY_CONTEXT = new WeakHashMap<>();

    /**
     * Called with the CSS selector of the toast elements. Reports {@code ('seen', id, time, type, title, description,
     * shown)} on every change of those values and {@code ('gone', id, time)} when the element leaves the page; all
     * strings. Scans at most every 50 ms; the attributes observed are the ones sonner changes on its toasts.
     */
    private static final String SCRIPT = """
            (selector => {
              if (window.__oreoToastRecorder) return;
              window.__oreoToastRecorder = true;
              const doc = Math.random().toString(36).slice(2);
              let next = 0;
              const live = new Map();
              const text = (el, sel) => {
                const field = el.querySelector(sel);
                return field ? (field.innerText || field.textContent || '').replace(/\\s+/g, ' ').trim() : '';
              };
              const shown = el => {
                if (el.getAttribute('data-mounted') === 'false' || el.getAttribute('data-visible') === 'false'
                    || el.getAttribute('data-removed') === 'true') return false;
                const box = el.getBoundingClientRect();
                if (box.width === 0 || box.height === 0) return false;
                const style = getComputedStyle(el);
                return style.visibility !== 'hidden' && style.display !== 'none';
              };
              const report = (...args) => {
                try { Promise.resolve(window.__oreoToast(...args)).catch(() => {}); } catch (e) {}
              };
              const scan = () => {
                const now = String(Date.now());
                for (const [el, seen] of live) {
                  if (!el.isConnected) { live.delete(el); report('gone', seen.id, now); }
                }
                document.querySelectorAll(selector).forEach(el => {
                  let seen = live.get(el);
                  if (!seen) { seen = { id: doc + '-' + (++next), key: '' }; live.set(el, seen); }
                  const entry = [el.getAttribute('data-type') || '', text(el, '[data-title]'),
                      text(el, '[data-description]'), String(shown(el))];
                  const key = JSON.stringify(entry);
                  if (key === seen.key) return;
                  seen.key = key;
                  report('seen', seen.id, now, ...entry);
                });
              };
              let pending = false;
              const schedule = () => {
                if (pending) return;
                pending = true;
                setTimeout(() => { pending = false; scan(); }, 50);
              };
              new MutationObserver(schedule).observe(document, { subtree: true, childList: true, characterData: true,
                  attributes: true, attributeFilter: ['data-type', 'data-mounted', 'data-visible', 'data-removed'] });
              schedule();
            })""";

    private final String script;
    private final List<Toast> toasts = new ArrayList<>();
    private Object scenario;

    private ToastRecorder(String toastSelector) {
        this.script = SCRIPT + "(" + jsString(toastSelector) + ")";
    }

    /**
     * The recorder of the page's browser context, installed on the first call (also into the pages already open),
     * emptied when {@code scenario} is not the one of the previous call.
     */
    static ToastRecorder attach(Page page, Object scenario, String toastSelector) {
        BrowserContext context = page.context();
        ToastRecorder recorder;
        synchronized (BY_CONTEXT) {
            recorder = BY_CONTEXT.get(context);
        }
        if (recorder == null) {
            recorder = install(context, toastSelector);
        }
        recorder.startScenario(scenario);
        return recorder;
    }

    private static ToastRecorder install(BrowserContext context, String toastSelector) {
        ToastRecorder recorder = new ToastRecorder(toastSelector);
        context.exposeFunction(FUNCTION, args -> {
            recorder.report(args);
            return null;
        });
        context.addInitScript(recorder.script);
        for (Page open : context.pages()) {
            try {
                open.evaluate(recorder.script);
            } catch (PlaywrightException e) {
                // A page that is navigating gets the script from addInitScript
                log.debug("[ToastRecorder] not injected into {}: {}", open.url(), e.getMessage());
            }
        }
        synchronized (BY_CONTEXT) {
            BY_CONTEXT.put(context, recorder);
        }
        log.info("[ToastRecorder] recording toasts '{}'", toastSelector);
        return recorder;
    }

    private synchronized void startScenario(Object scenario) {
        if (this.scenario != scenario) {
            this.scenario = scenario;
            toasts.clear();
        }
    }

    private synchronized void report(Object[] args) {
        String id = arg(args, 1);
        long at = Long.parseLong(arg(args, 2));
        Toast last = latest(id);
        if ("gone".equals(arg(args, 0))) {
            if (last != null && last.goneAt == null) {
                last.goneAt = at;
            }
            return;
        }
        String type = arg(args, 3);
        String title = arg(args, 4);
        String description = arg(args, 5);
        boolean shown = "true".equals(arg(args, 6));
        if (last != null && last.type.equals(type) && last.title.equals(title) && last.description.equals(description)) {
            if (shown && last.shownAt == null) {
                last.shownAt = at;
            }
            return;
        }
        if (last != null && last.goneAt == null) {
            last.goneAt = at;
        }
        Toast toast = new Toast(id, type, title, description, at);
        toast.shownAt = shown ? at : null;
        toasts.add(toast);
        log.info("[ToastRecorder] {}", toast);
    }

    /** The first toast shown in this scenario that no assertion has matched yet and that {@code match} accepts. */
    public synchronized Optional<Toast> find(Predicate<Toast> match) {
        return toasts.stream().filter(t -> t.shownAt != null && !t.matched).filter(match).findFirst();
    }

    /** {@link #find}, and the toast found is taken: one toast answers one assertion. */
    public synchronized Optional<Toast> take(Predicate<Toast> match) {
        Optional<Toast> found = find(match);
        found.ifPresent(t -> t.matched = true);
        return found;
    }

    /** Every toast recorded in this scenario, one per line, or {@code none}. */
    public synchronized String describe() {
        if (toasts.isEmpty()) {
            return "none";
        }
        return toasts.stream()
                .map(t -> "\n  " + t + (t.matched ? " (matched by an earlier assertion)" : ""))
                .collect(Collectors.joining());
    }

    public static String time(long epochMillis) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(TIME);
    }

    private Toast latest(String id) {
        for (int i = toasts.size() - 1; i >= 0; i--) {
            if (toasts.get(i).id.equals(id)) {
                return toasts.get(i);
            }
        }
        return null;
    }

    private static String arg(Object[] args, int index) {
        return index < args.length && args[index] != null ? String.valueOf(args[index]) : "";
    }

    private static String jsString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** One toast as the page showed it; {@code goneAt} is when it left the page or its content changed. */
    public static final class Toast {

        private final String id;
        private final String type;
        private final String title;
        private final String description;
        private final long seenAt;
        private Long shownAt;
        private Long goneAt;
        private boolean matched;

        private Toast(String id, String type, String title, String description, long seenAt) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.description = description;
            this.seenAt = seenAt;
        }

        public String type() { return type; }

        public String title() { return title; }

        public String description() { return description; }

        @Override
        public String toString() {
            String when = shownAt == null
                    ? "in the page from " + time(seenAt) + ", never shown"
                    : "shown " + time(shownAt) + (goneAt == null ? ", still in the page" : ", gone " + time(goneAt));
            return "[type=" + type + "][title=" + title + "][description=" + description + "] " + when;
        }
    }
}
