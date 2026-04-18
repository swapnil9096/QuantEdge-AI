# Java Legacy Archive

This directory contains the retired Spring Boot 3.2 trading application that used
to live at the repository root. It has been superseded by
`stock_scanner_api/` (Python + FastAPI) and is no longer built or deployed.

## What's here

- `src/` — original Java source tree (`com.trading.*`)
- `pom.xml` — Maven build file
- `target/` — last-known compiled output
- `backup/` — older Java services kept before the rewrite
- `app.log` — last runtime log
- The legacy docs (`API_LIST.md`, `MCP_*`, `NSE_*`, `HOW_TO_*`, `PROJECT_SUMMARY.md`,
  etc.) and the legacy `start` / `start.sh` helpers.

## Why it's archived, not deleted

- Historical reference for the feature set (multibagger / swing strategy, MCP
  integration, WebSocket streaming).
- Some of the strategy text (scoring thresholds, indicator weights) fed into the
  Python rewrite and may be useful again.

## Do not build from here

The Maven project is intentionally excluded from CI. If you genuinely need to run
it, copy the relevant files back to a separate branch so the main line stays
focused on the Python service.
