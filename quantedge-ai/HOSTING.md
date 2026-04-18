# Hosting QuantEdge AI (free forever, zero hardware)

End state: a URL like `https://quantedge-<your-org>.koyeb.app` that works from
any device, anywhere, with HTTPS and always-on (no cold starts). The
paper-trade monitor keeps running 24/7 in Koyeb's infra; your phone gets
Telegram pings even when your laptop is off.

Cost: **₹0 / month** on Koyeb's free Eco tier — 512 MB RAM, 0.1 vCPU,
1 GB volume, no credit card required to start.

> **An alternative Fly.io config** (paid, ~₹420/mo) lives in this repo too —
> see [Appendix — Fly.io](#appendix--flyio-paid-alternative). Same Docker image,
> different target.

---

## Prerequisites (one-time, ~10 minutes)

You need three things:

1. **A GitHub account** — [github.com/signup](https://github.com/signup). Free.
2. **A Koyeb account** — [app.koyeb.com/auth/signup](https://app.koyeb.com/auth/signup). Free. Sign up with GitHub for the smoothest OAuth flow.
3. **`git` and SSH keys** on your Mac (you already have git; add a GitHub SSH key if you haven't).

Add a GitHub SSH key if the command `ssh -T git@github.com` doesn't greet you:

```bash
ssh-keygen -t ed25519 -C "your.email@example.com"
cat ~/.ssh/id_ed25519.pub         # copy this into github.com/settings/ssh
ssh -T git@github.com             # should respond "Hi <you>! You've successfully authenticated..."
```

---

## Deploy flow (first time)

### 1. Initialise git in the project

From the Trading repo root (not just quantedge-ai — we want the secrets script + scripts folder in too):

```bash
cd /Users/swapnil.bobade_nuv/Documents/Trading

# If this directory isn't a git repo yet:
git init
git branch -M main
git add .
git status                  # review what's about to be committed
```

Check the output of `git status` — you should NOT see:

- `quantedge-ai/backend/.env` (plaintext API keys)
- `quantedge-ai/backend/data/quantedge.db` (runtime DB)
- `.venv/`, `node_modules/`, `__pycache__/`

You SHOULD see:

- `quantedge-ai/backend/secrets.enc.json` ✅ (encrypted vault, safe to push)
- `quantedge-ai/Dockerfile`, `koyeb.yaml`, `backend/`, `frontend/src/`

If `.env` appears, stop and run `python3 scripts/setup_secrets.py --strip-env`
first. Then re-run `git add .`.

Commit:

```bash
git commit -m "Initial QuantEdge AI deploy"
```

### 2. Create a PRIVATE GitHub repo

- Go to [github.com/new](https://github.com/new).
- Repo name: `quantedge-ai` (or anything).
- **Choose PRIVATE**. Your encrypted vault is committed — private is still
  safer than public since it's one more layer of protection.
- **Don't** tick "Initialize with README" / `.gitignore` / license. Just create
  the empty repo.

Copy the SSH URL from the green **Code** button — looks like:
```
git@github.com:<your-user>/quantedge-ai.git
```

### 3. Push your code

```bash
git remote add origin git@github.com:<your-user>/quantedge-ai.git
git push -u origin main
```

First push can take ~15 s (your repo is ~15 MB without the venv / node_modules).

### 4. Connect Koyeb to the repo

1. Log in to [app.koyeb.com](https://app.koyeb.com).
2. Click **Create Service** → **GitHub**.
3. Authorize Koyeb to see your repos (OAuth pop-up). Select "Only select
   repositories" and pick just `quantedge-ai` for least privilege.
4. Pick the repo → **Next**.
5. **Configure builder**:
   - Builder: **Dockerfile**
   - Dockerfile path: `quantedge-ai/Dockerfile`
   - Work directory: `quantedge-ai`
   - Branch: `main`
   - Auto-deploy: **on** (so every `git push` redeploys)
6. **Configure service**:
   - Service name: `quantedge`
   - Port: **8000** · Protocol: **HTTP**
   - Route: `/` → port `8000`
   - Instance type: **Eco** (free)
   - Regions: **Frankfurt (fra)** — closest free region to India
   - Scaling: min 1, max 1
7. **Health check**:
   - Type: HTTP · Port: 8000 · Path: `/health`
   - Grace period: 30 s · Interval: 30 s · Timeout: 10 s
8. **Environment variables** (copy-paste from `koyeb.yaml` or set one by one):
   | Key | Value |
   |---|---|
   | `APP_ENV` | `production` |
   | `LOG_LEVEL` | `INFO` |
   | `SESSION_TTL_HOURS` | `24` |
   | `PORT` | `8000` |
   | `PAPER_CAPITAL` | `1000000` |
   | `PAPER_RISK_PCT` | `2.0` |
   | `PAPER_MAX_OPEN` | `5` |
   | `PAPER_MAX_HOLD_DAYS` | `20` |
   | `AUTO_PAPER_TRADE_ENABLED` | `true` |
   | `AUTO_PAPER_TRADE_THRESHOLD` | `70` |
   | `TELEGRAM_CHAT_ID` | `1681623488` |
9. **Volumes** (crucial — without this, your SQLite DB resets on every redeploy):
   - Add volume: name `quantedge-data`, mount path `/app/backend/data`, size `1 GB`
10. Click **Deploy**.

First build takes 3–5 minutes (Koyeb installs npm deps, builds the React
bundle, installs Python deps, pushes the image). Subsequent deploys are
~60 s thanks to layer caching.

### 5. Grab your URL

When the build reaches **Healthy** status, Koyeb shows a URL at the top of
the service page — something like:

```
https://quantedge-<your-org>.koyeb.app
```

Open it on any device. The lock screen appears → enter your master password → dashboard loads.

---

## Ongoing use

### Deploy code changes

```bash
cd /Users/swapnil.bobade_nuv/Documents/Trading
./quantedge-ai/scripts/deploy-koyeb.sh "what changed"
```

That commits any local changes, pushes to GitHub, and Koyeb auto-redeploys.
Expect the new version live within ~90 seconds.

### Tail logs

Two options:

- **Dashboard**: `app.koyeb.com` → `quantedge` → Logs tab. Live.
- **CLI** (install once: `curl -fsSL https://raw.githubusercontent.com/koyeb/koyeb-cli/master/install.sh | sh`):
  ```bash
  koyeb service logs quantedge/quantedge --follow
  ```

### Restart / manual redeploy

```bash
koyeb service redeploy quantedge/quantedge
```

Or from dashboard: service page → **Redeploy** button.

### Change a secret in the vault

```bash
# On your Mac, update the encrypted vault file:
python3 scripts/setup_secrets.py --add-secret ANY_KEY=newvalue

# Commit + push so the updated secrets.enc.json ships:
./quantedge-ai/scripts/deploy-koyeb.sh "rotate keys"
```

### Change a non-secret env var

Koyeb dashboard → `quantedge` → **Environment variables** → edit → **Update
Service**. Takes ~30 s to apply.

---

## Free-tier limits to know about

| Resource | Free Eco tier | What it means for you |
|---|---|---|
| RAM | 512 MB | Plenty; we've verified locally at ~250 MB peak |
| CPU | 0.1 vCPU | Deep Analyze will be **~2-3x slower** than local (~20 s per symbol vs 7 s). Auto-scan over the NIFTY universe takes longer. Fine for interactive use. |
| Volume | 3 GB total / 1 GB per volume (free) | Enough for thousands of paper trades + full history |
| Egress | 100 GB / month | You'll never come close |
| Regions | Frankfurt, Washington, Singapore on free tier | No Mumbai — expect ~100 ms extra latency to NSE data |
| Auto-scale to zero? | No on Eco tier | Good — the paper-trade monitor keeps running |

---

## Troubleshooting

- **Build fails at `npm run build`** → most common cause is a typo pushed in
  `App.jsx`. Run `cd quantedge-ai/frontend && npm run build` locally first.
- **Build succeeds but `/health` returns 502** → usually the image is still
  booting. Koyeb retries the health check for 60 s before marking the service
  failed.
- **`/paper-portfolio` returns 401 forever after unlock** → the JWT isn't
  getting sent. Hard refresh the browser (Cmd+Shift+R); if that doesn't help,
  open DevTools → Application → Local Storage and confirm `quantedge_session_token`
  is there.
- **History / equity curve empty after redeploy** → volume wasn't mounted.
  Dashboard → **Volumes** → make sure `quantedge-data` is attached.
- **Telegram alerts stopped after deploy** → the bot token is in the vault, so
  it should survive as long as `secrets.enc.json` was committed. Check with
  `curl https://quantedge-<org>.koyeb.app/telegram-status`.
- **Deep Analyze super slow** → it's the 0.1 vCPU. If you need faster,
  Koyeb's next tier is $0.0000062/s ≈ ₹4/hour of active compute; for an
  interactive session of 1 h/day that's ₹120/month. Still cheaper than Fly.

---

## Security hygiene after first deploy

1. **Revoke the Telegram bot token you pasted earlier** — BotFather →
   `/mybots` → pick your bot → **API Token** → **Revoke current token** → get
   a new one → `python3 scripts/setup_secrets.py --add-secret TELEGRAM_BOT_TOKEN=<new>`
   → `./quantedge-ai/scripts/deploy-koyeb.sh "rotate telegram"`.
2. **Strip plaintext secrets from `.env`** if you haven't already:
   `python3 scripts/setup_secrets.py --strip-env`. Vault is already the real
   source of truth.
3. **Verify the GitHub repo is private**. Not strictly required (the vault is
   encrypted) but one more layer.

---

## Appendix — Fly.io (paid alternative)

If you later want to move to Fly.io (~₹420/mo, Mumbai region, faster CPU):

The repo already has `fly.toml` and `scripts/deploy.sh` set up. Walkthrough:

```bash
curl -L https://fly.io/install.sh | sh
fly auth signup                # adds a payment method
cd quantedge-ai
fly launch --copy-config --no-deploy
./scripts/deploy.sh
```

See commit history / the earlier commit of this file for the full Fly-specific
walkthrough — everything about volumes, health checks, env vars works the same
way, just through Fly's UI instead of Koyeb's.
