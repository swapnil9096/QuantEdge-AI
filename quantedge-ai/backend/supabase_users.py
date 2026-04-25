"""Thin Supabase REST API client for the `users` table.

Uses httpx (already a project dependency) to talk to PostgREST.
All functions are async.  When SUPABASE_URL / SUPABASE_KEY are not set,
every function gracefully returns None / [] so callers don't need guards.
"""

from __future__ import annotations

import logging
import os
from typing import Any, Optional

import httpx

logger = logging.getLogger("quantedge")

SUPABASE_URL: str = os.getenv("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY: str = os.getenv("SUPABASE_KEY", "")

_BASE = f"{SUPABASE_URL}/rest/v1" if SUPABASE_URL else ""

_HEADERS: dict[str, str] = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation",
}


def supabase_enabled() -> bool:
    return bool(SUPABASE_URL and SUPABASE_KEY)


async def supabase_get_user(username: str) -> Optional[dict[str, Any]]:
    if not supabase_enabled():
        return None
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            r = await c.get(f"{_BASE}/users", headers=_HEADERS, params={"username": f"eq.{username}", "limit": "1"})
            if r.status_code == 200:
                rows = r.json()
                return rows[0] if rows else None
    except Exception as exc:
        logger.warning("Supabase get_user(%s) failed: %s", username, exc)
    return None


async def supabase_get_user_by_id(user_id: int) -> Optional[dict[str, Any]]:
    if not supabase_enabled():
        return None
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            r = await c.get(f"{_BASE}/users", headers=_HEADERS, params={"id": f"eq.{user_id}", "limit": "1"})
            if r.status_code == 200:
                rows = r.json()
                return rows[0] if rows else None
    except Exception as exc:
        logger.warning("Supabase get_user_by_id(%s) failed: %s", user_id, exc)
    return None


async def supabase_get_all_users() -> list[dict[str, Any]]:
    if not supabase_enabled():
        return []
    try:
        async with httpx.AsyncClient(timeout=15) as c:
            r = await c.get(f"{_BASE}/users", headers=_HEADERS, params={"order": "id.asc"})
            if r.status_code == 200:
                return r.json()
    except Exception as exc:
        logger.warning("Supabase get_all_users failed: %s", exc)
    return []


async def supabase_insert_user(
    username: str,
    email: Optional[str],
    password_hash: str,
    created_at: str,
    is_admin: bool = False,
) -> Optional[dict[str, Any]]:
    if not supabase_enabled():
        return None
    body = {
        "username": username,
        "password_hash": password_hash,
        "created_at": created_at,
        "is_admin": 1 if is_admin else 0,
        "is_active": 1,
    }
    if email:
        body["email"] = email
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            r = await c.post(f"{_BASE}/users", headers=_HEADERS, json=body)
            if r.status_code in (200, 201):
                rows = r.json()
                return rows[0] if rows else None
            logger.warning("Supabase insert_user(%s) status=%d body=%s", username, r.status_code, r.text[:200])
    except Exception as exc:
        logger.warning("Supabase insert_user(%s) failed: %s", username, exc)
    return None


async def supabase_update_user(user_id: int, fields: dict[str, Any]) -> bool:
    if not supabase_enabled() or not fields:
        return False
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            r = await c.patch(
                f"{_BASE}/users",
                headers=_HEADERS,
                params={"id": f"eq.{user_id}"},
                json=fields,
            )
            return r.status_code in (200, 204)
    except Exception as exc:
        logger.warning("Supabase update_user(%s) failed: %s", user_id, exc)
    return False


async def supabase_ensure_table() -> bool:
    """Check if the users table exists (simple SELECT). Returns True if accessible."""
    if not supabase_enabled():
        return False
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            r = await c.get(f"{_BASE}/users", headers=_HEADERS, params={"limit": "0"})
            if r.status_code == 200:
                return True
            logger.warning("Supabase users table not accessible: %d %s", r.status_code, r.text[:200])
    except Exception as exc:
        logger.warning("Supabase connectivity check failed: %s", exc)
    return False
