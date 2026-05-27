import asyncio
import json
import logging
import time

from hermes_bridge import run_hermes
from coding_executor import CodingExecutor
from project_registry import search_projects

logger = logging.getLogger(__name__)


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

        if analysis.get("unknown_project"):
            hint = analysis.get("project_hint", "")
            return {
                "result": None,
                "status": "needs_clarification",
                "clarification": f"无法识别项目「{hint}」，请提供仓库地址或进一步说明是哪个项目。",
                "steps": steps,
                "total_tokens": sum(s.get("tokens_in", 0) + s.get("tokens_out", 0) for s in steps),
            }

        if analysis.get("intent") == "coding" and analysis.get("repos"):
            extra_from_analysis = {
                "repo_url": analysis["repos"][0]["url"],
                "branch": analysis["repos"][0].get("branch", "main"),
            }
            if len(analysis["repos"]) > 1:
                return await self._execute_multi_repo(request, analysis["repos"], steps)
            return await self._execute_coding_task(request, extra_from_analysis, steps)

        if analysis.get("intent") == "coding" and not analysis.get("repos"):
            return {
                "result": None,
                "status": "needs_clarification",
                "clarification": "检测到这是一个需要修改代码的任务，但无法确定目标仓库。请提供项目名称或仓库地址。",
                "steps": steps,
                "total_tokens": sum(s.get("tokens_in", 0) + s.get("tokens_out", 0) for s in steps),
            }

        # 非 coding 任务：Hermes Agent 直接执行
        return await self._execute_with_hermes(request, steps)

    async def _analyze_intent(self, request) -> dict:
        """用 Hermes Agent 分析用户意图，按需搜索项目注册表匹配仓库"""
        start = time.time()

        matched_projects = await self._search_relevant_projects(request.background, request.goal)
        if matched_projects:
            project_list = "\n".join(
                f"- {p['name']}: {p['repoUrl']} (branch: {p.get('defaultBranch', 'master')})"
                for p in matched_projects
            )
            registry_section = f"\n\nMatched known projects:\n{project_list}\n"
        else:
            registry_section = ""

        system_prompt = f"""You are a task analyzer. Given a user's request, determine:
1. Whether it requires code modifications (intent: "coding") or can be answered directly (intent: "simple")
2. If coding: match mentioned project names, service names, or contextual clues to known projects below. Use keyword matching (service names from traces, package names from stack traces, project aliases).
3. If the user mentions a project/service that cannot be matched to any known project, set "unknown_project": true and "project_hint" to what they mentioned.
{registry_section}
You MUST respond with ONLY a JSON object, no other text:
{{"intent": "coding"|"simple", "repos": [{{"url": "...", "branch": "..."}}], "reasoning": "...", "unknown_project": false, "project_hint": ""}}

If no specific repo URL is mentioned and no known project matches, but the task clearly needs code changes, set intent to "coding" with repos as empty list and unknown_project to true.
"""
        user_msg = f"Background: {request.background}\nGoal: {request.goal}"

        try:
            result = await run_hermes(
                model=request.model,
                api_key=request.api_key,
                system_message=system_prompt,
                user_message=user_msg,
                max_iterations=5,
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
        """非 coding 任务：使用 Hermes Agent 直接执行，注入项目注册表供上下文推断"""
        start = time.time()

        projects = await self._search_relevant_projects(request.background, request.goal)
        if projects:
            project_list = "\n".join(
                f"- {p['name']}: {p['repoUrl']} (keywords: {p.get('keywords', '')})"
                for p in projects
            )
            registry_section = (
                f"\n\nYou have access to the following known projects/services:\n{project_list}\n"
                "If during your investigation you determine that code changes are needed in one of these projects, "
                "include the following marker at the END of your response on its own line:\n"
                "CODING_NEEDED: <project_name>\n"
                "This will trigger the coding pipeline for that project.\n"
            )
        else:
            registry_section = ""

        system_prompt = (
            f"You are a helpful assistant.\n"
            f"Background: {request.background}\n"
            f"Acceptance criteria: {request.acceptance_criteria}\n"
            f"{registry_section}\n"
            "Complete the user's goal thoroughly."
        )

        try:
            result = await run_hermes(
                model=request.model,
                api_key=request.api_key,
                system_message=system_prompt,
                user_message=request.goal,
                max_iterations=30,
            )

            content = result.get("final_response", "")
            duration = int((time.time() - start) * 1000)

            steps.append({
                "step": len(steps) + 1, "phase": "act",
                "output": content,
                "tokens_in": 0, "tokens_out": 0,
                "duration_ms": duration,
            })

            coding_project = self._extract_coding_needed(content, projects)
            if coding_project:
                logger.info("Hermes identified coding needed for project: %s", coding_project["name"])
                extra = {
                    "repo_url": coding_project["repoUrl"],
                    "branch": coding_project.get("defaultBranch", "master"),
                }
                return await self._execute_coding_task(request, extra, steps)

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

    @staticmethod
    def _extract_coding_needed(content: str, projects: list[dict]) -> dict | None:
        """从 Hermes 响应中检测 CODING_NEEDED 标记，匹配到已知项目则返回"""
        import re
        match = re.search(r"CODING_NEEDED:\s*(.+)", content)
        if not match:
            return None
        hint = match.group(1).strip().lower()
        for p in projects:
            if p["name"].lower() == hint:
                return p
            keywords = (p.get("keywords") or "").lower().split(",")
            if hint in [k.strip() for k in keywords]:
                return p
        return None

    @staticmethod
    async def _search_relevant_projects(*texts: str) -> list[dict]:
        """从文本中提取关键词，按需搜索项目注册表，返回匹配的项目（去重，最多10个）"""
        import re
        combined = " ".join(t for t in texts if t)
        pattern = r'[a-zA-Z][a-zA-Z0-9_-]*(?:-(?:center|service|sdk|api|job|base|app|portal|agent))?'
        candidates = re.findall(pattern, combined)
        stop_words = {'the', 'and', 'for', 'are', 'this', 'that', 'with', 'from', 'have', 'has',
                      'not', 'but', 'can', 'will', 'code', 'bug', 'fix', 'scan', 'check', 'null'}
        keywords = []
        for c in candidates:
            if len(c) >= 3 and c.lower() not in stop_words and c not in keywords:
                keywords.append(c)
            if len(keywords) >= 5:
                break

        matched = []
        seen_names = set()
        for kw in keywords:
            results = await search_projects(kw)
            for p in results:
                if p["name"] not in seen_names:
                    seen_names.add(p["name"])
                    matched.append(p)
        return matched[:10]
