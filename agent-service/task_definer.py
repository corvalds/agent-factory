import json
import logging
import re

from hermes_bridge import run_hermes

logger = logging.getLogger(__name__)


class TaskDefiner:
    SYSTEM_PROMPT = (
        "You are an AI assistant helping users define tasks clearly. "
        "Ask clarifying questions about the user's request to understand: "
        "1) Background context, 2) Specific goal, 3) Acceptance criteria. "
        "When you have enough information, output a structured definition as JSON with keys: "
        "background, goal, acceptance_criteria. "
        "Keep questions concise (2-4 per turn). Max 10 conversation turns."
    )

    async def process(self, message: str, conversation: list[dict], model: str, api_key: str = None, base_url: str = None) -> dict:
        user_message = self._build_context_message(conversation, message)

        try:
            result = await run_hermes(
                model=model,
                api_key=api_key,
                system_message=self.SYSTEM_PROMPT,
                user_message=user_message,
                max_iterations=5,
                base_url=base_url,
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
