import os
import logging
import httpx

logger = logging.getLogger(__name__)

PLATFORM_URL = os.getenv("PLATFORM_URL", "http://localhost:8080")


async def get_all_projects() -> list[dict]:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.get(f"{PLATFORM_URL}/api/projects")
            r.raise_for_status()
            return r.json()
    except Exception as e:
        logger.warning("Failed to fetch project registry: %s", e)
        return []


async def search_projects(query: str) -> list[dict]:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.get(f"{PLATFORM_URL}/api/projects/search", params={"q": query})
            r.raise_for_status()
            return r.json()
    except Exception as e:
        logger.warning("Failed to search projects: %s", e)
        return []
