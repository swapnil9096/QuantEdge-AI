# Secrets Rotation Checklist

The previously-committed `stock_scanner_api/.env` exposed real API keys. Treat them as
compromised and rotate **every one of them** before doing anything else. A local-only
backup of the old values has been written to `stock_scanner_api/.env.compromised_backup`
(which is ignored by git) so you can revoke them on each provider's dashboard.

## 1. Revoke / rotate each key

| Provider | Dashboard URL | Key to revoke (prefix only) |
|----------|--------------|-----------------------------|
| OpenAI | https://platform.openai.com/api-keys | `sk-proj-Xe8SM1NS...` |
| Alpha Vantage | https://www.alphavantage.co/support/#api-key | `FZOZF8V...` |
| Twelve Data | https://twelvedata.com/account/api-keys | `b62949e5f4384...` |
| Polygon.io | https://polygon.io/dashboard/keys | `xf87zsavXfP_...` |

For each provider:

1. Delete / disable the old key.
2. Issue a new key.
3. Paste it into your **local** `stock_scanner_api/.env` (never commit).
4. If the app is already deployed, update the key in your secret manager
   (Render env, AWS SSM, Doppler, 1Password, etc.) and redeploy.

## 2. Verify the repo never commits secrets again

- `stock_scanner_api/.env` is now empty of secrets and will be ignored by git thanks to
  the root `.gitignore` (`.env` is blocked, `.env.example` is allowed).
- Sanity check: `git check-ignore -v stock_scanner_api/.env` should report the rule.
- If this repo is ever pushed to a remote, scan history with
  `git log --all --full-history -p -- stock_scanner_api/.env` to confirm no earlier
  commit leaks the values. If it does, rewrite history
  (`git filter-repo --path stock_scanner_api/.env --invert-paths`) and force-push.

## 3. Delete the local backup once rotation is complete

```bash
rm stock_scanner_api/.env.compromised_backup
```

That file only exists so you can copy each compromised key into the provider UI and
revoke it. Once every provider reports the key disabled, the backup must be removed.
