"""Export the SIT seed's dat files from Cortex, one per seed case (docs/sit-seed.md, "Dat files from Cortex").

For each seed case still to export (target/seed/seed-dats.csv, written by `TestDataTool seed-dats`), the script takes
the case's Sample Trade from the Excel and does in Cortex what is done by hand:

  1. TDS Browser: Trade ID is <Sample Trade>, Load (F9).
  2. Open the trade's INSTRUMENT, then its {Object ITradeable}: the View window.
  3. View > Export > Save As "Cortex object file (*.dat)": <export dir>/<Sample Trade>.dat.

Then it copies <Sample Trade>.dat to the case's dat path in the project (the name the catalog expects), unless
--no-install. The exported files keep their trade ID names outside the project: real trade IDs never reach the repo.

Windows only, on the machine where Cortex runs, with the TDS Browser open:
    pip install pywinauto openpyxl
    python scripts/cortex_dat_export.py --excel <the seed Excel> --dry-run      # the plan, no clicks
    python scripts/cortex_dat_export.py --excel <the seed Excel> --inspect      # the controls, to adjust LOCATORS
    python scripts/cortex_dat_export.py --excel <the seed Excel> --cases SEED-TRADE-013
    python scripts/cortex_dat_export.py --excel <the seed Excel>

The controls of Cortex were read from a screen recording, not from the application: run --inspect once and adjust
LOCATORS below to the names it prints. Keep the mouse and keyboard still while it runs; Ctrl+C stops it between cases.
"""
from __future__ import annotations

import argparse
import csv
import shutil
import sys
import time
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
PLAN = PROJECT / "target" / "seed" / "seed-dats.csv"
TO_EXPORT = {"placeholder", "missing"}

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
    "object_window": r"^(?!.*TDS Browser).*\d{8} VS\d{4}.*",   # the window with Import / Compare / Export / Link
    "object_cell": r".*Object ITradeable.*",
    "object_cell_offset": None,
    "view_window": r"^View$",
    "view_export": r"Export.*",                         # the Export button of the View window
    "export_menu_item": None,                           # a menu item under Export, when it opens a menu first
    "save_dialog": r"Save As",
    "confirm_dialog": r"Confirm Save As",
}
WAIT = {"load": 30, "window": 15, "save": 15, "between": 0.5}


def read_plan(path: Path) -> list[dict]:
    if not path.is_file():
        sys.exit(f"No {path}: run first\n  mvn -Ptest-data process-test-classes -DskipTests \"-Dexec.args=seed-dats\"")
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def read_sample_trades(excel: Path, sheet: str | None, case_column: str, trade_column: str) -> dict[int, str]:
    """Case number (Case NB) -> Sample Trade, from the first row that has both column names."""
    from openpyxl import load_workbook

    ws = load_workbook(excel, read_only=True, data_only=True)[sheet] if sheet else \
        load_workbook(excel, read_only=True, data_only=True).active
    rows = ws.iter_rows(values_only=True)
    for header in rows:
        names = [str(c).strip() if c is not None else "" for c in header]
        if case_column in names and trade_column in names:
            ci, ti = names.index(case_column), names.index(trade_column)
            break
    else:
        sys.exit(f"{excel}: no row with the columns '{case_column}' and '{trade_column}'")
    trades = {}
    for row in rows:
        case, trade = row[ci], row[ti]
        if case is None or trade is None or str(case).strip() == "":
            continue
        try:
            number = int(float(str(case).strip()))
        except ValueError:
            continue
        trades[number] = str(int(trade)) if isinstance(trade, float) else str(trade).strip()
    return trades


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
        tds.set_focus()
        if LOCATORS["tds_trade_id_offset"]:
            self.click_at(tds, LOCATORS["tds_trade_id_offset"])
        else:
            tds.child_window(**LOCATORS["tds_trade_id"]).click_input()
        send_keys("^a{BACKSPACE}" + trade_id, with_spaces=True)
        send_keys("{F9}")
        tds.child_window(title_re=LOCATORS["tds_loaded"]).wait("visible", timeout=WAIT["load"])

        if LOCATORS["tds_instrument_offset"]:
            self.click_at(tds, LOCATORS["tds_instrument_offset"], double=True)
        else:
            tds.child_window(title_re=LOCATORS["tds_instrument_cell"], found_index=0).double_click_input()
        objects = self.window(LOCATORS["object_window"])

        if LOCATORS["object_cell_offset"]:
            self.click_at(objects, LOCATORS["object_cell_offset"], double=True)
        else:
            objects.child_window(title_re=LOCATORS["object_cell"], found_index=0).double_click_input()
        view = self.window(LOCATORS["view_window"])

        view.child_window(title_re=LOCATORS["view_export"], found_index=0).click_input()
        if LOCATORS["export_menu_item"]:
            self.desktop.window(control_type="Menu").child_window(
                title_re=LOCATORS["export_menu_item"]).click_input()
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

        for w in (view, objects):                                # back to the TDS Browser for the next case
            try:
                w.close()
            except Exception:
                pass


def inspect() -> None:
    from pywinauto import Desktop

    desktop = Desktop(backend=LOCATORS["backend"])
    for w in desktop.windows():
        title = w.window_text()
        if title and any(k in title for k in ("TDS", "View", "VS20", "Save As")):
            print(f"\n=== {title!r}")
            w.print_control_identifiers(depth=6)


def where() -> None:
    """Prints the mouse position from the top left of the TDS Browser, for the *_offset locators."""
    import ctypes
    from ctypes import wintypes

    from pywinauto import Desktop

    tds = Desktop(backend=LOCATORS["backend"]).window(title_re=LOCATORS["tds_window"])
    print("Point at a cell; the position is read in 5 seconds.")
    time.sleep(5)
    point = wintypes.POINT()
    ctypes.windll.user32.GetCursorPos(ctypes.byref(point))
    rect = tds.rectangle()
    print(f"offset from the TDS Browser: ({point.x - rect.left}, {point.y - rect.top})")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--excel", type=Path, help="the seed Excel (Case NB, Sample Trade)")
    p.add_argument("--sheet", help="the sheet; the active one by default")
    p.add_argument("--case-column", default="Case NB")
    p.add_argument("--trade-column", default="Sample Trade")
    p.add_argument("--plan", type=Path, default=PLAN, help="seed-dats.csv")
    p.add_argument("--out", type=Path, default=Path.home() / "Downloads" / "seed-dats",
                   help="where <trade ID>.dat files are exported (outside the project)")
    p.add_argument("--cases", help="only these cases, comma-separated (SEED-TRADE-008,SEED-TRADE-009)")
    p.add_argument("--all", action="store_true", help="also cases whose dat file is already real")
    p.add_argument("--force", action="store_true", help="export again when <trade ID>.dat already exists")
    p.add_argument("--no-install", action="store_true", help="export only, do not copy into the project")
    p.add_argument("--dry-run", action="store_true", help="print the plan, click nothing")
    p.add_argument("--inspect", action="store_true", help="print the controls of the open Cortex windows")
    p.add_argument("--where", action="store_true", help="print the mouse position in the TDS Browser")
    args = p.parse_args()

    if args.inspect:
        inspect()
        return 0
    if args.where:
        where()
        return 0
    if not args.excel:
        p.error("--excel is required")

    plan = read_plan(args.plan)
    only = {c.strip() for c in args.cases.split(",")} if args.cases else None
    trades = read_sample_trades(args.excel, args.sheet, args.case_column, args.trade_column)
    todo, skipped = [], []
    for row in plan:
        case = row["case"]
        if only is not None and case not in only:
            continue
        if row["state"] in ("e2e", "shared"):
            skipped.append((case, row["state"] + (f" ({row['sameAs']})" if row["sameAs"] else "")))
            continue
        if not args.all and row["state"] not in TO_EXPORT:
            skipped.append((case, "already real"))
            continue
        trade = trades.get(int(case.rsplit("-", 1)[1]))
        if not trade:
            skipped.append((case, f"no {args.trade_column} in the Excel"))
            continue
        todo.append((case, trade, PROJECT / row["datFile"]))

    for case, why in skipped:
        print(f"skip   {case}: {why}")
    for case, trade, dat in todo:
        print(f"export {case}: {trade}.dat -> {dat.relative_to(PROJECT)}")
    if args.dry_run or not todo:
        print(f"{len(todo)} to export, {len(skipped)} skipped" + (" (dry run)" if args.dry_run else ""))
        return 0

    args.out.mkdir(parents=True, exist_ok=True)
    log_path = args.out / "export-log.csv"
    cortex = Cortex()
    failed = 0
    with log_path.open("a", newline="", encoding="utf-8") as log_file:
        log = csv.writer(log_file)
        for case, trade, dat in todo:
            exported = args.out / f"{trade}.dat"
            try:
                if args.force or not exported.is_file():
                    cortex.export(trade, exported)
                if not args.no_install:
                    dat.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(exported, dat)
                print(f"ok     {case}: {exported.name}" + ("" if args.no_install else f" -> {dat.name}"))
                log.writerow([time.strftime("%Y-%m-%d %H:%M:%S"), case, trade, dat.name, "ok", ""])
            except KeyboardInterrupt:
                print("stopped")
                break
            except Exception as e:                              # one case's failure does not stop the others
                failed += 1
                print(f"FAILED {case}: {e}")
                log.writerow([time.strftime("%Y-%m-%d %H:%M:%S"), case, trade, dat.name, "failed", str(e)[:300]])
            time.sleep(WAIT["between"])
    print(f"{len(todo) - failed} exported, {failed} failed; log {log_path}")
    print("Then: mvn -Ptest-data process-test-classes -DskipTests \"-Dexec.args=seed-dats\" to see what is left.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
