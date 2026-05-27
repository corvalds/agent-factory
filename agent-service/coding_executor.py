import asyncio
import logging
import os
import random
import string
import subprocess
from datetime import datetime
from pathlib import Path

logger = logging.getLogger(__name__)

WORKSPACE_ROOT = Path.home() / ".agent-factory" / "workspace"
WORKTREE_ROOT = Path.home() / ".agent-factory" / "worktrees"


class CodingExecutor:
    """Coding Agent 执行器：在本地 workspace 中用 Claude Code CLI 修复代码"""

    def __init__(self):
        WORKSPACE_ROOT.mkdir(parents=True, exist_ok=True)
        WORKTREE_ROOT.mkdir(parents=True, exist_ok=True)

    async def execute(self, subtask: dict) -> dict:
        """
        执行单个 coding 子任务。

        subtask keys:
            repo_url: str       — Git 仓库地址
            branch: str         — 目标分支 (默认 main)
            goal: str           — 修复目标描述
            background: str     — 背景信息
            task_id: str        — 任务 ID
            api_key: str        — Anthropic API Key
        """
        repo_url = subtask["repo_url"]
        branch = subtask.get("branch", "main")
        task_id = str(subtask.get("task_id", "unknown"))

        try:
            repo_dir = self._ensure_repo(repo_url, branch)
            worktree_dir = self._create_worktree(repo_dir, task_id, branch)

            result = await self._run_claude_code(worktree_dir, subtask)

            has_changes = self._has_uncommitted_or_new_commits(worktree_dir, branch)
            mr_url = None
            if has_changes:
                mr_url = await self._create_merge_request(worktree_dir, subtask)

            return {
                "result": result,
                "mr_url": mr_url,
                "worktree_path": str(worktree_dir),
                "status": "completed" if has_changes else "no_changes",
            }
        except asyncio.TimeoutError:
            return {"result": "Claude Code execution timed out (600s)", "status": "failed"}
        except subprocess.CalledProcessError as e:
            return {"result": f"Git operation failed: {e.stderr or e.stdout or str(e)}", "status": "failed"}
        except Exception as e:
            logger.exception("CodingExecutor error for task %s", task_id)
            return {"result": f"Execution error: {str(e)}", "status": "failed"}

    def _extract_repo_name(self, repo_url: str) -> str:
        """从 URL 提取仓库名: git@github.com:org/repo.git → org--repo"""
        url = repo_url.rstrip("/").removesuffix(".git")
        if ":" in url and "@" in url:
            # SSH format: git@host:org/repo
            path = url.split(":")[-1]
        else:
            # HTTPS format
            path = "/".join(url.split("/")[-2:])
        return path.replace("/", "--")

    def _ensure_repo(self, repo_url: str, branch: str) -> Path:
        """仓库不存在则 clone，存在则 fetch 最新"""
        repo_name = self._extract_repo_name(repo_url)
        repo_dir = WORKSPACE_ROOT / repo_name

        if repo_dir.exists():
            logger.info("Repo exists at %s, fetching latest", repo_dir)
            subprocess.run(["git", "fetch", "--all", "--prune"], cwd=repo_dir, check=True,
                           capture_output=True, text=True)
        else:
            logger.info("Cloning %s to %s", repo_url, repo_dir)
            subprocess.run(["git", "clone", repo_url, str(repo_dir)], check=True,
                           capture_output=True, text=True)

        return repo_dir

    def _create_worktree(self, repo_dir: Path, task_id: str, base_branch: str) -> Path:
        """创建 git worktree，放在仓库外部避免被 git track"""
        suffix = "".join(random.choices(string.ascii_lowercase, k=3))
        date_str = datetime.now().strftime("%m%d")
        branch_name = f"fix/task-{task_id}-{date_str}{suffix}"
        repo_name = repo_dir.name
        worktree_name = f"{repo_name}--{branch_name.replace('/', '-')}"
        worktree_path = WORKTREE_ROOT / worktree_name

        worktree_path.parent.mkdir(parents=True, exist_ok=True)

        subprocess.run(
            ["git", "worktree", "add", "-b", branch_name, str(worktree_path), f"origin/{base_branch}"],
            cwd=repo_dir, check=True, capture_output=True, text=True,
        )
        logger.info("Created worktree at %s (branch: %s)", worktree_path, branch_name)
        return worktree_path

    async def _run_claude_code(self, worktree_dir: Path, subtask: dict) -> str:
        """在 worktree 中运行 Claude Code CLI"""
        background = subtask.get("background", "")
        goal = subtask.get("goal", "")
        acceptance = subtask.get("acceptance_criteria", "")

        prompt_parts = []
        if background:
            prompt_parts.append(f"Background: {background}")
        prompt_parts.append(f"Goal: {goal}")
        if acceptance:
            prompt_parts.append(f"Acceptance criteria: {acceptance}")
        prompt_parts.append(
            "\nFix the issue in this repository. After making changes:\n"
            "1. Ensure the code compiles/passes linting\n"
            "2. Run relevant tests if they exist\n"
            "3. Commit your changes with a clear commit message"
        )
        prompt = "\n".join(prompt_parts)

        env = os.environ.copy()
        if subtask.get("api_key"):
            env["ANTHROPIC_API_KEY"] = subtask["api_key"]

        cmd = ["claude", "-p", prompt, "--dangerously-skip-permissions"]

        logger.info("Running Claude Code in %s", worktree_dir)
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            cwd=str(worktree_dir),
            env=env,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=600)

        if proc.returncode != 0:
            error_msg = stderr.decode(errors="replace").strip()
            logger.warning("Claude Code exited with %d: %s", proc.returncode, error_msg[:500])
            return f"Claude Code failed (exit {proc.returncode}): {error_msg[:2000]}"

        return stdout.decode(errors="replace").strip()

    def _has_uncommitted_or_new_commits(self, worktree_dir: Path, base_branch: str) -> bool:
        """检查 worktree 是否有新 commit 或未提交的修改"""
        # Check uncommitted changes
        status = subprocess.run(
            ["git", "status", "--porcelain"], cwd=worktree_dir,
            capture_output=True, text=True
        )
        if status.stdout.strip():
            return True
        # Check new commits vs base
        log = subprocess.run(
            ["git", "log", f"origin/{base_branch}..HEAD", "--oneline"],
            cwd=worktree_dir, capture_output=True, text=True
        )
        return bool(log.stdout.strip())

    async def _create_merge_request(self, worktree_dir: Path, subtask: dict) -> str | None:
        """推送分支并创建 MR/PR"""
        # Push
        push_result = subprocess.run(
            ["git", "push", "-u", "origin", "HEAD"],
            cwd=worktree_dir, capture_output=True, text=True,
        )
        if push_result.returncode != 0:
            logger.error("Push failed: %s", push_result.stderr)
            return None

        # Detect platform
        remote_url = subprocess.run(
            ["git", "remote", "get-url", "origin"],
            cwd=worktree_dir, capture_output=True, text=True
        ).stdout.strip()

        goal = subtask.get("goal", "Auto fix")[:60]
        task_id = subtask.get("task_id", "")
        title = f"fix: {goal}"
        body = f"Auto-generated by Agent Factory\n\nTask ID: {task_id}"

        if "gitlab" in remote_url.lower():
            return await self._create_gitlab_mr(worktree_dir, title, body)
        else:
            return await self._create_github_pr(worktree_dir, title, body)

    async def _create_github_pr(self, worktree_dir: Path, title: str, body: str) -> str | None:
        proc = await asyncio.create_subprocess_exec(
            "gh", "pr", "create", "--title", title, "--body", body,
            cwd=str(worktree_dir),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()
        if proc.returncode != 0:
            logger.error("gh pr create failed: %s", stderr.decode(errors="replace"))
            return None
        return stdout.decode().strip()

    async def _create_gitlab_mr(self, worktree_dir: Path, title: str, body: str) -> str | None:
        proc = await asyncio.create_subprocess_exec(
            "glab", "mr", "create", "--title", title, "--description", body, "--yes",
            cwd=str(worktree_dir),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()
        if proc.returncode != 0:
            logger.error("glab mr create failed: %s", stderr.decode(errors="replace"))
            return None
        return stdout.decode().strip()
