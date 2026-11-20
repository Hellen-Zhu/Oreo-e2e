"""Export dat files from Cortex by trade ID (docs/sit-seed.md, "Dat files from Cortex").

For each trade ID given, the script does in Cortex what is done by hand:

  1. TDS Browser: Trade ID is <trade ID>, Load (F9).
  2. Open the trade's INSTRUMENT, then its {Object ITradeable}: the View window.
  3. View > Export > Save As "Cortex object file (*.dat)": <out>/<trade ID>.dat.

The files stay outside the project (--out); copy each into the seed case's dat path yourself (seed-dats lists them).

Windows only, on the machine where Cortex runs, with the TDS Browser open:
    pip install pywinauto
    python scripts/cortex_dat_export.py --inspect          # the controls, to <out>/inspect-<n>.txt
    python scripts/cortex_dat_export.py <trade ID>
    python scripts/cortex_dat_export.py <trade ID> <trade ID> ...

The controls of Cortex were read from a screen recording, not from the application: run --inspect once and adjust
LOCATORS below to the names it prints. Keep the mouse and keyboard still while it runs; Ctrl+C stops it between trades.
"""
from __future__ import annotations

import argparse
import contextlib
import csv
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
    "tds_instrument_offset": None,
    "object_window": r"^Viewer - .*",                  # "Viewer - {value} <build> VS2022 ...": Import / Export / Link
    "object_cell": r".*Object ITradeable.*",
    "object_cell_offset": None,
    "view_window": r"^View$",
    "view_export": r"Export.*",                         # the Export button of the View window
    "export_menu_item": None,                           # a menu item under Export, when it opens a menu first
    "save_dialog": r"Save As",
    "confirm_dialog": r"Confirm Save As",
}
WAIT = {"load": 30, "window": 15, "save": 15, "between": 0.5}


# ── Cortex ──────────────────────────────────────────────────────────────────────────────────────────────────────────

class Cortex:
    def __init__(self) -> None:
        from pywinauto import Desktop

        self.desktop = Desktop(backend=LOCATORS["backend"])
        self.tds = self.window(LOCATORS["tds_window"])

    def window(self, title_re: str, timeout: float = WAIT["window"]):
        w = self.desktop.window(title_re=title_re)
        w.wait("visible", timeout=timeout)
        return w

    @staticmethod
    def click_at(window, offset, double: bool = False) -> None:
        rect = window.rectangle()
        coords = (rect.left + offset[0], rect.top + offset[1])
        from pywinauto import mouse
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
            tds.child_window(title_re=LOCATORS["tds_loaded"]).wait("visible", timeout=WAIT["load"])

        with step("3 double-click the INSTRUMENT cell (tds_instrument_cell / tds_instrument_offset)"):
            if LOCATORS["tds_instrument_offset"]:
                self.click_at(tds, LOCATORS["tds_instrument_offset"], double=True)
            else:
                tds.child_window(title_re=LOCATORS["tds_instrument_cell"], found_index=0).double_click_input()
        with step("4 wait for the instrument's window (object_window)"):
            objects = self.window(LOCATORS["object_window"])

        with step("5 double-click {Object ITradeable} (object_cell / object_cell_offset)"):
            if LOCATORS["object_cell_offset"]:
                self.click_at(objects, LOCATORS["object_cell_offset"], double=True)
            else:
                objects.child_window(title_re=LOCATORS["object_cell"], found_index=0).double_click_input()
        with step("6 wait for the View window (view_window)"):
            view = self.window(LOCATORS["view_window"])

        with step("7 click Export (view_export, export_menu_item)"):
            view.child_window(title_re=LOCATORS["view_export"], found_index=0).click_input()
            if LOCATORS["export_menu_item"]:
                self.desktop.window(control_type="Menu").child_window(
                    title_re=LOCATORS["export_menu_item"]).click_input()
        with step("8 save as <trade ID>.dat (save_dialog)"):
            save = self.window(LOCATORS["save_dialog"])
            name = save.child_window(title="File name:", control_type="Edit") if LOCATORS["backend"] == "uia" \
                else save.child_window(class_name="Edit", found_index=0)
            name.set_edit_text(str(target))
            send_keys("{ENTER}")
            try:
                self.desktop.window(title_re=LOCATORS["confirm_dialog"]).wait("visible", timeout=2)
                send_keys("%y")                                   # overwrite: Yes
            except Exception:
                pass
            deadline = time.time() + WAIT["save"]
            while not (target.is_file() and target.stat().st_size > 0):
                if time.time() > deadline:
                    raise RuntimeError(f"{target} was not written")
                time.sleep(0.3)

        for w in (view, objects):                                # back to the TDS Browser for the next trade
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

    user32 = ctypes.windll.user32
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


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("trades", nargs="*", help="trade IDs to export, one <trade ID>.dat each")
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
    if not args.trades:
        p.error("give at least one trade ID")

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
