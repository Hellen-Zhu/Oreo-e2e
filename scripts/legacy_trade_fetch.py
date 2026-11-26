"""Fetch each seed case's Sample Trade from the old system (docs/sit-seed.md, "Values from the old system").

The seed books each case with the dat file of its Sample Trade (the Excel), a trade of the old system. For MO to
compare the two systems, the case also sends that trade's other values (portfolio, premium, ...). This script asks
the old system's trade query for each Sample Trade, as its blotter does, and keeps the response:

  1. POST <url>, form field tradesQuery = the template with its tradeId set to the trade ID.
  2. When the trade is in a package of another ID (a back-to-back trade: its packageId is not its trade ID), the
     same query for the package ID: the package's own trade is the one the case takes its values from.
  3. <out>/<case>.json: {"case", "tradeId", "packageId" (when queried), "fetchedAt", "trade": <response>,
     "package": <response> (when queried)}; fetch-log.csv beside it, a line per case.

TestDataTool seed-legacy <out> then turns the responses into the cases' request fields (seed/legacy.json). The
files stay outside the project (--out): they hold real trades.

    set LEGACY_ACCESS_TOKEN=<the accesstoken header of a request of the old system's blotter>
    set LEGACY_CSRF_TOKEN=<its x-csrf-token header>
    python scripts/legacy_trade_fetch.py --url <trade query URL> --template <tradesQuery.json> --file <cases.csv>
    python scripts/legacy_trade_fetch.py --url <trade query URL> --template <tradesQuery.json> SEED-TRADE-001=<trade ID>

(PowerShell: $env:LEGACY_ACCESS_TOKEN = "...".) The template is the JSON of the tradesQuery form field of one query
of the blotter (DevTools, Network, Payload), kept outside the project: its userContext names the user. The script
sets its tradeId, and its userContext.authToken.jwt to the access token; the headers user and authtype are its
authToken's userId and authenticationType. The cases file has a line per case, <case ID>,<trade ID>; # starts a
comment. A case already fetched is reused unless --force. Python 3.9 or later, nothing to install.
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
TOKEN_ENV = "LEGACY_ACCESS_TOKEN"
CSRF_ENV = "LEGACY_CSRF_TOKEN"
URL_ENV = "LEGACY_QUERY_URL"


def read_cases(file: Path | None, pairs: list[str]) -> list[tuple[str, str]]:
    """(case ID, trade ID) of the cases file and of the CASE=TRADE arguments, each case once, in the order given."""
    lines = []
    if file:
        lines += [line.split("#", 1)[0] for line in file.read_text(encoding="utf-8-sig").splitlines()]
    lines += [pair.replace("=", ",", 1) for pair in pairs]
    cases: dict[str, str] = {}
    for line in lines:
        cells = [c.strip() for c in line.split(",")]
        if not any(cells) or cells[0].lower() == "case":
            continue
        if len(cells) < 2 or not CASE.match(cells[0]) or not TRADE_ID.match(cells[1]):
            raise ValueError(f"not <case ID>,<trade ID>: {line.strip()!r}")
        if cases.setdefault(cells[0], cells[1]) != cells[1]:
            raise ValueError(f"{cells[0]} is given two trades: {cases[cells[0]]} and {cells[1]}")
    return list(cases.items())


def trade_of(response, trade_id: str) -> dict:
    """The one trade of the response (a list of trades, or one) whose tradeId is trade_id."""
    trades = response if isinstance(response, list) else [response]
    found = [t for t in trades if isinstance(t, dict) and str(t.get("tradeId", "")) == trade_id]
    if len(found) != 1:
        raise ValueError(f"{len(found)} trades {trade_id} in the response of {len(trades)} trade(s)")
    return found[0]


class OldSystem:
    def __init__(self, url: str, template: dict, token: str, csrf: str, timeout: float, ca: Path | None) -> None:
        self.url, self.template, self.token, self.csrf, self.timeout = url, template, token, csrf, timeout
        self.context = ssl.create_default_context(cafile=str(ca) if ca else None)

    def query(self, trade_id: str):
        """The response of the trade query for trade_id: the template, its tradeId and jwt set."""
        body = copy.deepcopy(self.template)
        body["tradeId"] = trade_id
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
            raise RuntimeError(f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}") from None
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            raise RuntimeError(f"not JSON: {text[:300]}") from None

    def fetch(self, case: str, trade_id: str) -> dict:
        """The case's file: the trade's response and, for a trade in a package of another ID, the package's."""
        fetched = {"case": case, "tradeId": trade_id, "fetchedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z")}
        fetched["trade"] = self.query(trade_id)
        package_id = str(trade_of(fetched["trade"], trade_id).get("packageId") or "")
        if package_id and package_id != trade_id:
            fetched["packageId"] = package_id
            fetched["package"] = self.query(package_id)
            trade_of(fetched["package"], package_id)
        return fetched


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("cases", nargs="*", help="<case ID>=<trade ID>, e.g. SEED-TRADE-001=<trade ID>")
    p.add_argument("--file", type=Path, help="a line per case: <case ID>,<trade ID>; # starts a comment")
    p.add_argument("--url", default=os.environ.get(URL_ENV), help=f"the trade query's URL (or {URL_ENV})")
    p.add_argument("--template", type=Path, required=True, help="the tradesQuery JSON of one query of the blotter")
    p.add_argument("--out", type=Path, default=Path.home() / "Downloads" / "seed-legacy",
                   help="where <case>.json files are written (outside the project)")
    p.add_argument("--force", action="store_true", help="fetch again when <case>.json already exists")
    p.add_argument("--timeout", type=float, default=60, help="seconds to wait for a response")
    p.add_argument("--ca", type=Path, help="a PEM file of the CA certificates to trust, if not the system's")
    args = p.parse_args()

    if not args.url:
        p.error(f"give --url or {URL_ENV}")
    token, csrf = os.environ.get(TOKEN_ENV, "").strip(), os.environ.get(CSRF_ENV, "").strip()
    if not token or not csrf:
        p.error(f"set {TOKEN_ENV} and {CSRF_ENV} (the accesstoken and x-csrf-token headers of the blotter)")
    try:
        cases = read_cases(args.file, args.cases)
    except ValueError as e:
        p.error(str(e))
    if not cases:
        p.error("give at least one <case ID>=<trade ID>, or --file")
    template = json.loads(args.template.read_text(encoding="utf-8-sig"))
    old = OldSystem(args.url, template, token, csrf, args.timeout, args.ca)
    print(f"{len(cases)} case(s) to fetch to {args.out}")

    args.out.mkdir(parents=True, exist_ok=True)
    log_path = args.out / "fetch-log.csv"
    failed = 0
    with log_path.open("w", newline="", encoding="utf-8") as log_file:
        log = csv.writer(log_file)
        log.writerow(["case", "tradeId", "packageId", "status", "error"])
        for case, trade_id in cases:
            file = args.out / f"{case}.json"
            try:
                if not args.force and file.is_file():
                    fetched, status = json.loads(file.read_text(encoding="utf-8")), "reused"
                    if fetched.get("tradeId") != trade_id:
                        raise ValueError(f"{file.name} holds trade {fetched.get('tradeId')}: fetch with --force")
                else:
                    fetched, status = old.fetch(case, trade_id), "fetched"
                    file.write_text(json.dumps(fetched, indent=2, ensure_ascii=False), encoding="utf-8")
                package = fetched.get("packageId", "")
                print(f"{status:7} {case} {trade_id}" + (f" (package {package})" if package else ""))
                log.writerow([case, trade_id, package, status, ""])
            except KeyboardInterrupt:
                print("stopped")
                break
            except Exception as e:                              # one case's failure does not stop the others
                failed += 1
                print(f"FAILED  {case} {trade_id}: {e}")
                log.writerow([case, trade_id, "", "failed", str(e)[:300]])
    print(f"{len(cases) - failed} fetched or reused, {failed} failed; log {log_path}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
