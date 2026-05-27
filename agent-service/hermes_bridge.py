"""
Hermes Bridge: 统一的 Hermes AIAgent 异步调用层。

task_definer.py 和 orchestrator.py 都通过此模块调用 Hermes，
确保模型解析、异步桥接、错误处理逻辑一致。
"""

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger(__name__)

_executor = ThreadPoolExecutor(max_workers=4)


def resolve_model(model: str) -> str:
    """将 platform 的 model 名转为 Hermes 的 provider/model 格式"""
    if not model:
        return "openai/gpt-4o"
    if model.startswith(("openai/", "anthropic/", "deepseek/", "openrouter/")):
        return model
    if "claude" in model:
        return f"anthropic/{model}"
    if "deepseek" in model:
        return f"deepseek/{model}"
    return f"openai/{model}"


def resolve_api_mode(model: str) -> str:
    """根据 model 名推断 Hermes 的 api_mode"""
    if "claude" in model or model.startswith("anthropic/"):
        return "anthropic_messages"
    return "chat_completions"


def _run_sync(model: str, api_key: str, base_url: str, api_mode: str, system_message: str, user_message: str, max_iterations: int) -> dict:
    """同步调用 Hermes AIAgent"""
    from run_agent import AIAgent

    agent_kwargs = {"model": model, "max_iterations": max_iterations}
    if api_key:
        agent_kwargs["api_key"] = api_key
    if base_url:
        agent_kwargs["base_url"] = base_url
    if api_mode:
        agent_kwargs["api_mode"] = api_mode

    agent = AIAgent(**agent_kwargs)
    return agent.run_conversation(
        user_message=user_message,
        system_message=system_message,
    )


async def run_hermes(model: str, api_key: str, system_message: str, user_message: str, max_iterations: int = 30, base_url: str = None, api_mode: str = None) -> dict:
    """
    统一的 Hermes 异步调用入口。

    返回 Hermes 的 result dict，包含 final_response 等字段。
    """
    hermes_model = resolve_model(model)
    effective_api_mode = api_mode or resolve_api_mode(hermes_model)
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(
        _executor,
        _run_sync,
        hermes_model,
        api_key,
        base_url or "",
        effective_api_mode,
        system_message,
        user_message,
        max_iterations,
    )
