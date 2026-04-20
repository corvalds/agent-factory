import asyncio
import time
import litellm

TOOLS_BY_AGENT = {
    "web-scraper": [
        {
            "type": "function",
            "function": {
                "name": "http_get",
                "description": "Fetch a URL and return the response body as text",
                "parameters": {
                    "type": "object",
                    "properties": {"url": {"type": "string", "description": "The URL to fetch"}},
                    "required": ["url"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "parse_html",
                "description": "Extract text content from HTML, optionally filtering by CSS selector",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "html": {"type": "string", "description": "Raw HTML string"},
                        "selector": {"type": "string", "description": "CSS selector to filter elements"},
                    },
                    "required": ["html"],
                },
            },
        },
    ],
    "code-analyst": [
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "Read a file and return its contents",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string", "description": "File path to read"}},
                    "required": ["path"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "list_files",
                "description": "List files in a directory matching an optional glob pattern",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "directory": {"type": "string", "description": "Directory path"},
                        "pattern": {"type": "string", "description": "Glob pattern", "default": "*"},
                    },
                    "required": ["directory"],
                },
            },
        },
    ],
    "general-purpose": [],
}


async def _execute_tool(name: str, args: dict) -> str:
    if name == "http_get":
        import requests
        try:
            resp = requests.get(args["url"], timeout=30, headers={"User-Agent": "AgentFactory/1.0"})
            resp.raise_for_status()
            return resp.text[:50000]
        except Exception as e:
            return f"Error fetching URL: {e}"

    elif name == "parse_html":
        from bs4 import BeautifulSoup
        soup = BeautifulSoup(args["html"], "html.parser")
        if "selector" in args and args["selector"]:
            elements = soup.select(args["selector"])
            return "\n".join(el.get_text(strip=True) for el in elements)
        return soup.get_text(separator="\n", strip=True)[:30000]

    elif name == "read_file":
        try:
            with open(args["path"], "r") as f:
                return f.read()[:50000]
        except Exception as e:
            return f"Error reading file: {e}"

    elif name == "list_files":
        import glob as g
        pattern = args.get("pattern", "*")
        matches = g.glob(f"{args['directory']}/{pattern}", recursive=True)
        return "\n".join(matches[:200])

    return f"Unknown tool: {name}"


async def _llm_call_with_retry(model, messages, api_key=None, tools=None, max_retries=3):
    for attempt in range(max_retries):
        try:
            kwargs = {"model": model, "messages": messages}
            if api_key:
                kwargs["api_key"] = api_key
            if tools:
                kwargs["tools"] = tools
            return await litellm.acompletion(**kwargs)
        except Exception as e:
            if attempt == max_retries - 1:
                raise
            wait = (2 ** attempt)
            await asyncio.sleep(wait)


class AgentRunner:
    MAX_ITERATIONS = 20

    async def run(self, request) -> dict:
        steps = []
        tools = TOOLS_BY_AGENT.get(request.agent_type, [])

        system_prompt = (
            f"You are a {request.agent_type} agent.\n"
            f"Background: {request.background}\n"
            f"Goal: {request.goal}\n"
            f"Acceptance criteria: {request.acceptance_criteria}\n\n"
            "Execute the task using the observe-think-act pattern.\n"
            "OBSERVE: Read the task and available information.\n"
            "THINK: Plan your next action.\n"
            "ACT: Execute the action (use tools if available).\n"
            "CHECK: Verify if the acceptance criteria are met.\n\n"
            "When the task is complete, output TASK_COMPLETE followed by the final result."
        )

        messages = [{"role": "system", "content": system_prompt}]
        total_tokens = 0
        consecutive_errors = 0
        last_error_type = None

        for i in range(self.MAX_ITERATIONS):
            step_start = time.time()
            phase = "think" if i % 2 == 0 else "act"
            if i == 0:
                phase = "observe"

            try:
                response = await _llm_call_with_retry(
                    model=request.model,
                    messages=messages,
                    api_key=request.api_key,
                    tools=tools or None,
                )
                msg = response.choices[0].message
                usage = response.usage
                tokens_in = usage.prompt_tokens or 0
                tokens_out = usage.completion_tokens or 0
                total_tokens += tokens_in + tokens_out
                duration_ms = int((time.time() - step_start) * 1000)

                if msg.tool_calls:
                    phase = "act"
                    messages.append(msg)
                    tool_results = []
                    for tc in msg.tool_calls:
                        import json
                        args = json.loads(tc.function.arguments) if isinstance(tc.function.arguments, str) else tc.function.arguments
                        result = await _execute_tool(tc.function.name, args)
                        tool_results.append({"tool": tc.function.name, "args": args, "result": result[:2000]})
                        messages.append({"role": "tool", "tool_call_id": tc.id, "content": result[:5000]})

                    steps.append({
                        "step": i + 1,
                        "phase": phase,
                        "output": f"Called {len(msg.tool_calls)} tool(s): {', '.join(tc.function.name for tc in msg.tool_calls)}",
                        "tool_calls": tool_results,
                        "tokens_in": tokens_in,
                        "tokens_out": tokens_out,
                        "duration_ms": duration_ms,
                    })
                    consecutive_errors = 0
                    last_error_type = None
                    continue

                content = msg.content or ""
                steps.append({
                    "step": i + 1,
                    "phase": phase,
                    "output": content,
                    "tokens_in": tokens_in,
                    "tokens_out": tokens_out,
                    "duration_ms": duration_ms,
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
                duration_ms = int((time.time() - step_start) * 1000)
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
                    "duration_ms": duration_ms,
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
