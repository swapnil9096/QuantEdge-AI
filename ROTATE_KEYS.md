# Rotate compromised API keys

`stock_scanner_api/.env.compromised_backup` still contains the four keys that
were previously committed:

- `TWELVEDATA_API_KEY`
- `ALPHA_VANTAGE_API_KEY`
- `POLYGON_API_KEY`
- `OPENAI_API_KEY`

Treat them as public. Rotate them before running either of the two apps for
anything important.

## One command

```bash
cd /Users/swapnil.bobade_nuv/Documents/Trading
python3 scripts/rotate_keys.py
```

No extra Python packages needed — the script only uses the standard library.

For each provider the script:

1. Prints the dashboard URL and opens it in your default browser.
2. Prompts for the new key (hidden via `getpass`, so it never enters shell
   history).
3. Validates the new key with a **live, read-only** API call.
4. Rejects any paste that matches the old compromised value.

After every provider has been rotated and verified, the script:

- Rewrites both `stock_scanner_api/.env` and `quantedge-ai/backend/.env`
  in-place with the new values (makes a `.bak` of each first, chmod 600).
- Deletes `stock_scanner_api/.env.compromised_backup`.

## Options

```bash
python3 scripts/rotate_keys.py --dry-run       # no writes, no network
python3 scripts/rotate_keys.py --no-browser    # don't open dashboards
python3 scripts/rotate_keys.py --keep-backup   # leave the backup file in place
```

## Provider dashboards

| Provider | Dashboard | Notes |
|---|---|---|
| Twelve Data | <https://twelvedata.com/account/api-keys> | Delete the old key first. |
| Alpha Vantage | <https://www.alphavantage.co/support/#api-key> | Alpha Vantage can't revoke keys — treat the old one as permanently public. |
| Polygon.io | <https://polygon.io/dashboard/keys> | Revoke old, create new. |
| OpenAI | <https://platform.openai.com/api-keys> | Revoke old, create new; copy the full `sk-...` value immediately. |

## After rotation

1. Restart any running service so it picks up the new `.env`:

   ```bash
   # QuantEdge AI
   cd /Users/swapnil.bobade_nuv/Documents/Trading/quantedge-ai && ./start.sh

   # stock_scanner_api
   cd /Users/swapnil.bobade_nuv/Documents/Trading/stock_scanner_api && make start-api
   ```

2. Confirm the backup is gone:

   ```bash
   ls stock_scanner_api/.env.compromised_backup  # should say "No such file..."
   ```

3. (Optional) Remove the `.bak` files the script left next to each `.env` once
   you've verified the apps still run:

   ```bash
   rm -f stock_scanner_api/.env.bak quantedge-ai/backend/.env.bak
   ```

## Safety

- No writes happen until every key you entered has passed its live validation.
- Backups are created (`<path>.bak`) before overwriting.
- `getpass` hides your input and avoids shell history.
- No third-party dependencies, so nothing is pulled off PyPI during a rotation.
