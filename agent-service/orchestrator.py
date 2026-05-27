import asyncio
import json
import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor

from coding_executor import CodingExecutor

logger = logging.getLogger(__name__)

_executor = ThreadPoolExecutor(max_workers=4)


def _run_hermes_sync(model: str, api_key: str, system_message: str, user_message: str, max_iterations: int = 30) -> dict:
    """同步调用 Hermes AIAgent（Hermes 核心是同步的）"""
    from hermes_agent import AIAgent

    agent_kwargs = {"model": model, "max_iterations": max_iterations}
    if api_key:
        agent_kwargs["api_key"] = api_key

    agent = AIAgent(**agent_kwargs)
    result = agent.run_conversation(
        user_message=user_message,
        system_message=system_message,
    )
    return result


class TaskOrchestrator:
    """
    决策层编排器：使用 Hermes AIAgent 分析用户任务，判断执行策略，委托或自行执行。

    Hermes 提供：
    - 多模型支持（Anthropic/OpenAI/DeepSeek 等）
    - 工具调用能力
    - 跨会话记忆与 skill 沉淀
    """

    def __init__(self):
        self.coding_executor = CodingExecutor()

    async def process(self, request) -> dict:
        """
        主入口：接收 ExecuteRequest，返回 ExecuteResponse 格式 dict。

        决策流程:
        1. 如果 extra_context 中有 repo_url → 直接当作 coding 任务
        2. 否则用 Hermes Agent 分析意图，判断是否需要代码修改
        3. coding 任务 → CodingExecutor
        4. 其他任务 → Hermes Agent 直接执行
        """
        extra = request.extra_context or {}
        steps = []

        # 快速路径：已明确指定仓库
        if extra.get("repo_url"):
            steps.append({
                "step": 1, "phase": "analyze",
                "output": f"Coding task detected. Repo: {extra['repo_url']}",
                "tokens_in": 0, "tokens_out": 0, "duration_ms": 0,
            })
            return await self._execute_coding_task(request, extra, steps)

        # 决策路径：Hermes 分析意图
        analysis = await self._analyze_intent(request)
        steps.append({
            "step": 1, "phase": "analyze",
            "output": f"Intent analysis: {analysis.get('intent', 'unknown')}. Reasoning: {analysis.get('reasoning', '')}",
            "tokens_in": analysis.get("tokens_in", 0),
            "tokens_out": analysis.get("tokens_out", 0),
            "duration_ms": analysis.get("duration_ms", 0),
        })

        if analysis.get("intent") == "coding" and analysis.get("repos"):
            extra_from_analysis = {
                "repo_url": analysis["repos"][0]["url"],
                "branch": analysis["repos"][0].get("branch", "main"),
            }
            if len(analysis["repos"]) > 1:
                return await self._execute_multi_repo(request, analysis["repos"], steps)
            return await self._execute_coding_task(request, extra_from_analysis, steps)

        # 非 coding 任务：Hermes Agent 直接执行
        return await self._execute_with_hermes(request, steps)

    def _resolve_model(self, request) -> str:
        """将 platform 的 model 名转为 Hermes 的 provider/model 格式"""
        model = request.model or "gpt-4o"
        if model.startswith(("openai/", "anthropic/", "deepseek/", "openrouter/")):
            return model
        if "claude" in model:
            return f"anthropic/{model}"
        if "deepseek" in model:
            return f"deepseek/{model}"
        return f"openai/{model}"

    async def _analyze_intent(self, request) -> dict:
        """用 Hermes Agent 分析用户意图，提取仓库信息"""
        start = time.time()

        system_prompt = """You are a task analyzer. Given a user's request, determine:
1. Whether it requires code modifications (intent: "coding") or can be answered directly (intent: "simple")
2. If coding: extract repository URLs and branches mentioned

You MUST respond with ONLY a JSON object, no other text:
{"intent": "coding"|"simple", "repos": [{"url": "...", "branch": "..."}], "reasoning": "..."}

If no specific repo URL is mentioned but the task clearly needs code changes, set intent to "coding" with repos as empty list.
"""
        user_msg = f"Background: {request.background}\nGoal: {request.goal}"
        model = self._resolve_model(request)

        try:
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(
                _executor,
                _run_hermes_sync,
                model,
                request.api_key,
                system_prompt,
                user_msg,
                5,
            )

            response_text = result.get("final_response", "")
            parsed = self._parse_json_response(response_text)
            parsed["duration_ms"] = int((time.time() - start) * 1000)
            return parsed
        except Exception as e:
            logger.warning("Hermes intent analysis failed: %s", e)
            return {"intent": "simple", "repos": [], "duration_ms": int((time.time() - start) * 1000)}

    def _parse_json_response(self, text: str) -> dict:
        """从 Hermes 响应中提取 JSON"""
        text = text.strip()
        # 尝试直接解析
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass
        # 尝试提取 ```json ... ``` 中的内容
        if "```json" in text:
            start = text.index("```json") + 7
            end = text.index("```", start)
            try:
                return json.loads(text[start:end].strip())
            except (json.JSONDecodeError, ValueError):
                pass
        # 尝试找第一个 { 到最后一个 }
        first_brace = text.find("{")
        last_brace = text.rfind("}")
        if first_brace != -1 and last_brace != -1:
            try:
                return json.loads(text[first_brace:last_brace + 1])
            except json.JSONDecodeError:
                pass
        return {"intent": "simple", "repos": []}

    async def _execute_coding_task(self, request, extra: dict, steps: list) -> dict:
        """单仓库 coding 任务"""
        subtask = {
            "repo_url": extra["repo_url"],
            "branch": extra.get("branch", "main"),
            "goal": request.goal,
            "background": request.background,
            "acceptance_criteria": request.acceptance_criteria,
            "task_id": extra.get("task_id", "0"),
            "api_key": request.api_key,
        }

        steps.append({
            "step": len(steps) + 1, "phase": "act",
            "output": f"Delegating to CodingExecutor: {extra['repo_url']}",
            "tokens_in": 0, "tokens_out": 0, "duration_ms": 0,
        })

        result = await self.coding_executor.execute(subtask)

        steps.append({
            "step": len(steps) + 1, "phase": "check",
            "output": f"CodingExecutor result: status={result['status']}, mr_url={result.get('mr_url')}",
            "tokens_in": 0, "tokens_out": 0, "duration_ms": 0,
        })

        final_result = result.get("result", "")
        if result.get("mr_url"):
            final_result += f"\n\nMR/PR created: {result['mr_url']}"

        return {
            "result": final_result,
            "steps": steps,
            "total_tokens": sum(s.get("tokens_in", 0) + s.get("tokens_out", 0) for s in steps),
            "status": "completed" if result["status"] in ("completed", "no_changes") else "failed",
            "mr_url": result.get("mr_url"),
        }

    async def _execute_multi_repo(self, request, repos: list, steps: list) -> dict:
        """多仓库并行执行"""
        tasks = []
        for i, repo in enumerate(repos):
            subtask = {
                "repo_url": repo["url"],
                "branch": repo.get("branch", "main"),
                "goal": request.goal,
                "background": request.background,
                "acceptance_criteria": request.acceptance_criteria,
                "task_id": f"{(request.extra_context or {}).get('task_id', '0')}-{i}",
                "api_key": request.api_key,
            }
            tasks.append(self.coding_executor.execute(subtask))

        results = await asyncio.gather(*tasks, return_exceptions=True)

        mr_urls = []
        all_results = []
        subtask_records = []
        for i, r in enumerate(results):
            if isinstance(r, Exception):
                all_results.append(f"Repo {repos[i]['url']}: FAILED - {str(r)}")
                subtask_records.append({"repo_url": repos[i]["url"], "status": "failed", "error": str(r)})
            else:
                all_results.append(f"Repo {repos[i]['url']}: {r['status']}")
                if r.get("mr_url"):
                    mr_urls.append(r["mr_url"])
                subtask_records.append({
                    "repo_url": repos[i]["url"],
                    "status": r["status"],
                    "mr_url": r.get("mr_url"),
                    "result": r.get("result", "")[:500],
                })

        steps.append({
            "step": len(steps) + 1, "phase": "act",
            "output": f"Multi-repo execution: {len(repos)} repos, {len(mr_urls)} MRs created",
            "tokens_in": 0, "tokens_out": 0, "duration_ms": 0,
        })

        final_result = "\n".join(all_results)
        if mr_urls:
            final_result += "\n\nMR/PR links:\n" + "\n".join(mr_urls)

        any_success = any(not isinstance(r, Exception) and r["status"] == "completed" for r in results)
        return {
            "result": final_result,
            "steps": steps,
            "total_tokens": sum(s.get("tokens_in", 0) + s.get("tokens_out", 0) for s in steps),
            "status": "completed" if any_success else "failed",
            "mr_url": mr_urls[0] if mr_urls else None,
            "subtasks": subtask_records,
        }

    async def _execute_with_hermes(self, request, steps: list) -> dict:
        """非 coding 任务：使用 Hermes Agent 直接执行"""
        start = time.time()

        system_prompt = (
            f"You are a helpful assistant.\n"
            f"Background: {request.background}\n"
            f"Acceptance criteria: {request.acceptance_criteria}\n\n"
            "Complete the user's goal thoroughly."
        )
        model = self._resolve_model(request)

        try:
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(
                _executor,
                _run_hermes_sync,
                model,
                request.api_key,
                system_prompt,
                request.goal,
                30,
            )

            content = result.get("final_response", "")
            duration = int((time.time() - start) * 1000)

            steps.append({
                "step": len(steps) + 1, "phase": "act",
                "output": content,
                "tokens_in": 0, "tokens_out": 0,
                "duration_ms": duration,
            })

            return {
                "result": content,
                "steps": steps,
                "total_tokens": sum(s.get("tokens_in", 0) + s.get("tokens_out", 0) for s in steps),
                "status": "completed",
            }
        except Exception as e:
            logger.exception("Hermes execution failed")
            return {
                "result": f"Execution failed: {str(e)}",
                "steps": steps,
                "total_tokens": 0,
                "status": "failed",
            }
