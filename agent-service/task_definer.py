import litellm


class TaskDefiner:
    SYSTEM_PROMPT = (
        "You are an AI assistant helping users define tasks clearly. "
        "Ask clarifying questions about the user's request to understand: "
        "1) Background context, 2) Specific goal, 3) Acceptance criteria. "
        "When you have enough information, output a structured definition as JSON with keys: "
        "background, goal, acceptance_criteria. "
        "Keep questions concise (2-4 per turn). Max 10 conversation turns."
    )

    async def process(self, message: str, conversation: list[dict], model: str) -> dict:
        messages = [{"role": "system", "content": self.SYSTEM_PROMPT}]
        messages.extend(conversation)
        messages.append({"role": "user", "content": message})

        try:
            response = await litellm.acompletion(model=model, messages=messages)
            reply = response.choices[0].message.content

            structured = self._extract_structured(reply)
            return {
                "reply": reply,
                "structured": structured,
                "is_complete": structured is not None,
            }
        except Exception as e:
            return {
                "reply": f"Error calling LLM: {e}",
                "structured": None,
                "is_complete": False,
            }

    def _extract_structured(self, text: str) -> dict | None:
        import json
        import re

        match = re.search(r"\{[^{}]*\"background\"[^{}]*\}", text, re.DOTALL)
        if match:
            try:
                data = json.loads(match.group())
                if all(k in data for k in ("background", "goal", "acceptance_criteria")):
                    return data
            except json.JSONDecodeError:
                pass
        return None
