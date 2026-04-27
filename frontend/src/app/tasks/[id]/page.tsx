"use client";

import { useEffect, useState, useRef } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, Task, TaskEvent, Artifact, createEventSource } from "@/lib/api";

const phaseColors: Record<string, { dot: string; text: string }> = {
  observe: { dot: "border-[#60a5fa] bg-[#1a2a3a]", text: "text-[#60a5fa]" },
  think: { dot: "border-[#a78bfa] bg-[#1a1a2a]", text: "text-[#a78bfa]" },
  act: { dot: "border-[#4ade80] bg-[#1a3a2a]", text: "text-[#4ade80]" },
  check: { dot: "border-[#facc15] bg-[#2a2a1a]", text: "text-[#facc15]" },
  error: { dot: "border-[#f87171] bg-[#2a1a1a]", text: "text-[#f87171]" },
};

const statusColors: Record<string, string> = {
  RUNNING: "bg-[#1a3a2a] text-[#4ade80]",
  PENDING: "bg-[#2a2a1a] text-[#facc15]",
  COMPLETED: "bg-[#1a1a2a] text-[#818cf8]",
  FAILED: "bg-[#2a1a1a] text-[#f87171]",
  ANALYZING: "bg-[#1a2a3a] text-[#60a5fa]",
};

function parseEventData(data: string): Record<string, any> {
  try { return JSON.parse(data); } catch { return {}; }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

export default function TaskDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = Number(params.id);

  const [task, setTask] = useState<Task | null>(null);
  const [events, setEvents] = useState<TaskEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalCost, setTotalCost] = useState("0");
  const [totalTokens, setTotalTokens] = useState(0);
  const esRef = useRef<EventSource | null>(null);
  const [error, setError] = useState<string | null>(null);
  const retryCountRef = useRef(0);
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);

  useEffect(() => {
    Promise.all([api.tasks.get(id), api.tasks.events(id), api.artifacts.list(id)])
      .then(([t, evts, arts]) => {
        setTask(t);
        setEvents(evts);
        setArtifacts(arts);
        computeCost(evts);
        if (t.status === "RUNNING" || t.status === "ANALYZING") {
          connectSSE(evts.length > 0 ? String(evts[evts.length - 1].id) : undefined);
        }
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));

    return () => { esRef.current?.close(); };
  }, [id]);

  function connectSSE(lastId?: string) {
    const es = createEventSource(id, lastId);
    esRef.current = es;

    const handler = (e: MessageEvent) => {
      retryCountRef.current = 0;
      try {
        const evt: TaskEvent = JSON.parse(e.data);
        setEvents((prev) => [...prev, evt]);
        if (evt.eventType === "COST") {
          const d = parseEventData(evt.data);
          setTotalTokens((t) => t + (d.tokens_in || 0) + (d.tokens_out || 0));
        }
        if (evt.eventType === "COMPLETION") {
          const d = parseEventData(evt.data);
          setTotalCost(d.total_cost_usd || "0");
          setTotalTokens(d.total_tokens || 0);
          api.tasks.get(id).then(setTask);
          api.artifacts.list(id).then(setArtifacts);
          es.close();
        }
      } catch {}
    };

    es.addEventListener("step", handler);
    es.addEventListener("cost", handler);
    es.addEventListener("error", handler);
    es.addEventListener("completion", handler);
    es.addEventListener("artifact", () => {
      api.artifacts.list(id).then(setArtifacts);
    });
    es.onerror = () => {
      es.close();
      retryCountRef.current += 1;
      if (retryCountRef.current > 10) return;
      const delay = Math.min(2000 * Math.pow(2, retryCountRef.current - 1), 30000);
      setTimeout(() => {
        api.tasks.get(id).then((t) => {
          setTask(t);
          if (t.status === "RUNNING" || t.status === "ANALYZING") connectSSE();
        }).catch(() => {});
      }, delay);
    };
  }

  function computeCost(evts: TaskEvent[]) {
    let cost = 0, tokens = 0;
    for (const evt of evts) {
      if (evt.eventType === "COST") {
        const d = parseEventData(evt.data);
        cost += parseFloat(d.cost_usd || "0");
        tokens += (d.tokens_in || 0) + (d.tokens_out || 0);
      }
      if (evt.eventType === "COMPLETION") {
        const d = parseEventData(evt.data);
        cost = parseFloat(d.total_cost_usd || String(cost));
        tokens = d.total_tokens || tokens;
      }
    }
    setTotalCost(cost.toFixed(6));
    setTotalTokens(tokens);
  }

  if (loading) {
    return (
      <div className="p-8">
        <div className="h-8 w-64 bg-[#1a1a1d] rounded animate-pulse mb-4" />
        <div className="h-64 bg-[#1a1a1d] rounded-lg animate-pulse" />
      </div>
    );
  }

  if (!task) {
    return (
      <div className="p-8">
        <div className="bg-[#1a0a0a] border border-[#3a1a1a] rounded-lg p-4 flex gap-3">
          <span className="text-[#f87171] text-lg">!</span>
          <div>
            <div className="text-[13px] font-semibold text-[#f87171]">{error ? "Failed to load task" : "Task not found"}</div>
            {error && <div className="text-[12px] text-[#888] mt-1">{error}</div>}
          </div>
        </div>
      </div>
    );
  }

  const isRunning = task.status === "RUNNING" || task.status === "ANALYZING";
  const stepEvents = events.filter((e) => e.eventType === "STEP");

  return (
    <div className="p-8">
      <div className="text-[12px] text-[#555] mb-4">
        <a href="/" className="text-[#6366f1] hover:underline">Tasks</a> / {task.name}
      </div>

      <div className="flex justify-between items-start mb-6">
        <div>
          <h1 className="text-xl font-semibold text-white mb-2">{task.name}</h1>
          <div className="flex gap-4 items-center text-[12px] text-[#666]">
            <span className={`px-2 py-0.5 rounded-full text-[11px] font-medium ${statusColors[task.status]}`}>
              {task.status}
            </span>
            <span>{task.agentType} / {task.modelId || "—"}</span>
            <span className="font-mono">Step {stepEvents.length}</span>
            {totalTokens > 0 && <span className="font-mono">{totalTokens.toLocaleString()} tokens</span>}
            {parseFloat(totalCost) > 0 && <span className="font-mono">${totalCost}</span>}
            {isRunning && <span className="flex items-center gap-1"><span className="w-2 h-2 bg-[#4ade80] rounded-full animate-pulse" /> Running</span>}
          </div>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => api.tasks.clone(id).then((t) => router.push(`/tasks/new`))}
            className="px-3.5 py-1.5 text-[12px] rounded-md border border-[#333] bg-[#1a1a1d] text-[#aaa] hover:bg-[#222] hover:text-white transition-colors"
          >
            Clone
          </button>
          {artifacts.length > 0 && (
            <a
              href={api.artifacts.downloadAllUrl(id)}
              className="px-3.5 py-1.5 text-[12px] rounded-md bg-[#6366f1] text-white hover:bg-[#5558e6] transition-colors"
            >
              Export
            </a>
          )}
        </div>
      </div>

      <div className="grid grid-cols-[1fr_300px] gap-6">
        <div>
          {task.status === "FAILED" && task.error && (
            <div className="bg-[#1a0a0a] border border-[#3a1a1a] rounded-lg p-4 flex gap-3 mb-4">
              <span className="text-[#f87171] text-lg">!</span>
              <div>
                <div className="text-[13px] font-semibold text-[#f87171]">Task failed</div>
                <div className="text-[12px] text-[#888] mt-1">{task.error}</div>
              </div>
            </div>
          )}

          <div className="relative">
            {stepEvents.length > 0 && <div className="absolute left-[18px] top-0 bottom-0 w-0.5 bg-[#222]" />}
            {stepEvents.map((evt) => {
              const d = parseEventData(evt.data);
              const phase = d.phase || "act";
              const colors = phaseColors[phase] || phaseColors.act;
              return (
                <div key={evt.id} className="relative pl-12 mb-4">
                  <div className={`absolute left-[11px] top-1 w-4 h-4 rounded-full border-2 ${colors.dot}`} />
                  <div className="bg-[#111113] border border-[#222] rounded-lg p-3">
                    <div className="flex justify-between items-center mb-1">
                      <span className={`text-[11px] font-semibold uppercase tracking-wider ${colors.text}`}>
                        Step {d.step_number || "?"} · {phase}
                      </span>
                      <div className="flex gap-2 text-[11px] text-[#555] font-mono">
                        {evt.durationMs && <span className="bg-[#1a1a1d] px-1.5 py-0.5 rounded">{(evt.durationMs / 1000).toFixed(1)}s</span>}
                        <span>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                      </div>
                    </div>
                    <div className="text-[13px] text-[#aaa] leading-relaxed whitespace-pre-wrap">
                      {d.output || d.message || ""}
                    </div>
                  </div>
                </div>
              );
            })}
            {isRunning && (
              <div className="flex items-center gap-2 pl-12 py-3">
                <div className="w-2 h-2 bg-[#4ade80] rounded-full animate-pulse" />
                <span className="text-[12px] text-[#555]">Agent is working...</span>
              </div>
            )}
            {stepEvents.length === 0 && !isRunning && (
              <div className="text-[13px] text-[#555] text-center py-12">No execution steps recorded.</div>
            )}
          </div>

          {artifacts.length > 0 && (
            <div className="bg-[#111113] border border-[#222] rounded-lg p-4 mt-4">
              <div className="text-[13px] font-semibold text-white mb-2">Artifacts</div>
              <div className="space-y-2">
                {artifacts.map((art) => (
                  <div key={art.id} className="flex items-center justify-between bg-[#0a0a0b] border border-[#1a1a1d] rounded-md p-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <span className="text-[11px] text-[#555] font-mono uppercase">{art.artifactType === "PRIMARY" ? "PRI" : "SUP"}</span>
                      <div className="min-w-0">
                        <div className="text-[13px] text-[#ccc] truncate">{art.filename}</div>
                        <div className="text-[11px] text-[#555]">{art.mimeType} · {formatBytes(art.sizeBytes)}</div>
                      </div>
                    </div>
                    {art.mimeType.startsWith("image/") ? (
                      <a href={api.artifacts.contentUrl(art.id)} target="_blank" rel="noopener noreferrer"
                        className="px-2.5 py-1 text-[11px] rounded border border-[#333] text-[#aaa] hover:text-white hover:bg-[#222] transition-colors shrink-0">
                        View
                      </a>
                    ) : (
                      <a href={api.artifacts.contentUrl(art.id)}
                        className="px-2.5 py-1 text-[11px] rounded border border-[#333] text-[#aaa] hover:text-white hover:bg-[#222] transition-colors shrink-0">
                        Download
                      </a>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="space-y-4">
          <div>
            <div className="text-[11px] text-[#555] uppercase tracking-wider mb-2 font-semibold">Task Definition</div>
            {task.background && (
              <div className="mb-2">
                <div className="text-[10px] text-[#444] uppercase mb-0.5">Background</div>
                <div className="text-[13px] text-[#ccc] leading-relaxed">{task.background}</div>
              </div>
            )}
            {task.goal && (
              <div className="mb-2">
                <div className="text-[10px] text-[#444] uppercase mb-0.5">Goal</div>
                <div className="text-[13px] text-[#ccc] leading-relaxed">{task.goal}</div>
              </div>
            )}
            {task.acceptanceCriteria && (
              <div className="mb-2">
                <div className="text-[10px] text-[#444] uppercase mb-0.5">Acceptance Criteria</div>
                <div className="text-[13px] text-[#ccc] leading-relaxed">{task.acceptanceCriteria}</div>
              </div>
            )}
          </div>

          <div className="border-t border-[#222] pt-4">
            <div className="text-[11px] text-[#555] uppercase tracking-wider mb-2 font-semibold">Configuration</div>
            <div className="space-y-1 text-[12px]">
              <div className="flex justify-between"><span className="text-[#666]">Agent</span><span className="text-[#ccc] font-mono">{task.agentType}</span></div>
              <div className="flex justify-between"><span className="text-[#666]">Model</span><span className="text-[#ccc] font-mono">{task.modelId || "—"}</span></div>
              <div className="flex justify-between"><span className="text-[#666]">Sandbox</span><span className="text-[#ccc] font-mono">{task.sandboxEnabled ? "Docker" : "None"}</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
