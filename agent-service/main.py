from fastapi import FastAPI
from pydantic import BaseModel
from task_definer import TaskDefiner
from agent_runner import AgentRunner

app = FastAPI(title="Agent Factory - Agent Service", version="0.1.0")
definer = TaskDefiner()
runner = AgentRunner()


@app.get("/health")
async def health():
    return {"status": "ok", "service": "agent-service"}


class DefineRequest(BaseModel):
    message: str
    conversation: list[dict] = []
    model: str = "gpt-4o"


class DefineResponse(BaseModel):
    reply: str
    structured: dict | None = None
    is_complete: bool = False


@app.post("/define", response_model=DefineResponse)
async def define(request: DefineRequest):
    return await definer.process(request.message, request.conversation, request.model)


class ExecuteRequest(BaseModel):
    background: str
    goal: str
    acceptance_criteria: str
    agent_type: str = "general-purpose"
    model: str = "gpt-4o"
    api_key: str | None = None


class ExecuteResponse(BaseModel):
    result: str
    steps: list[dict] = []
    total_tokens: int = 0
    status: str = "completed"


@app.post("/execute", response_model=ExecuteResponse)
async def execute(request: ExecuteRequest):
    return await runner.run(request)
