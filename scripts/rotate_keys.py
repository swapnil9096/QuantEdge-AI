#!/usr/bin/env python3
"""Interactive API-key rotation helper.

Walks the user through rotating the four credentials that were previously
committed to `stock_scanner_api/.env` (and cloned into `quantedge-ai/.env`):

  * TWELVEDATA_API_KEY
  * ALPHA_VANTAGE_API_KEY
  * POLYGON_API_KEY
  * OPENAI_API_KEY

For each key the script:

  1. Prints the dashboard URL and (optionally) opens it in the default browser.
  2. Reads a new value via `getpass` so the key never echoes to the terminal or
     shell history.
  3. Validates the new key with a live, read-only API call.
  4. If the key is different from the one in `.env.compromised_backup`, marks
     that provider as rotated.

At the end:

  * Rewrites both `stock_scanner_api/.env` and `quantedge-ai/backend/.env` with
     the new values (in-place, preserving every other line).
  * Creates `.bak` copies of the old files with mode 0600.
  * Deletes `stock_scanner_api/.env.compromised_backup` once every key has been
     successfully rotated and verified (unless --keep-backup is passed).

Usage:
    python scripts/rotate_keys.py          # interactive, live calls
    python scripts/rotate_keys.py --dry-run  # no writes, no network

Safe to run multiple times. Only rotates keys that differ from the compromised
values, and only writes to disk after every live check has succeeded.
"""

from __future__ import annotations

import argparse
import getpass
import json
import os
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional


# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

THIS_FILE = Path(__file__).resolve()
REPO_ROOT = THIS_FILE.parent.parent

ENV_TARGETS: list[Path] = [
    REPO_ROOT / "stock_scanner_api" / ".env",
    REPO_ROOT / "quantedge-ai" / "backend" / ".env",
]
COMPROMISED_BACKUP = REPO_ROOT / "stock_scanner_api" / ".env.compromised_backup"


# ---------------------------------------------------------------------------
# Colours (no external deps)
# ---------------------------------------------------------------------------


class C:
    BOLD = "\033[1m"
    DIM = "\033[2m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    CYAN = "\033[36m"
    MAG = "\033[35m"
    RESET = "\033[0m"


def say(text: str, *, color: str = "") -> None:
    print(f"{color}{text}{C.RESET}" if color else text)


def banner(text: str) -> None:
    bar = "─" * max(10, len(text) + 2)
    print()
    say(bar, color=C.CYAN)
    say(f" {text}", color=C.CYAN + C.BOLD)
    say(bar, color=C.CYAN)


# ---------------------------------------------------------------------------
# Validators — live, read-only calls
# ---------------------------------------------------------------------------


class ValidationError(Exception):
    pass


def _http_get(url: str, *, timeout: float = 12.0) -> tuple[int, str]:
    req = urllib.request.Request(url, headers={"User-Agent": "rotate_keys/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
        return exc.code, body


def _http_post_json(url: str, payload: dict, headers: dict, *, timeout: float = 15.0) -> tuple[int, str]:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json", **headers})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
        return exc.code, body


def validate_twelvedata(key: str) -> None:
    if not key:
        raise ValidationError("empty key")
    url = "https://api.twelvedata.com/time_series?" + urllib.parse.urlencode(
        {"symbol": "AAPL", "interval": "1day", "outputsize": 1, "apikey": key}
    )
    code, body = _http_get(url)
    if code != 200:
        raise ValidationError(f"HTTP {code}: {body[:200]}")
    payload = json.loads(body)
    if payload.get("status") == "error":
        raise ValidationError(payload.get("message", "error from TwelveData")[:200])


def validate_alphavantage(key: str) -> None:
    if not key:
        raise ValidationError("empty key")
    # GLOBAL_QUOTE is cheap and consumes one of the daily quota.
    url = "https://www.alphavantage.co/query?" + urllib.parse.urlencode(
        {"function": "GLOBAL_QUOTE", "symbol": "AAPL", "apikey": key}
    )
    code, body = _http_get(url)
    if code != 200:
        raise ValidationError(f"HTTP {code}: {body[:200]}")
    payload = json.loads(body)
    if "Error Message" in payload:
        raise ValidationError(payload["Error Message"][:200])
    if "Note" in payload and "Thank you for using Alpha Vantage" in payload["Note"]:
        # Rate-limit / invalid-key responses share this key; treat as unknown.
        raise ValidationError(payload["Note"][:200])
    if "Global Quote" not in payload:
        raise ValidationError("unexpected payload: no 'Global Quote' field")


def validate_polygon(key: str) -> None:
    if not key:
        raise ValidationError("empty key")
    url = f"https://api.polygon.io/v3/reference/tickers?limit=1&apiKey={urllib.parse.quote(key)}"
    code, body = _http_get(url)
    if code == 401:
        raise ValidationError("HTTP 401: unauthorized (bad key)")
    if code != 200:
        raise ValidationError(f"HTTP {code}: {body[:200]}")


def validate_openai(key: str) -> None:
    if not key:
        raise ValidationError("empty key")
    # /v1/models is a lightweight auth check.
    code, body = _http_get(
        "https://api.openai.com/v1/models",
    )  # type: ignore[arg-type]
    # Need Authorization header; rebuild the request.
    req = urllib.request.Request(
        "https://api.openai.com/v1/models",
        headers={"Authorization": f"Bearer {key}", "User-Agent": "rotate_keys/1.0"},
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            status = resp.status
            body = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        status = exc.code
        body = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
    if status == 401:
        raise ValidationError("HTTP 401: invalid_api_key")
    if status != 200:
        raise ValidationError(f"HTTP {status}: {body[:200]}")


# ---------------------------------------------------------------------------
# Provider descriptors
# ---------------------------------------------------------------------------


@dataclass
class Provider:
    env_key: str
    label: str
    dashboard_url: str
    help_text: str
    validator: Callable[[str], None]


PROVIDERS: list[Provider] = [
    Provider(
        env_key="TWELVEDATA_API_KEY",
        label="Twelve Data",
        dashboard_url="https://twelvedata.com/account/api-keys",
        help_text="Delete the old key, create a new one, copy the full value.",
        validator=validate_twelvedata,
    ),
    Provider(
        env_key="ALPHA_VANTAGE_API_KEY",
        label="Alpha Vantage",
        dashboard_url="https://www.alphavantage.co/support/#api-key",
        help_text=(
            "Alpha Vantage can't revoke individual keys; request a fresh one "
            "from the form and treat the old one as public."
        ),
        validator=validate_alphavantage,
    ),
    Provider(
        env_key="POLYGON_API_KEY",
        label="Polygon.io",
        dashboard_url="https://polygon.io/dashboard/keys",
        help_text="Revoke the old key, generate a new one, copy the value.",
        validator=validate_polygon,
    ),
    Provider(
        env_key="OPENAI_API_KEY",
        label="OpenAI",
        dashboard_url="https://platform.openai.com/api-keys",
        help_text=(
            "Revoke the old key, create a new one. "
            "Keys start with 'sk-proj-' or 'sk-' and are ~95+ chars long."
        ),
        validator=validate_openai,
    ),
]


# ---------------------------------------------------------------------------
# .env read / write
# ---------------------------------------------------------------------------


def read_env_map(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def read_compromised_keys() -> dict[str, str]:
    return read_env_map(COMPROMISED_BACKUP) if COMPROMISED_BACKUP.exists() else {}


def rewrite_env(path: Path, updates: dict[str, str], *, dry_run: bool) -> bool:
    """Replace matching KEY=... lines in-place. Returns True if file changed."""
    if not path.exists():
        say(f"  (!) skipped {path} — file does not exist", color=C.YELLOW)
        return False

    original = path.read_text(encoding="utf-8")
    lines = original.splitlines(keepends=False)
    changed = False
    new_lines: list[str] = []

    for raw in lines:
        stripped = raw.lstrip()
        if stripped.startswith("#") or "=" not in stripped:
            new_lines.append(raw)
            continue
        key_part = stripped.split("=", 1)[0].strip()
        if key_part in updates:
            new_value = updates[key_part]
            leading = raw[: len(raw) - len(raw.lstrip())]
            new_line = f"{leading}{key_part}={new_value}"
            if new_line != raw:
                changed = True
            new_lines.append(new_line)
        else:
            new_lines.append(raw)

    if not changed:
        return False

    if dry_run:
        say(f"  would write {path} (dry run)", color=C.DIM)
        return True

    backup = path.with_suffix(path.suffix + ".bak")
    shutil.copy2(path, backup)
    try:
        backup.chmod(0o600)
    except OSError:
        pass

    path.write_text("\n".join(new_lines) + ("\n" if original.endswith("\n") else ""), encoding="utf-8")
    try:
        path.chmod(0o600)
    except OSError:
        pass
    say(f"  wrote {path} (backup at {backup.name})", color=C.GREEN)
    return True


# ---------------------------------------------------------------------------
# Flow
# ---------------------------------------------------------------------------


def prompt_key(provider: Provider, compromised: str, *, open_browser: bool) -> Optional[str]:
    banner(f"{provider.label}  —  {provider.env_key}")
    say(f"Dashboard: {provider.dashboard_url}", color=C.CYAN)
    say(provider.help_text)
    if compromised:
        say(
            f"(currently compromised key starts with {compromised[:8]}… len={len(compromised)})",
            color=C.DIM,
        )

    if open_browser:
        try:
            webbrowser.open(provider.dashboard_url)
        except Exception:
            pass

    while True:
        try:
            value = getpass.getpass(f"Paste new {provider.env_key} (or 'skip', 'quit'): ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return None
        if value.lower() == "quit":
            return None
        if value.lower() == "skip":
            say("  skipped", color=C.YELLOW)
            return ""
        if not value:
            say("  empty input — try again", color=C.YELLOW)
            continue
        if compromised and value == compromised:
            say("  that's the compromised value — paste the NEW key", color=C.RED)
            continue
        return value


def run(args) -> int:
    if not any(p.exists() for p in ENV_TARGETS):
        say(f"None of the expected .env files exist: {ENV_TARGETS}", color=C.RED)
        return 2

    compromised = read_compromised_keys()
    if not compromised:
        say(
            f"No compromised backup found at {COMPROMISED_BACKUP}. "
            "Nothing to cross-check against — you can still rotate, but the script "
            "won't be able to auto-delete the backup.",
            color=C.YELLOW,
        )

    say(
        "You will be prompted for each of the 4 compromised keys in turn. "
        "Press Ctrl+C at any time to abort; nothing is written until every live "
        "check succeeds.",
    )

    rotated: dict[str, str] = {}
    skipped: list[str] = []

    for provider in PROVIDERS:
        compromised_value = compromised.get(provider.env_key, "")
        new_value = prompt_key(
            provider,
            compromised_value,
            open_browser=not args.no_browser,
        )
        if new_value is None:
            say("Aborted.", color=C.RED)
            return 1
        if new_value == "":
            skipped.append(provider.env_key)
            continue

        if args.dry_run:
            say(f"  dry-run: would validate {provider.env_key} length={len(new_value)}", color=C.DIM)
            rotated[provider.env_key] = new_value
            continue

        say("  validating live…", color=C.DIM)
        try:
            provider.validator(new_value)
        except ValidationError as exc:
            say(f"  ✗ {exc}", color=C.RED)
            say("  not accepted — try again or 'skip' to leave it alone", color=C.YELLOW)
            # Re-prompt for the same provider once.
            retry_value = prompt_key(
                provider,
                compromised_value,
                open_browser=False,
            )
            if retry_value in (None, ""):
                skipped.append(provider.env_key)
                continue
            try:
                provider.validator(retry_value)
            except ValidationError as exc2:
                say(f"  ✗ {exc2} — giving up on this provider", color=C.RED)
                skipped.append(provider.env_key)
                continue
            new_value = retry_value
        say("  ✓ accepted", color=C.GREEN)
        rotated[provider.env_key] = new_value

    banner("Summary")
    if rotated:
        say(f"Rotated: {', '.join(rotated.keys())}", color=C.GREEN)
    if skipped:
        say(f"Skipped: {', '.join(skipped)}", color=C.YELLOW)
    if not rotated:
        say("No keys rotated — nothing to do.", color=C.YELLOW)
        return 0

    # Write out.
    say("")
    say("Writing updated .env files…", color=C.BOLD)
    for target in ENV_TARGETS:
        rewrite_env(target, rotated, dry_run=args.dry_run)

    # Auto-delete backup if every provider has been rotated in this run.
    all_rotated = all(p.env_key in rotated for p in PROVIDERS)
    if all_rotated and COMPROMISED_BACKUP.exists():
        if args.dry_run:
            say(f"  would remove {COMPROMISED_BACKUP} (dry run)", color=C.DIM)
        elif args.keep_backup:
            say(f"  --keep-backup set; leaving {COMPROMISED_BACKUP} in place", color=C.YELLOW)
        else:
            COMPROMISED_BACKUP.unlink()
            say(f"  removed {COMPROMISED_BACKUP}", color=C.GREEN)
    elif not all_rotated and COMPROMISED_BACKUP.exists():
        say(
            f"  {COMPROMISED_BACKUP.name} kept — not all keys rotated. "
            "Re-run the script after rotating the rest.",
            color=C.YELLOW,
        )

    say("")
    say("Done. Restart any running services so they pick up the new values.", color=C.BOLD)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Rotate the four compromised API keys.")
    parser.add_argument("--dry-run", action="store_true", help="No writes, no network calls.")
    parser.add_argument("--no-browser", action="store_true", help="Don't open dashboards in a browser.")
    parser.add_argument(
        "--keep-backup",
        action="store_true",
        help="Leave .env.compromised_backup on disk even after all four keys have rotated.",
    )
    return run(parser.parse_args())


if __name__ == "__main__":
    sys.exit(main())
