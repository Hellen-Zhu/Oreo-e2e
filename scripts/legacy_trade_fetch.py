"""Fetch trades of the old system by trade ID, the seed's Sample Trades (docs/sit-seed.md, "Values from the old system").

The seed books each case with the dat file of its Sample Trade (the Excel), a trade of the old system. For MO to
compare the two systems, the case also sends that trade's other values (portfolio, premium, ...). This script asks
the old system's trade query for each trade ID, as its blotter does, and keeps the responses:

  1. POST <url>, form field tradesQuery = the template with its tradeId set to the trade ID.
  2. When the trade is in a package of another ID (a back-to-back trade: its packageId is not its trade ID), the
     same query by that packageId (set in the template in place of its tradeId): the package's own trade is the one
     a case takes its values from.
  3. <out>/<trade ID>.json: {"case" (when given), "tradeId", "packageId" (when queried), "error" (when a response is
     not as expected), "fetchedAt", "trade": <response>, "package": <response> (when queried)}; fetch-log.csv beside
     it, a line per trade.

TestDataTool seed-legacy <out> then turns the responses of the trades with a case into the cases' request fields
(seed/legacy.json). The files stay outside the project (--out): they hold real trades.

    set LEGACY_ACCESS_TOKEN=<the accesstoken header of a request of the old system's blotter>
    set LEGACY_CSRF_TOKEN=<its x-csrf-token header>
    python scripts/legacy_trade_fetch.py --url <trade query URL> --template <tradesQuery.json> --file <trades.txt>
    python scripts/legacy_trade_fetch.py --url <trade query URL> --template <tradesQuery.json> <trade ID> ...

(PowerShell: $env:LEGACY_ACCESS_TOKEN = "...".) The template is the JSON of the tradesQuery form field of one query
of the blotter (DevTools, Network, Payload), kept outside the project: its userContext names the user. The script
sets its tradeId (or packageId, where the template has either), and its userContext.authToken.jwt to the access
token; the headers user and authtype are its authToken's userId and authenticationType.

The trades file is the one of cortex_dat_export.py: trade IDs, one per line or several separated by commas or spaces;
# starts a comment. A line <case ID>,<trade ID> (an argument <case ID>=<trade ID>) also names the trade's seed case,
which seed-legacy needs. Each trade is fetched once; one already fetched is reused unless --force or its file has an
error, and a case given moves to that trade's file from any other. Python 3.9 or later, nothing to install.
"""
from __future__ import annotations

import argparse
import copy
import csv
import json
import os
import re
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

CASE = re.compile(r"^SEED-[A-Z]+-[0-9]{3}$")
TRADE_ID = re.compile(r"^[A-Za-z0-9_-]+$")
SEPARATORS = re.compile(r"[\s,;]+")
HEADERS = {"case", "tradeid", "trade"}
QUERY_KEYS = ("tradeId", "packageId")
TOKEN_ENV = "LEGACY_ACCESS_TOKEN"
CSRF_ENV = "LEGACY_CSRF_TOKEN"
URL_ENV = "LEGACY_QUERY_URL"


def read_trades(file: Path | None, args: list[str]) -> list[tuple[str, str]]:
    """(trade ID, case ID or "") of the trades file and of the arguments, each trade once, in the order given."""
    lines = []
    if file:
        lines += [line.split("#", 1)[0] for line in file.read_text(encoding="utf-8-sig").splitlines()]
    lines += [arg.replace("=", ",", 1) for arg in args]
    trades: dict[str, str] = {}
    cases: dict[str, str] = {}
    for line in lines:
        cells = [c for c in SEPARATORS.split(line.strip()) if c]
        if not cells or cells[0].lower() in HEADERS:
            continue
        if CASE.match(cells[0]):
            if len(cells) != 2 or not TRADE_ID.match(cells[1]) or CASE.match(cells[1]):
                raise ValueError(f"not <case ID>,<trade ID>: {line.strip()!r}")
            pairs = [(cells[1], cells[0])]
        elif all(TRADE_ID.match(c) and not CASE.match(c) for c in cells):
            pairs = [(c, "") for c in cells]
        else:
            raise ValueError(f"not trade IDs, nor <case ID>,<trade ID>: {line.strip()!r}")
        for trade, case in pairs:
            known = trades.setdefault(trade, case)
            if case and known and known != case:
                raise ValueError(f"trade {trade} is given two cases: {known} and {case}")
            if case:
                trades[trade] = case
                if cases.setdefault(case, trade) != trade:
                    raise ValueError(f"{case} is given two trades: {cases[case]} and {trade}")
    return list(trades.items())


def trade_of(response, trade_id: str) -> dict:
    """The one trade of the response (a list of trades, or one) whose tradeId is trade_id."""
    trades = response if isinstance(response, list) else [response]
    found = [t for t in trades if isinstance(t, dict) and str(t.get("tradeId", "")) == trade_id]
    if len(found) != 1:
        raise ValueError(f"{len(found)} trades {trade_id} in the response of {len(trades)} trade(s)")
    return found[0]


def query_body(template: dict, key: str, value: str) -> dict:
    """The template with key (tradeId or packageId) set to value, in the place of the one of the two it has."""
    body, placed = {}, False
    for k, v in copy.deepcopy(template).items():
        if k in QUERY_KEYS:
            if not placed:
                body[key], placed = value, True
            continue
        body[k] = v
    if not placed:
        body[key] = value
    return body


def with_case(fetched: dict, case: str) -> dict:
    """fetched with its case set to case ("" for none), first."""
    rest = {k: v for k, v in fetched.items() if k != "case"}
    return {"case": case, **rest} if case else rest


def read_fetched(file: Path) -> dict | None:
    try:
        fetched = json.loads(file.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return fetched if isinstance(fetched, dict) else None


def write(file: Path, fetched: dict) -> None:
    file.write_text(json.dumps(fetched, indent=2, ensure_ascii=False), encoding="utf-8")


def move_cases(out: Path, trades: list[tuple[str, str]]) -> None:
    """Take from the files of out a case the trades given name for another trade: a case is in one file."""
    given = {case: trade for trade, case in trades if case}
    for file in sorted(out.glob("*.json")):
        fetched = read_fetched(file)
        case = (fetched or {}).get("case")
        if case in given and fetched.get("tradeId") != given[case]:
            write(file, with_case(fetched, ""))
            print(f"moved   {case} from {fetched.get('tradeId')} to {given[case]}")


class OldSystem:
    def __init__(self, url: str, template: dict, token: str, csrf: str, timeout: float, ca: Path | None) -> None:
        self.url, self.template, self.token, self.csrf, self.timeout = url, template, token, csrf, timeout
        self.context = ssl.create_default_context(cafile=str(ca) if ca else None)

    def query(self, key: str, value: str):
        """The response of the trade query by key (tradeId or packageId): the template, that key and its jwt set."""
        body = query_body(self.template, key, value)
        auth = (body.get("userContext") or {}).get("authToken")
        headers = {"accept": "application/json", "content-type": "application/x-www-form-urlencoded",
                   "accesstoken": self.token, "x-csrf-token": self.csrf}
        if isinstance(auth, dict):
            auth["jwt"] = self.token
            headers["user"] = str(auth.get("userId", ""))
            headers["authtype"] = str(auth.get("authenticationType", ""))
        data = urllib.parse.urlencode({"tradesQuery": json.dumps(body)}).encode("utf-8")
        request = urllib.request.Request(self.url, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=self.timeout, context=self.context) as response:
                text = response.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"{key} {value}: HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}") from None
        except (urllib.error.URLError, TimeoutError) as e:
            raise RuntimeError(f"{key} {value}: no response: {getattr(e, 'reason', e)}") from None
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            raise RuntimeError(f"{key} {value}: not JSON: {text[:300]}") from None

    def fetch(self, trade_id: str, case: str) -> dict:
        """The trade's file: its response and, for a trade in a package of another ID, the package's. A failure
        after the trade's response is the file's error, with the responses so far."""
        head = {"case": case} if case else {}
        head["tradeId"] = trade_id
        fetched_at = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        responses = {"trade": self.query("tradeId", trade_id)}
        try:
            package_id = str(trade_of(responses["trade"], trade_id).get("packageId") or "")
            if package_id and package_id != trade_id:
                head["packageId"] = package_id
                responses["package"] = self.query("packageId", package_id)
                trade_of(responses["package"], package_id)
        except (ValueError, RuntimeError) as e:
            head["error"] = str(e)
        return {**head, "fetchedAt": fetched_at, **responses}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("trades", nargs="*", help="<trade ID>, or <case ID>=<trade ID>")
    p.add_argument("--file", type=Path, help="trade IDs, one per line or comma-separated, or <case ID>,<trade ID>"
                                             " lines; # starts a comment")
    p.add_argument("--url", default=os.environ.get(URL_ENV), help=f"the trade query's URL (or {URL_ENV})")
    p.add_argument("--template", type=Path, required=True, help="the tradesQuery JSON of one query of the blotter")
    p.add_argument("--out", type=Path, default=Path.home() / "Downloads" / "seed-legacy",
                   help="where <trade ID>.json files are written (outside the project)")
    p.add_argument("--force", action="store_true", help="fetch again a trade already fetched")
    p.add_argument("--timeout", type=float, default=60, help="seconds to wait for a response")
    p.add_argument("--ca", type=Path, help="a PEM file of the CA certificates to trust, if not the system's")
    args = p.parse_args()

    if not args.url:
        p.error(f"give --url or {URL_ENV}")
    token, csrf = os.environ.get(TOKEN_ENV, "").strip(), os.environ.get(CSRF_ENV, "").strip()
    if not token or not csrf:
        p.error(f"set {TOKEN_ENV} and {CSRF_ENV} (the accesstoken and x-csrf-token headers of the blotter)")
    try:
        trades = read_trades(args.file, args.trades)
    except ValueError as e:
        p.error(str(e))
    if not trades:
        p.error("give at least one trade ID, or --file")
    template = json.loads(args.template.read_text(encoding="utf-8-sig"))
    old = OldSystem(args.url, template, token, csrf, args.timeout, args.ca)
    print(f"{len(trades)} trade(s) to fetch to {args.out}")

    args.out.mkdir(parents=True, exist_ok=True)
    move_cases(args.out, trades)
    log_path = args.out / "fetch-log.csv"
    done = failed = 0
    with log_path.open("w", newline="", encoding="utf-8") as log_file:
        log = csv.writer(log_file)
        log.writerow(["tradeId", "case", "packageId", "status", "error"])
        for trade_id, case in trades:
            file = args.out / f"{trade_id}.json"
            before = read_fetched(file) if file.is_file() else None
            try:
                if (before and not args.force and "error" not in before
                        and str(before.get("tradeId")) == trade_id):
                    fetched, status = before, "reused"
                    if case and before.get("case") != case:
                        fetched = with_case(before, case)
                        write(file, fetched)
                else:
                    fetched = old.fetch(trade_id, case or (before or {}).get("case", ""))
                    write(file, fetched)
                    status = "failed" if "error" in fetched else "fetched"
                error = fetched.get("error", "")
            except KeyboardInterrupt:
                print("stopped")
                break
            except Exception as e:                              # one trade's failure does not stop the others
                fetched, status, error = {"case": case}, "failed", str(e)
            done, failed = done + 1, failed + (status == "failed")
            case, package = fetched.get("case", ""), fetched.get("packageId", "")
            print(f"{status:7} {trade_id}" + (f" {case}" if case else "") + (f" (package {package})" if package else "")
                  + (f": {error}" if error else ""))
            log.writerow([trade_id, case, package, status, error[:300]])
    print(f"{done - failed} fetched or reused, {failed} failed of {len(trades)}; log {log_path}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
