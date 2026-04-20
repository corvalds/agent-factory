import os
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from task_definer import TaskDefiner
from agent_runner import AgentRunner

app = FastAPI(title="Agent Factory - Agent Service", version="0.2.0")
definer = TaskDefiner()
runner = AgentRunner()

INTERNAL_KEY = os.environ.get("AF_INTERNAL_KEY", "internal-dev-key")


def verify_internal(x_internal_key: str = Header(None)):
    if INTERNAL_KEY and x_internal_key != INTERNAL_KEY:
        raise HTTPException(status_code=401, detail="Invalid internal key")


@app.get("/health")
async def health():
    return {"status": "ok", "service": "agent-service", "version": "0.2.0"}


class DefineRequest(BaseModel):
    message: str
    conversation: list[dict] = []
    model: str = "gpt-4o"
    api_key: str | None = None
    base_url: str | None = None


class DefineResponse(BaseModel):
    reply: str
    structured: dict | None = None
    is_complete: bool = False


@app.post("/define", response_model=DefineResponse, dependencies=[])
async def define(request: DefineRequest, x_internal_key: str = Header(None)):
    verify_internal(x_internal_key)
    return await definer.process(request.message, request.conversation, request.model, request.api_key, request.base_url)


class ExecuteRequest(BaseModel):
    background: str
    goal: str
    acceptance_criteria: str
    agent_type: str = "general-purpose"
    model: str = "gpt-4o"
    api_key: str | None = None
    base_url: str | None = None


class ExecuteResponse(BaseModel):
    result: str
    steps: list[dict] = []
    total_tokens: int = 0
    status: str = "completed"


@app.post("/execute", response_model=ExecuteResponse)
async def execute(request: ExecuteRequest, x_internal_key: str = Header(None)):
    verify_internal(x_internal_key)
    return await runner.run(request)
