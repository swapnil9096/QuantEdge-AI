#!/usr/bin/env python3
"""One-time setup: encrypt QuantEdge AI API keys into backend/secrets.enc.json.

Usage:
    # Interactive — reads current .env, prompts for any missing keys, asks for password.
    python scripts/setup_secrets.py

    # Overwrite an existing vault:
    python scripts/setup_secrets.py --force

    # Also strip the secret keys from backend/.env (keep non-secret config):
    python scripts/setup_secrets.py --strip-env

    # Change the master password (keeps the same secrets):
    python scripts/setup_secrets.py --change-password

After setup:
    1. git add quantedge-ai/backend/secrets.enc.json
    2. Restart the backend (./start.sh)
    3. Open the UI — it will prompt for the password to unlock the app.
"""

from __future__ import annotations

import argparse
import base64
import getpass
import json
import os
import secrets as secrets_mod
import subprocess
import sys
from pathlib import Path
from typing import Optional

REPO_ROOT = Path(__file__).resolve().parent.parent
VAULT_PATH = REPO_ROOT / "quantedge-ai" / "backend" / "secrets.enc.json"
ENV_PATH = REPO_ROOT / "quantedge-ai" / "backend" / ".env"


# ---------------------------------------------------------------------------
# Auto-discover: re-exec under the backend venv if this Python doesn't have
# `cryptography` / `PyJWT`. Lets `python3 scripts/setup_secrets.py` just work
# without asking users to remember to activate the venv first.
# ---------------------------------------------------------------------------

def _python_has_crypto(python_path: Path) -> bool:
    try:
        result = subprocess.run(
            [str(python_path), "-c", "import cryptography, jwt"],
            capture_output=True,
            timeout=10,
        )
        return result.returncode == 0
    except Exception:
        return False


def _candidate_venv_pythons() -> list[Path]:
    # Covers macOS/Linux (bin/python) and Windows (Scripts/python.exe).
    roots = [
        REPO_ROOT / "quantedge-ai" / "backend" / ".venv",
        REPO_ROOT / "quantedge-ai" / "backend" / "venv",
        REPO_ROOT / ".venv",
    ]
    candidates: list[Path] = []
    for root in roots:
        for rel in ("bin/python", "bin/python3", "Scripts/python.exe"):
            candidates.append(root / rel)
    return [c for c in candidates if c.exists()]


def _auto_reexec_if_missing_crypto() -> None:
    try:
        import cryptography  # noqa: F401
        import jwt  # noqa: F401

        return  # already have everything we need
    except ImportError:
        pass

    # Guard against infinite recursion — only attempt the re-exec once.
    if os.environ.get("QE_SETUP_REEXECED") == "1":
        return

    for python_path in _candidate_venv_pythons():
        if not _python_has_crypto(python_path):
            continue
        print(
            f"[setup_secrets] current Python is missing 'cryptography' / 'PyJWT'. "
            f"Re-executing under {python_path} which has them.",
            file=sys.stderr,
        )
        env = dict(os.environ)
        env["QE_SETUP_REEXECED"] = "1"
        # os.execve replaces the current process entirely — argv, stdin, stdout,
        # stderr, and the terminal all flow through transparently. The new
        # interpreter will run this same script from the beginning.
        os.execve(str(python_path), [str(python_path), __file__, *sys.argv[1:]], env)
        # unreachable


_auto_reexec_if_missing_crypto()

SECRET_KEYS: list[str] = [
    "TWELVEDATA_API_KEY",
    "ALPHA_VANTAGE_API_KEY",
    "POLYGON_API_KEY",
    "OPENAI_API_KEY",
    "ANTHROPIC_API_KEY",
    "PORTKEY_API_KEY",
    "PORTKEY_VIRTUAL_KEY",
    "PORTKEY_CONFIG",
    "TELEGRAM_BOT_TOKEN",
]

VAULT_AAD = b"quantedge-secrets-v1"
SCRYPT_N = 2 ** 17
SCRYPT_R = 8
SCRYPT_P = 1
MIN_PASSWORD_LEN = 12


def _stderr(msg: str) -> None:
    print(msg, file=sys.stderr)


def _import_crypto():
    try:
        from cryptography.hazmat.backends import default_backend
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

        return default_backend, AESGCM, Scrypt
    except ImportError:
        reexeced = os.environ.get("QE_SETUP_REEXECED") == "1"
        msg = [
            "Missing 'cryptography' package. This script tried to auto-switch to a",
            "venv that has it, but none was found (or the venv is missing the deps).",
            "",
            "Fix one of these:",
            "",
            "  1) Install the backend deps into a venv (recommended):",
            "       cd quantedge-ai/backend",
            "       python3 -m venv .venv",
            "       .venv/bin/pip install -r requirements.txt",
            "     Then rerun from the repo root:",
            "       python3 scripts/setup_secrets.py",
            "",
            "  2) Install cryptography + PyJWT for the current Python:",
            "       python3 -m pip install --user 'cryptography>=42' 'PyJWT>=2.8'",
        ]
        if reexeced:
            msg.insert(
                0,
                "(Re-exec already attempted — still missing. Deps really aren't installed anywhere we can find.)",
            )
        _stderr("\n".join(msg))
        sys.exit(2)


def load_env(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        out[key.strip()] = value.strip()
    return out


def rewrite_env_without_secrets(path: Path) -> int:
    """Keep non-secret config, drop the whitelisted secret keys. Returns number removed."""
    if not path.exists():
        return 0
    lines = path.read_text(encoding="utf-8").splitlines()
    removed = 0
    keep: list[str] = []
    for raw in lines:
        stripped = raw.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            keep.append(raw)
            continue
        key = stripped.split("=", 1)[0].strip()
        if key in SECRET_KEYS:
            removed += 1
            keep.append(f"# {key}=   # moved to secrets.enc.json — unlock via UI password")
        else:
            keep.append(raw)
    path.write_text("\n".join(keep) + "\n", encoding="utf-8")
    return removed


def derive_key(password: str, salt: bytes, n: int, r: int, p: int, Scrypt, default_backend) -> bytes:
    kdf = Scrypt(salt=salt, length=32, n=n, r=r, p=p, backend=default_backend())
    return kdf.derive(password.encode("utf-8"))


def encrypt_payload(payload: dict, password: str) -> dict:
    default_backend, AESGCM, Scrypt = _import_crypto()
    salt = secrets_mod.token_bytes(16)
    nonce = secrets_mod.token_bytes(12)
    key = derive_key(password, salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, Scrypt, default_backend)
    aead = AESGCM(key)
    plaintext = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    ciphertext = aead.encrypt(nonce, plaintext, VAULT_AAD)
    return {
        "version": 1,
        "kdf": "scrypt",
        "scrypt_n": SCRYPT_N,
        "scrypt_r": SCRYPT_R,
        "scrypt_p": SCRYPT_P,
        "salt": base64.b64encode(salt).decode("ascii"),
        "nonce": base64.b64encode(nonce).decode("ascii"),
        "aad": VAULT_AAD.decode("ascii"),
        "ciphertext": base64.b64encode(ciphertext).decode("ascii"),
    }


def decrypt_payload(blob: dict, password: str) -> dict:
    default_backend, AESGCM, Scrypt = _import_crypto()
    salt = base64.b64decode(blob["salt"])
    nonce = base64.b64decode(blob["nonce"])
    ciphertext = base64.b64decode(blob["ciphertext"])
    key = derive_key(
        password,
        salt,
        int(blob.get("scrypt_n", SCRYPT_N)),
        int(blob.get("scrypt_r", SCRYPT_R)),
        int(blob.get("scrypt_p", SCRYPT_P)),
        Scrypt,
        default_backend,
    )
    aead = AESGCM(key)
    aad = blob.get("aad", VAULT_AAD.decode("ascii")).encode("ascii")
    plaintext = aead.decrypt(nonce, ciphertext, aad)
    return json.loads(plaintext)


def prompt_password(confirm: bool = True, label: str = "Master password") -> str:
    while True:
        pw = getpass.getpass(f"{label} (min {MIN_PASSWORD_LEN} chars): ")
        if len(pw) < MIN_PASSWORD_LEN:
            print(f"Too short ({len(pw)} chars). Use at least {MIN_PASSWORD_LEN}.")
            continue
        if confirm:
            pw2 = getpass.getpass("Confirm password: ")
            if pw != pw2:
                print("Passwords don't match. Try again.")
                continue
        return pw


def abbreviate(value: str) -> str:
    if not value:
        return ""
    if len(value) <= 10:
        return "*" * len(value)
    return f"{value[:5]}…{value[-4:]}"


def gather_secrets_interactive(existing: dict[str, str]) -> dict[str, str]:
    print()
    print("Enter API keys. Press ENTER to keep existing value (shown abbreviated) or skip if empty.")
    print("Leave blank to skip a key you don't use.")
    print()
    out: dict[str, str] = {}
    for key in SECRET_KEYS:
        default = existing.get(key, "")
        hint = f"  [current: {abbreviate(default)}]" if default else "  [not set]"
        try:
            raw = getpass.getpass(f"{key}{hint}: ")
        except (EOFError, KeyboardInterrupt):
            print()
            raise SystemExit("Aborted.")
        value = raw.strip() or default
        if value:
            out[key] = value
    return out


def cmd_setup(force: bool, strip_env: bool) -> int:
    existing_env = load_env(ENV_PATH)

    if VAULT_PATH.exists() and not force:
        _stderr(
            f"Vault already exists at {VAULT_PATH}.\n"
            f"  - Use --force to overwrite.\n"
            f"  - Or use --change-password to change just the password."
        )
        return 1

    print(f"Writing vault to {VAULT_PATH}")
    if not existing_env:
        print(f"(no existing .env at {ENV_PATH} — all keys will start empty)")

    secrets_dict = gather_secrets_interactive(existing_env)
    if not secrets_dict:
        _stderr("No keys provided. Aborting.")
        return 1
    print(f"\nAbout to encrypt {len(secrets_dict)} secret(s): {', '.join(sorted(secrets_dict.keys()))}")

    password = prompt_password()

    blob = encrypt_payload(secrets_dict, password)
    VAULT_PATH.parent.mkdir(parents=True, exist_ok=True)
    VAULT_PATH.write_text(json.dumps(blob, indent=2), encoding="utf-8")
    try:
        VAULT_PATH.chmod(0o600)
    except OSError:
        pass
    print(f"✓ Wrote {VAULT_PATH}")

    if strip_env and ENV_PATH.exists():
        n = rewrite_env_without_secrets(ENV_PATH)
        print(f"✓ Stripped {n} secret(s) from {ENV_PATH}")

    print()
    print("Next steps:")
    print("  1. Save the master password in your password manager (1Password / Apple Keychain / Bitwarden).")
    print("     If you lose it, there is NO RECOVERY — you'll have to rotate every API key and re-run setup.")
    print("  2. git add quantedge-ai/backend/secrets.enc.json")
    print("  3. Restart the backend: cd quantedge-ai && ./start.sh")
    print("  4. Open http://localhost:3000 — the UI will prompt for the password.")
    return 0


def cmd_change_password() -> int:
    if not VAULT_PATH.exists():
        _stderr(f"No vault at {VAULT_PATH}. Run setup first.")
        return 1
    blob = json.loads(VAULT_PATH.read_text(encoding="utf-8"))
    print("Enter current password:")
    current = getpass.getpass("Current password: ")
    try:
        secrets_dict = decrypt_payload(blob, current)
    except Exception as exc:  # covers InvalidTag
        _stderr(f"Decryption failed — password is wrong. ({exc})")
        return 1
    print("OK — decrypted. Now set a new password.")
    new_pw = prompt_password(label="New master password")
    new_blob = encrypt_payload(secrets_dict, new_pw)
    VAULT_PATH.write_text(json.dumps(new_blob, indent=2), encoding="utf-8")
    try:
        VAULT_PATH.chmod(0o600)
    except OSError:
        pass
    print(f"✓ Password changed. {len(secrets_dict)} secret(s) re-encrypted.")
    return 0


def cmd_add_secret(assignment: str) -> int:
    """Add or overwrite one secret in an existing vault. Form: KEY=VALUE.

    KEY must be in the SECRET_KEYS whitelist. Decrypts the existing vault with
    the master password, inserts/overwrites the key, re-encrypts in place.
    """
    if "=" not in assignment:
        _stderr("Expected KEY=VALUE, e.g. --add-secret TELEGRAM_BOT_TOKEN=8012345678:AA...")
        return 2
    key, _, value = assignment.partition("=")
    key = key.strip()
    value = value.strip()
    if not key or not value:
        _stderr("Both KEY and VALUE are required (got empty).")
        return 2
    if key not in SECRET_KEYS:
        _stderr(
            f"Unknown key '{key}'. Allowed:\n  " + "\n  ".join(SECRET_KEYS) +
            "\n\nIf you need to add a new secret category, update SECRET_KEYS in this script"
            " and the matching whitelist in quantedge-ai/backend/main.py."
        )
        return 2
    if not VAULT_PATH.exists():
        _stderr(
            f"No vault at {VAULT_PATH}. Run `python scripts/setup_secrets.py` first to create one."
        )
        return 1

    blob = json.loads(VAULT_PATH.read_text(encoding="utf-8"))
    print("Decrypting current vault ...")
    password = getpass.getpass("Master password: ")
    try:
        current = decrypt_payload(blob, password)
    except Exception as exc:
        _stderr(f"Decryption failed: wrong password? ({exc})")
        return 1

    existed = key in current
    current[key] = value
    new_blob = encrypt_payload(current, password)
    VAULT_PATH.write_text(json.dumps(new_blob, indent=2), encoding="utf-8")
    try:
        VAULT_PATH.chmod(0o600)
    except OSError:
        pass
    action = "updated" if existed else "added"
    print(f"✓ {action} {key} (value length {len(value)}).  Total secrets in vault: {len(current)}.")
    print("\nRestart the backend so the new value is loaded on next /unlock.")
    return 0


def cmd_remove_secret(key: str) -> int:
    key = key.strip()
    if key not in SECRET_KEYS:
        _stderr(f"Unknown key '{key}'. Allowed: {', '.join(SECRET_KEYS)}")
        return 2
    if not VAULT_PATH.exists():
        _stderr(f"No vault at {VAULT_PATH}.")
        return 1
    blob = json.loads(VAULT_PATH.read_text(encoding="utf-8"))
    password = getpass.getpass("Master password: ")
    try:
        current = decrypt_payload(blob, password)
    except Exception as exc:
        _stderr(f"Decryption failed: {exc}")
        return 1
    if key not in current:
        print(f"{key} wasn't in the vault — nothing to do.")
        return 0
    del current[key]
    new_blob = encrypt_payload(current, password)
    VAULT_PATH.write_text(json.dumps(new_blob, indent=2), encoding="utf-8")
    try:
        VAULT_PATH.chmod(0o600)
    except OSError:
        pass
    print(f"✓ Removed {key}. {len(current)} secret(s) remain in the vault.")
    return 0


def cmd_verify(password: Optional[str] = None) -> int:
    if not VAULT_PATH.exists():
        _stderr(f"No vault at {VAULT_PATH}")
        return 1
    blob = json.loads(VAULT_PATH.read_text(encoding="utf-8"))
    pw = password if password is not None else getpass.getpass("Password: ")
    try:
        secrets_dict = decrypt_payload(blob, pw)
    except Exception as exc:
        _stderr(f"Decryption failed: {exc}")
        return 1
    print(f"✓ Vault decrypted successfully.  {len(secrets_dict)} secret(s) stored:")
    for key in sorted(secrets_dict):
        print(f"    {key} = {abbreviate(secrets_dict[key])}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Manage QuantEdge AI encrypted secrets vault.")
    parser.add_argument("--force", action="store_true", help="Overwrite existing vault (creates a fresh one).")
    parser.add_argument(
        "--strip-env",
        action="store_true",
        help="After writing the vault, remove the secret keys from backend/.env (non-secrets are preserved).",
    )
    parser.add_argument(
        "--change-password",
        action="store_true",
        help="Change the master password without re-entering secrets.",
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Decrypt the existing vault to confirm the password works. No writes.",
    )
    parser.add_argument(
        "--add-secret",
        metavar="KEY=VALUE",
        help=(
            "Add or overwrite a single secret in the existing vault "
            "(e.g. --add-secret TELEGRAM_BOT_TOKEN=8012:AA...). Keeps all other secrets."
        ),
    )
    parser.add_argument(
        "--remove-secret",
        metavar="KEY",
        help="Remove a single secret from the existing vault.",
    )
    args = parser.parse_args()

    if args.change_password:
        return cmd_change_password()
    if args.verify:
        return cmd_verify()
    if args.add_secret:
        return cmd_add_secret(args.add_secret)
    if args.remove_secret:
        return cmd_remove_secret(args.remove_secret)
    return cmd_setup(force=args.force, strip_env=args.strip_env)


if __name__ == "__main__":
    sys.exit(main())
