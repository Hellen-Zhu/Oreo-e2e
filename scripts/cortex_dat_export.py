"""Export dat files from Cortex by trade ID (docs/sit-seed.md, "Dat files from Cortex").

For each trade ID given, the script does in Cortex what is done by hand:

  1. TDS Browser: Trade ID is <trade ID>, Load (F9).
  2. Double-click the trade's INSTRUMENT: the "Trade <trade ID>_1" window. Its Export > View: "Viewer - {value}".
  3. Double-click the Viewer's {Object ITradeable}: the "View" window.
  4. View's Export > Save As..., "Cortex object file (*.dat)": <out>/<trade ID>.dat.

The files stay outside the project (--out); copy each into the seed case's dat path yourself (seed-dats lists them).

Windows only, on the machine where Cortex runs, with the TDS Browser open:
    pip install pywinauto
    python scripts/cortex_dat_export.py --inspect          # the controls, to <out>/inspect-<n>.txt
    python scripts/cortex_dat_export.py <trade ID>
    python scripts/cortex_dat_export.py <trade ID> <trade ID> ...
    python scripts/cortex_dat_export.py --file trades.txt  # one trade ID per line (or comma-separated), # comments

The controls of Cortex were read from a screen recording, not from the application: run --inspect once and adjust
LOCATORS below to the names it prints. Keep the mouse and keyboard still while it runs; Ctrl+C stops it between trades.
"""
from __future__ import annotations

import argparse
import contextlib
import csv
import re
import sys
import time
from pathlib import Path

# ── What the script clicks. Adjust after --inspect: titles are regular expressions. ─────────────────────────────────
LOCATORS = {
    "backend": "uia",                                   # "uia", or "win32" for an older WinForms/MFC Cortex
    "tds_window": r".*TDS Browser.*",
    # The value box right of "Trade ID ... is". With None, the script clicks at tds_trade_id_offset instead.
    "tds_trade_id": {"control_type": "ComboBox", "found_index": 2},
    "tds_trade_id_offset": None,                        # (x, y) from the window's top left, from --where
    "tds_loaded": r"Loaded 1 trade.*",                  # the status text once the trade is loaded
    # The INSTRUMENT cell of the first row ({FXFVA}, {FXAverageRateOption}): its text, or an offset.
    "tds_instrument_cell": r"^\{.+\}$",
    "tds_instrument_offset": (877, 268),           # measured with --where: the grid draws its cells
    "trade_window": r"^Trade {trade}",                 # "Trade 8613152402_1": tabs Fields, INSTRUMENT, ...
    "export_button": r"^Export",                        # the Export button of the Trade and View windows
    # A menu item of Export (Save As..., Copy, Share to Store..., Email, View, Publish): its text, and how many
    # Down presses select it when the menu is not a UI Automation element.
    "trade_export_item": (r"^View", 5),
    "object_window": r"^Viewer - .*",                   # "Viewer - {value} <build> VS2022 ...": Import / Export / Link
    # The Viewer shows many columns; INSTRUMENT is far right. Right-click a column header, untick All, tick
    # INSTRUMENT: the Viewer then shows INSTRUMENT only, {Object ITradeable} in its first cell. Offsets are from the
    # Viewer's top left, but an item of the column list is at its offset from the list (--where's first line), else
    # clicked by its text.
    # Where the Viewer is put once it stops moving (it opens at a varying place and size): left, top, width,
    # height in physical pixels. The offsets below are from its top left, so they hold wherever it is put.
    "viewer_rect": (100, 100, 1600, 1000),
    "object_header_offset": (128, 186),                 # a column header of the Viewer, e.g. ACTION
    "column_all": ("All", (22, 28)),                   # (text, offset) of the list's All
    "column_instrument": ("INSTRUMENT", (22, 876)),          # (text, offset) of the list's INSTRUMENT
    "object_cell": r".*Object ITradeable.*",            # the Viewer's INSTRUMENT cell; or object_cell_offset
    "object_cell_offset": (160, 225),                        # {Object ITradeable} once INSTRUMENT is the only column
    "view_window": r"^View$",                           # Export, Raw view, Trade type: ...
    "view_export_item": (r"^Save As", 1),
    # Closed before each trade: every View, Viewer and Trade window open, so that each title names one window.
    "close_first": (r"^View", r"^Trade \d"),
    "save_dialog": r"Save As",
    "confirm_dialog": r"Confirm Save As",
}
WAIT = {"load": 30, "window": 15, "viewer_ready": 20, "save": 15, "between": 0.5}


# ── Cortex ──────────────────────────────────────────────────────────────────────────────────────────────────────────

class Cortex:
    def __init__(self) -> None:
        from pywinauto import Desktop

        self.desktop = Desktop(backend=LOCATORS["backend"])
        self.tds = self.window(LOCATORS["tds_window"])
        self.close_all(*LOCATORS["close_first"])

    def window(self, title_re: str, timeout: float = WAIT["window"]):
        # found_index: a window left open by an earlier try has the same title; the first one is enough
        w = self.desktop.window(title_re=title_re, found_index=0)
        w.wait("visible", timeout=timeout)
        return w

    @staticmethod
    def close_all(*titles_re: str) -> None:
        """Closes the windows an earlier try left open, so that each title names one window. The Win32 list of
        top-level windows has them all, owned windows too; each gets WM_CLOSE, as from its close button."""
        import ctypes
        from ctypes import wintypes

        user32 = ctypes.windll.user32
        patterns = [re.compile(t) for t in titles_re]

        def matching() -> list[int]:
            found = []

            def visit(hwnd, _):
                if user32.IsWindowVisible(hwnd):
                    title = ctypes.create_unicode_buffer(512)
                    user32.GetWindowTextW(hwnd, title, 512)
                    if any(p.match(title.value) for p in patterns):
                        found.append(hwnd)
                return True

            user32.EnumWindows(ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)(visit), 0)
            return found

        windows = matching()
        for hwnd in windows:
            user32.PostMessageW(hwnd, 0x0010, 0, 0)                 # WM_CLOSE
        deadline = time.time() + 5
        while windows and matching() and time.time() < deadline:
            time.sleep(0.3)
        if windows:
            print(f"       closed {len(windows)} window(s) left open")

    def menu(self, window, item) -> None:
        """Clicks the window's Export button, then the menu item: by its text, else by Down presses and Enter."""
        from pywinauto.keyboard import send_keys

        text, downs = item
        window.set_focus()
        window.child_window(title_re=LOCATORS["export_button"], found_index=0).click_input()
        time.sleep(0.5)
        try:
            self.desktop.window(control_type="Menu", found_index=0).child_window(
                title_re=text, control_type="MenuItem", found_index=0).click_input()
        except Exception:
            send_keys("{DOWN}" * downs + "{ENTER}")

    def popup_under_cursor(self, owner: int, timeout: float = 3):
        """The popup a right-click opened at the cursor: the top-level window just below and right of it, once it
        is another window than the owner's (it takes a moment to open). None when none opened."""
        import ctypes
        from ctypes import wintypes

        user32 = ctypes.windll.user32
        deadline = time.time() + timeout
        while time.time() < deadline:
            point = wintypes.POINT()
            user32.GetCursorPos(ctypes.byref(point))
            point.x += 10
            point.y += 10
            handle = user32.GetAncestor(user32.WindowFromPoint(point), 2)   # GA_ROOT
            if handle and handle != owner and user32.IsWindowVisible(handle):
                time.sleep(0.3)                                             # let it draw its items
                return self.desktop.window(handle=handle), handle
            time.sleep(0.2)
        return None

    @staticmethod
    def settle_and_place(window, rect) -> None:
        """Waits until the window keeps one place and size for 1.5 s (it fills and moves after it shows), then
        moves it to rect, so that offsets from its top left meet the same cells each time."""
        import ctypes

        handle = window.wrapper_object().handle
        last, stable_since, deadline = None, time.time(), time.time() + WAIT["viewer_ready"]
        while time.time() < deadline:
            r = window.rectangle()
            now = (r.left, r.top, r.right, r.bottom)
            if now != last:
                last, stable_since = now, time.time()
            elif time.time() - stable_since >= 1.5:
                break
            time.sleep(0.2)
        user32 = ctypes.windll.user32
        user32.ShowWindow(handle, 9)                                        # SW_RESTORE, if maximized
        user32.MoveWindow(handle, *rect, True)
        time.sleep(0.5)
        window.set_focus()
        time.sleep(0.3)

    def show_instrument_only(self, objects) -> None:
        """Right-click a column header of the Viewer, untick All, tick INSTRUMENT, close the list. Each action
        waits for its effect: the list to open (right-click again if not), then to close."""
        import ctypes

        from pywinauto.keyboard import send_keys

        user32 = ctypes.windll.user32
        objects.set_focus()
        time.sleep(0.5)
        owner = objects.wrapper_object().handle
        for _ in range(3):
            self.click_at(objects, LOCATORS["object_header_offset"], right=True)
            popup = self.popup_under_cursor(owner)
            if popup:
                break
            send_keys("{ESC}")
        else:
            raise RuntimeError("the column list did not open after 3 right-clicks")
        columns, handle = popup
        self.list_item(columns, LOCATORS["column_all"])
        self.list_item(columns, LOCATORS["column_instrument"])
        for _ in range(3):
            send_keys("{ESC}")
            deadline = time.time() + 2
            while user32.IsWindowVisible(handle) and time.time() < deadline:
                time.sleep(0.2)
            if not user32.IsWindowVisible(handle):
                break
        time.sleep(0.5)                                                     # the Viewer redraws its one column

    def list_item(self, window, item) -> None:
        """Clicks an item of an open popup list: at its offset from the list when given, else by its text."""
        text, offset = item
        if offset:
            self.click_at(window, offset)
        else:
            self.desktop.window(title_re=f"^{re.escape(text)}$", found_index=0, top_level_only=False).click_input()
        time.sleep(0.5)

    @staticmethod
    def click_at(window, offset, double: bool = False, right: bool = False) -> None:
        rect = window.rectangle()
        coords = (rect.left + offset[0], rect.top + offset[1])
        from pywinauto import mouse
        if right:
            mouse.right_click(coords=coords)
        else:
            (mouse.double_click if double else mouse.click)(coords=coords)

    def export(self, trade_id: str, target: Path) -> None:
        from pywinauto.keyboard import send_keys

        tds = self.tds
        with step("1 type the trade ID in the TDS Browser (tds_trade_id)"):
            tds.set_focus()
            if LOCATORS["tds_trade_id_offset"]:
                self.click_at(tds, LOCATORS["tds_trade_id_offset"])
            else:
                tds.child_window(**LOCATORS["tds_trade_id"]).click_input()
            send_keys("^a{BACKSPACE}" + trade_id, with_spaces=True)
            send_keys("{F9}")
        with step("2 wait for the trade to load (tds_loaded)"):
            loaded = tds.child_window(title_re=LOCATORS["tds_loaded"])
            loaded.wait("visible", timeout=WAIT["load"])
        with step("2b close the Trade ID suggestion list, which covers the INSTRUMENT column"):
            send_keys("{ESC}")                                  # the trade is loaded: the box's text no longer matters
            loaded.click_input()                                # a click outside the box closes the list too
            time.sleep(0.3)

        trade_title = LOCATORS["trade_window"].replace("{trade}", re.escape(trade_id))
        self.close_all(*LOCATORS["close_first"])
        tds.set_focus()
        with step("3 double-click the INSTRUMENT cell (tds_instrument_cell / tds_instrument_offset)"):
            if LOCATORS["tds_instrument_offset"]:
                self.click_at(tds, LOCATORS["tds_instrument_offset"], double=True)
            else:
                tds.child_window(title_re=LOCATORS["tds_instrument_cell"], found_index=0).double_click_input()
        with step("4 wait for the trade's window (trade_window)"):
            trade = self.window(trade_title)
        with step("5 Export > View in the trade's window (export_button, trade_export_item)"):
            self.menu(trade, LOCATORS["trade_export_item"])
        with step("6 wait for the Viewer (object_window)"):
            objects = self.window(LOCATORS["object_window"])

        with step("6b wait for the Viewer to stop moving, then put it at viewer_rect"):
            self.settle_and_place(objects, LOCATORS["viewer_rect"])
        if LOCATORS["object_header_offset"]:
            # once: All is a toggle, so a second pass would tick every column again
            with step("7a show the INSTRUMENT column only (object_header_offset, column_all, column_instrument)"):
                self.show_instrument_only(objects)
        view = None
        for attempt in range(1, 4):                             # 7 again when no View window opened
            with step(f"7 double-click {{Object ITradeable}}, try {attempt} (object_cell / object_cell_offset)"):
                if LOCATORS["object_cell_offset"]:
                    self.click_at(objects, LOCATORS["object_cell_offset"], double=True)
                else:
                    objects.child_window(title_re=LOCATORS["object_cell"], found_index=0).double_click_input()
            try:
                view = self.window(LOCATORS["view_window"], timeout=10)
                break
            except Exception:
                send_keys("{ESC}")
        if view is None:
            raise RuntimeError("step 8 wait for the View window (view_window): none opened after 3 tries")
        with step("9 Export > Save As... in the View window (export_button, view_export_item)"):
            self.menu(view, LOCATORS["view_export_item"])

        with step("10 save as <trade ID>.dat (save_dialog)"):
            # UI Automation lists the modal Save As under the window that opened it, not on the desktop: it is
            # looked up under View. Its File name box and Save button have the common dialog's fixed IDs, 1001
            # and 1. Without the dialog, the path is typed where the focus is: the File name box (Alt+N).
            save = view.child_window(title_re=LOCATORS["save_dialog"], control_type="Window", found_index=0)
            typed = False
            if save.exists(timeout=WAIT["window"]):
                try:
                    save.set_focus()
                    save.child_window(auto_id="1001", control_type="Edit", found_index=0).set_edit_text(
                        str(target))
                    time.sleep(0.3)
                    save.child_window(auto_id="1", control_type="Button", found_index=0).click_input()
                    typed = True
                except Exception:
                    pass
            if not typed:
                escaped = "".join("{" + c + "}" if c in "+^%~(){}[]" else c for c in str(target))
                send_keys("%n^a{BACKSPACE}" + escaped, with_spaces=True)
                time.sleep(0.3)
                send_keys("{ENTER}")
            confirm = view.child_window(title_re=LOCATORS["confirm_dialog"], control_type="Window", found_index=0)
            if confirm.exists(timeout=2):                       # the file exists (--force): overwrite, Yes
                send_keys("%y")
            deadline = time.time() + WAIT["save"]
            while not (target.is_file() and target.stat().st_size > 0):
                if time.time() > deadline:
                    raise RuntimeError(f"{target} was not written")
                time.sleep(0.3)

        for w in (view, objects, trade):                         # back to the TDS Browser for the next trade
            try:
                w.close()
            except Exception:
                pass


class step:
    """Names the step that failed: "3 double-click the INSTRUMENT cell (...): <the pywinauto error>"."""

    def __init__(self, name: str) -> None:
        self.name = name

    def __enter__(self) -> None:
        return None

    def __exit__(self, kind, error, trace) -> bool:
        if error is not None and not isinstance(error, KeyboardInterrupt):
            raise RuntimeError(f"step {self.name}: {type(error).__name__}: {error}") from error
        return False


def inspect(out: Path) -> None:
    """Writes the control tree of each open Cortex window to <out>/inspect-<n>.txt, for adjusting LOCATORS."""
    from pywinauto import Desktop

    desktop = Desktop(backend=LOCATORS["backend"])
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for w in desktop.windows():
        title = w.window_text()
        # Cortex's windows end in "<build date> VS20xx ..."; its View and Save As windows may not ("Protected View"
        # is Excel's)
        if title and (" VS20" in title or title.strip() in ("View", "Save As", "Confirm Save As")):
            n += 1
            file = out / f"inspect-{n}.txt"
            # windows() returns wrappers; print_control_identifiers is on a window specification. Its filename=
            # writes in the system code page (cp1252), which has no em space of Cortex's titles: print to UTF-8.
            with file.open("w", encoding="utf-8") as f, contextlib.redirect_stdout(f):
                desktop.window(handle=w.handle).print_control_identifiers(depth=6)
            print(f"{file}: {' '.join(title.split())!r}")
    if n == 0:
        print("No Cortex window open (TDS Browser, View, Save As).")


def where() -> None:
    """Prints the mouse position from the top left of the window under it, for the *_offset locators."""
    import ctypes
    from ctypes import wintypes

    import pywinauto  # noqa: F401 - makes this process DPI aware, as when it clicks: physical pixels both ways

    user32 = ctypes.windll.user32
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)                     # per monitor; a no-op if already set
    except Exception:
        user32.SetProcessDPIAware()
    print("Switch to Cortex (Alt+Tab) and point at the cell; the position is read in 8 seconds.")
    time.sleep(8)
    point = wintypes.POINT()
    user32.GetCursorPos(ctypes.byref(point))
    window = user32.GetAncestor(user32.WindowFromPoint(point), 2)          # GA_ROOT: the top-level window
    rect = wintypes.RECT()
    user32.GetWindowRect(window, ctypes.byref(rect))
    title = ctypes.create_unicode_buffer(512)
    user32.GetWindowTextW(window, title, 512)
    print(f"offset ({point.x - rect.left}, {point.y - rect.top}) in {' '.join(title.value.split())!r}")
    # A popup list (the Viewer's columns) is a window of its own: the offsets from the Cortex windows too.
    from pywinauto import Desktop

    for w in Desktop(backend=LOCATORS["backend"]).windows():
        name = w.window_text()
        if name and (" VS20" in name or name.strip() == "View") and w.handle != window:
            r = w.rectangle()
            print(f"offset ({point.x - r.left}, {point.y - r.top}) from {' '.join(name.split())!r}")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("trades", nargs="*", help="trade IDs to export, one <trade ID>.dat each")
    p.add_argument("--file", type=Path,
                   help="a list of trade IDs: one per line or comma-separated; # starts a comment")
    p.add_argument("--out", type=Path, default=Path.home() / "Downloads" / "seed-dats",
                   help="where <trade ID>.dat files are exported (outside the project)")
    p.add_argument("--force", action="store_true", help="export again when <trade ID>.dat already exists")
    p.add_argument("--inspect", action="store_true", help="print the controls of the open Cortex windows")
    p.add_argument("--where", action="store_true", help="print the mouse position in the window under it")
    args = p.parse_args()

    if args.inspect:
        inspect(args.out)
        return 0
    if args.where:
        where()
        return 0
    trades = list(args.trades)
    if args.file:
        for line in args.file.read_text(encoding="utf-8-sig").splitlines():
            trades += [t.strip() for t in line.split("#", 1)[0].split(",") if t.strip()]
    trades = list(dict.fromkeys(trades))                         # each trade once, in the order given
    if not trades:
        p.error("give at least one trade ID, or --file")
    args.trades = trades
    print(f"{len(trades)} trade(s) to export to {args.out}")

    args.out.mkdir(parents=True, exist_ok=True)
    log_path = args.out / "export-log.csv"
    cortex = Cortex()
    failed = 0
    with log_path.open("a", newline="", encoding="utf-8") as log_file:
        log = csv.writer(log_file)
        for trade in args.trades:
            exported = args.out / f"{trade}.dat"
            try:
                if args.force or not exported.is_file():
                    cortex.export(trade, exported)
                print(f"ok     {trade}: {exported}")
                log.writerow([time.strftime("%Y-%m-%d %H:%M:%S"), trade, "ok", ""])
            except KeyboardInterrupt:
                print("stopped")
                break
            except Exception as e:                              # one trade's failure does not stop the others
                failed += 1
                print(f"FAILED {trade}: {e}")
                log.writerow([time.strftime("%Y-%m-%d %H:%M:%S"), trade, "failed", str(e)[:300]])
            time.sleep(WAIT["between"])
    print(f"{len(args.trades) - failed} exported, {failed} failed; log {log_path}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
