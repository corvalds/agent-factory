import litellm


class AgentRunner:
    MAX_ITERATIONS = 20

    async def run(self, request) -> dict:
        steps = []
        messages = [
            {
                "role": "system",
                "content": (
                    f"You are a {request.agent_type} agent. "
                    f"Background: {request.background}\n"
                    f"Goal: {request.goal}\n"
                    f"Acceptance criteria: {request.acceptance_criteria}\n\n"
                    "Execute the task step by step. For each step, describe what you observe, "
                    "think, and do. When the task is complete, output TASK_COMPLETE followed by "
                    "the final result."
                ),
            }
        ]

        total_tokens = 0
        consecutive_errors = 0
        last_error_type = None

        for i in range(self.MAX_ITERATIONS):
            try:
                response = await litellm.acompletion(
                    model=request.model,
                    messages=messages,
                    api_key=request.api_key,
                )
                content = response.choices[0].message.content
                usage = response.usage
                total_tokens += (usage.prompt_tokens or 0) + (usage.completion_tokens or 0)

                steps.append({
                    "step": i + 1,
                    "phase": "act",
                    "output": content,
                    "tokens_in": usage.prompt_tokens or 0,
                    "tokens_out": usage.completion_tokens or 0,
                })

                consecutive_errors = 0
                last_error_type = None

                if "TASK_COMPLETE" in content:
                    result = content.split("TASK_COMPLETE", 1)[-1].strip()
                    return {
                        "result": result,
                        "steps": steps,
                        "total_tokens": total_tokens,
                        "status": "completed",
                    }

                messages.append({"role": "assistant", "content": content})
                messages.append({"role": "user", "content": "Continue to the next step."})

            except Exception as e:
                error_type = type(e).__name__
                if error_type == last_error_type:
                    consecutive_errors += 1
                else:
                    consecutive_errors = 1
                    last_error_type = error_type

                steps.append({
                    "step": i + 1,
                    "phase": "error",
                    "error_type": error_type,
                    "message": str(e),
                })

                if consecutive_errors >= 3:
                    return {
                        "result": f"Circuit breaker: 3 consecutive {error_type} errors",
                        "steps": steps,
                        "total_tokens": total_tokens,
                        "status": "failed",
                    }

        return {
            "result": "Max iterations reached",
            "steps": steps,
            "total_tokens": total_tokens,
            "status": "failed",
        }
