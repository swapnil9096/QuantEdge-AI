#!/usr/bin/env python3
"""
setup_secrets.py — create or update the QuantEdge AI secrets vault.

The vault is stored at backend/secrets.enc.json (AES-GCM encrypted,
scrypt key derivation).  This script lets you initialise the vault or
add/replace individual secrets without editing the encrypted file by hand.

Usage
-----
# First-time setup (creates the vault):
python scripts/setup_secrets.py \
  --password "YourMasterPassword" \
  --add-secret ANTHROPIC_API_KEY=sk-ant-xxx \
  --add-secret TWELVEDATA_API_KEY=xxx \
  --add-secret ALPHA_VANTAGE_API_KEY=xxx

# Add / update Angel One broker keys:
python scripts/setup_secrets.py \
  --password "YourMasterPassword" \
  --add-secret ANGEL_CLIENT_ID=Axxxxx \
  --add-secret ANGEL_PASSWORD=yourpassword \
  --add-secret ANGEL_TOTP_SECRET=BASE32SECRETHERE \
  --add-secret ANGEL_API_KEY=yourApiKey

# List keys stored in the vault (values are hidden):
python scripts/setup_secrets.py --password "YourMasterPassword" --list

# Remove a key from the vault:
python scripts/setup_secrets.py --password "YourMasterPassword" --remove-secret ANGEL_PASSWORD

Notes
-----
- Never commit backend/secrets.enc.json to git (it is in .gitignore).
- On Render / Koyeb you can instead set secrets as plain environment variables
  in the hosting dashboard — the backend reads them at startup and they work
  just the same as vault secrets.  The vault is mainly useful for local dev and
  self-hosted deployments where you want secrets encrypted at rest.
"""

import argparse
import base64
import json
import os
import secrets
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Crypto helpers — mirror of what main.py does so the vault is compatible
# ---------------------------------------------------------------------------

try:
    from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.backends import default_backend
except ImportError:
    sys.exit(
        "ERROR: 'cryptography' package not installed.\n"
        "Run:  pip install cryptography"
    )

SCRYPT_N = 2 ** 17
SCRYPT_R = 8
SCRYPT_P = 1
AAD = b"quantedge-secrets-v1"

# Allowlisted keys — must match main.py SECRET_KEYS
ALLOWED_KEYS = {
    "TWELVEDATA_API_KEY",
    "ALPHA_VANTAGE_API_KEY",
    "POLYGON_API_KEY",
    "OPENAI_API_KEY",
    "ANTHROPIC_API_KEY",
    "PORTKEY_API_KEY",
    "PORTKEY_VIRTUAL_KEY",
    "PORTKEY_CONFIG",
    "TELEGRAM_BOT_TOKEN",
    "TELEGRAM_CHAT_ID",
    "ANGEL_CLIENT_ID",
    "ANGEL_PASSWORD",
    "ANGEL_TOTP_SECRET",
    "ANGEL_API_KEY",
    "MASTER_PASSWORD",
}


def _derive_key(password: str, salt: bytes) -> bytes:
    kdf = Scrypt(
        salt=salt,
        length=32,
        n=SCRYPT_N,
        r=SCRYPT_R,
        p=SCRYPT_P,
        backend=default_backend(),
    )
    return kdf.derive(password.encode("utf-8"))


def _encrypt(plaintext: dict, password: str) -> dict:
    salt = secrets.token_bytes(32)
    nonce = secrets.token_bytes(12)
    key = _derive_key(password, salt)
    ciphertext = AESGCM(key).encrypt(nonce, json.dumps(plaintext).encode("utf-8"), AAD)
    return {
        "salt": base64.b64encode(salt).decode(),
        "nonce": base64.b64encode(nonce).decode(),
        "ciphertext": base64.b64encode(ciphertext).decode(),
        "scrypt_n": SCRYPT_N,
        "scrypt_r": SCRYPT_R,
        "scrypt_p": SCRYPT_P,
        "aad": AAD.decode("ascii"),
    }


def _decrypt(blob: dict, password: str) -> dict:
    salt = base64.b64decode(blob["salt"])
    nonce = base64.b64decode(blob["nonce"])
    ciphertext = base64.b64decode(blob["ciphertext"])
    key = _derive_key(password, salt)
    aad = blob.get("aad", AAD.decode("ascii")).encode("ascii")
    try:
        plaintext = AESGCM(key).decrypt(nonce, ciphertext, aad)
    except Exception:
        sys.exit("ERROR: Wrong master password — could not decrypt the vault.")
    return json.loads(plaintext)


# ---------------------------------------------------------------------------
# Vault path
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parent.parent
VAULT_PATH = REPO_ROOT / "backend" / "secrets.enc.json"


def load_vault(password: str) -> dict:
    if not VAULT_PATH.exists():
        return {}
    blob = json.loads(VAULT_PATH.read_text(encoding="utf-8"))
    return _decrypt(blob, password)


def save_vault(secrets_dict: dict, password: str) -> None:
    VAULT_PATH.parent.mkdir(parents=True, exist_ok=True)
    blob = _encrypt(secrets_dict, password)
    VAULT_PATH.write_text(json.dumps(blob, indent=2), encoding="utf-8")
    print(f"Vault saved → {VAULT_PATH}")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create or update the QuantEdge AI secrets vault.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--password",
        required=False,
        help="Master password (or set MASTER_PASSWORD env var)",
    )
    parser.add_argument(
        "--add-secret",
        metavar="KEY=VALUE",
        action="append",
        default=[],
        dest="add_secrets",
        help="Add or update a secret (can be repeated)",
    )
    parser.add_argument(
        "--remove-secret",
        metavar="KEY",
        action="append",
        default=[],
        dest="remove_secrets",
        help="Remove a secret from the vault (can be repeated)",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List keys currently stored in the vault (values hidden)",
    )
    args = parser.parse_args()

    password = args.password or os.getenv("MASTER_PASSWORD", "")
    if not password:
        sys.exit(
            "ERROR: master password required.\n"
            "Pass --password or set MASTER_PASSWORD env var."
        )

    # Load existing vault (or start empty)
    current = load_vault(password)

    if args.list:
        if not current:
            print("Vault is empty or does not exist.")
        else:
            print(f"Keys in vault ({len(current)}):")
            for k in sorted(current):
                print(f"  {k}")
        return

    changed = False

    for entry in args.add_secrets:
        if "=" not in entry:
            print(f"WARNING: Skipping '{entry}' — expected KEY=VALUE format.")
            continue
        key, _, value = entry.partition("=")
        key = key.strip()
        if key not in ALLOWED_KEYS:
            print(f"WARNING: '{key}' is not in the allowed key list — skipping.")
            print(f"  Allowed keys: {', '.join(sorted(ALLOWED_KEYS))}")
            continue
        current[key] = value
        print(f"  Set {key}")
        changed = True

    for key in args.remove_secrets:
        if key in current:
            del current[key]
            print(f"  Removed {key}")
            changed = True
        else:
            print(f"  (Key '{key}' not found in vault — nothing to remove)")

    if not changed and not args.list:
        print("No changes. Use --add-secret KEY=VALUE or --remove-secret KEY.")
        return

    save_vault(current, password)
    print(f"\nDone. Vault contains {len(current)} secret(s).")
    print("\nRestart the backend server so it picks up the updated vault.")
    print("Then unlock with your master password in the app.")


if __name__ == "__main__":
    main()
