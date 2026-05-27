import json
import logging
import re

from hermes_bridge import run_hermes
from project_registry import search_projects

logger = logging.getLogger(__name__)


class TaskDefiner:
    SYSTEM_PROMPT_BASE = (
        "You are a task definition assistant for a cloud-based AI Agent platform (agent-factory). "
        "Your ONLY job is to help users clarify their intent through conversational questions. "
        "You MUST NOT execute any tasks, run code, read files, or perform any actions. "
        "You are purely a conversation facilitator.\n\n"
        "Platform context:\n"
        "- This is a CLOUD platform — agents clone repos from remote Git URLs. "
        "NEVER ask for local file paths or directories.\n"
        "- Known projects have a registered repo_url and default_branch in the system. "
        "If the user mentions a known project, DO NOT ask for its repo URL or branch.\n"
        "- Only ask for repo URL/branch if the project is NOT in the known projects list.\n\n"
        "Process:\n"
        "1. Understand what the user wants to accomplish\n"
        "2. Check if mentioned projects/services are in the known projects list below\n"
        "3. Ask 2-4 concise clarifying questions per turn ONLY about things that are genuinely unclear "
        "(e.g., scope of changes, specific requirements). Skip questions whose answers can be inferred "
        "from the known projects list or from the user's message.\n"
        "4. When you have enough information (usually 1-2 turns), output ONLY a JSON object with keys: "
        "background, goal, acceptance_criteria\n\n"
        "Rules:\n"
        "- NEVER ask for local paths, directories, or workspace locations\n"
        "- NEVER ask which branch to target for MR if the project is known (use its default_branch)\n"
        "- NEVER ask for the repo URL if the project is known\n"
        "- NEVER attempt to fulfill the user's request directly\n"
        "- NEVER use tools or execute code\n"
        "- NEVER produce the final deliverable (code, analysis, report, etc.)\n"
        "- If the user's request is already clear and specific enough, output the JSON immediately "
        "without asking questions\n"
        "- Your output is ONLY questions OR the structured JSON definition\n"
        "- Max 10 conversation turns\n"
        "- Respond in the same language as the user"
    )

    async def process(self, message: str, conversation: list[dict], model: str, api_key: str = None, base_url: str = None) -> dict:
        system_prompt = await self._build_system_prompt(message, conversation)
        user_message = self._build_context_message(conversation, message)

        try:
            result = await run_hermes(
                model=model,
                api_key=api_key,
                system_message=system_prompt,
                user_message=user_message,
                max_iterations=5,
                base_url=base_url,
                disable_tools=True,
            )

            reply = result.get("final_response", "")
            structured = self._extract_structured(reply)
            return {
                "reply": reply,
                "structured": structured,
                "is_complete": structured is not None,
            }
        except Exception as e:
            logger.exception("Hermes task definer failed")
            return {
                "reply": f"Error calling LLM: {e}",
                "structured": None,
                "is_complete": False,
            }

    async def _build_system_prompt(self, message: str, conversation: list[dict]) -> str:
        keywords = self._extract_keywords(message, conversation)
        matched_projects = []
        for kw in keywords:
            results = await search_projects(kw)
            for p in results:
                if p not in matched_projects:
                    matched_projects.append(p)

        if matched_projects:
            project_list = "\n".join(
                f"- {p['name']}: repo={p['repoUrl']}, default_branch={p.get('defaultBranch', 'master')}"
                for p in matched_projects[:10]
            )
            registry_section = f"\n\nMatched known projects (DO NOT ask about repo URL or branch for these):\n{project_list}"
        else:
            registry_section = "\n\nNo known projects matched the user's message. If the task requires code changes, ask the user for the repo URL and branch."
        return self.SYSTEM_PROMPT_BASE + registry_section

    @staticmethod
    def _extract_keywords(message: str, conversation: list[dict]) -> list[str]:
        """从用户消息中提取可能的项目名/服务名关键词"""
        text = message
        for msg in (conversation or []):
            if msg.get("role") == "user":
                text += " " + msg.get("content", "")

        keywords = []
        # 匹配常见项目命名模式: xxx-center, xxx-service, xxx-sdk, xxx-api 等
        pattern = r'[a-zA-Z][a-zA-Z0-9_-]*(?:-(?:center|service|sdk|api|job|base|app|portal|agent))?'
        candidates = re.findall(pattern, text)
        for c in candidates:
            if len(c) >= 3 and c.lower() not in ('the', 'and', 'for', 'are', 'this', 'that', 'with', 'from', 'have', 'has', 'not', 'but', 'can', 'will'):
                if c not in keywords:
                    keywords.append(c)
        return keywords[:5]

    def _build_context_message(self, conversation: list[dict], current_message: str) -> str:
        """将历史对话和当前消息合并为单个 user message 供 Hermes 处理"""
        if not conversation:
            return current_message

        parts = []
        for msg in conversation:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            if role == "user":
                parts.append(f"User: {content}")
            elif role == "assistant":
                parts.append(f"Assistant: {content}")

        parts.append(f"User: {current_message}")
        return (
            "Here is the conversation so far:\n\n"
            + "\n".join(parts)
            + "\n\nContinue the conversation. If you have enough information, output the structured JSON."
        )

    def _extract_structured(self, text: str) -> dict | None:
        match = re.search(r"\{[^{}]*\"background\"[^{}]*\}", text, re.DOTALL)
        if match:
            try:
                data = json.loads(match.group())
                if all(k in data for k in ("background", "goal", "acceptance_criteria")):
                    return data
            except json.JSONDecodeError:
                pass
        # 尝试 ```json 包裹
        if "```json" in text:
            try:
                start = text.index("```json") + 7
                end = text.index("```", start)
                data = json.loads(text[start:end].strip())
                if all(k in data for k in ("background", "goal", "acceptance_criteria")):
                    return data
            except (json.JSONDecodeError, ValueError):
                pass
        return None
