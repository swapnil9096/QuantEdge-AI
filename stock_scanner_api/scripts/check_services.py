#!/usr/bin/env python3
"""Check PostgreSQL and Redis connectivity using app config. Exit 0 if both OK, 1 otherwise."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


def main() -> int:
    from app.core.config import get_settings

    settings = get_settings()
    errors = []

    # Database: PostgreSQL or SQLite
    url = settings.database_url.strip()
    if "sqlite" in url.lower():
        try:
            from urllib.parse import unquote
            if "///" in url:
                path = url.split("///", 1)[-1].split("?")[0]
            else:
                path = url.split(":", 1)[-1].lstrip("/").split("?")[0]
            path = unquote(path)
            if path.startswith("./"):
                path = str(Path(__file__).resolve().parent.parent / path[2:])
            Path(path).parent.mkdir(parents=True, exist_ok=True)
            import sqlite3
            sqlite3.connect(path).close()
            print("Database (SQLite): OK", flush=True)
        except Exception as e:
            errors.append(f"Database (SQLite): FAIL - {e}")
            print(f"Database (SQLite): FAIL - {e}")
    else:
        # PostgreSQL (asyncpg-style URL)
        try:
            import asyncio
            import asyncpg
        except ImportError:
            errors.append("PostgreSQL: SKIP (asyncpg not installed)")
        else:
            if not url.startswith("postgresql+asyncpg://") and not url.startswith("postgresql://"):
                errors.append("PostgreSQL: FAIL - unsupported URL scheme")
                print("PostgreSQL: FAIL - unsupported URL scheme")
            else:
                try:
                    if url.startswith("postgresql+asyncpg://"):
                        url = url.replace("postgresql+asyncpg://", "postgresql://", 1)
                    part = url.replace("postgresql://", "")
                    if "@" in part:
                        auth, rest = part.split("@", 1)
                        user, password = auth.split(":", 1) if ":" in auth else (auth, None)
                    else:
                        user, password, rest = "postgres", None, part
                    if "/" in rest:
                        host_port, db = rest.rsplit("/", 1)
                        database = db.split("?")[0]
                    else:
                        host_port, database = rest, "postgres"
                    host, port = host_port.split(":") if ":" in host_port else (host_port, "5432")
                    port = int(port)

                    async def try_pg():
                        conn = await asyncpg.connect(
                            host=host,
                            port=port,
                            user=user,
                            password=password or "postgres",
                            database=database,
                            timeout=3,
                        )
                        await conn.close()

                    asyncio.run(try_pg())
                    print("PostgreSQL: OK", flush=True)
                except Exception as e:
                    errors.append(f"PostgreSQL: FAIL - {e}")
                    print(f"PostgreSQL: FAIL - {e}")

    # Redis
    try:
        import redis
    except ImportError:
        errors.append("Redis: SKIP (redis not installed)")
    else:
        try:
            r = redis.from_url(settings.redis_url, socket_connect_timeout=3)
            r.ping()
            print("Redis: OK")
        except Exception as e:
            errors.append(f"Redis: FAIL - {e}")
            print(f"Redis: FAIL - {e}")

    if errors:
        print("\nStart services with: docker compose up -d postgres redis")
        print("Or install locally (e.g. brew install postgresql redis && brew services start postgresql redis)")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
