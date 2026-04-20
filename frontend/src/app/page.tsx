"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, Task } from "@/lib/api";

const statusColors: Record<string, string> = {
  RUNNING: "bg-[#1a3a2a] text-[#4ade80]",
  PENDING: "bg-[#2a2a1a] text-[#facc15]",
  COMPLETED: "bg-[#1a1a2a] text-[#818cf8]",
  FAILED: "bg-[#2a1a1a] text-[#f87171]",
  ANALYZING: "bg-[#1a2a3a] text-[#60a5fa]",
};

export default function TaskListPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<string>("ALL");

  useEffect(() => {
    api.tasks
      .list()
      .then(setTasks)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="p-8">
        <h1 className="text-xl font-semibold text-white mb-6">Tasks</h1>
        {[...Array(4)].map((_, i) => (
          <div key={i} className="h-14 bg-[#1a1a1d] rounded mb-2 animate-pulse" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <h1 className="text-xl font-semibold text-white mb-6">Tasks</h1>
        <div className="bg-[#1a0a0a] border border-[#3a1a1a] rounded-lg p-4 flex gap-3">
          <span className="text-[#f87171] text-lg">!</span>
          <div>
            <div className="text-[13px] font-semibold text-[#f87171]">Failed to load tasks</div>
            <div className="text-[12px] text-[#888] mt-1">{error}</div>
          </div>
        </div>
      </div>
    );
  }

  if (tasks.length === 0) {
    return (
      <div className="p-8">
        <h1 className="text-xl font-semibold text-white mb-6">Tasks</h1>
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <div className="text-5xl text-[#333] mb-4">□</div>
          <div className="text-base font-semibold text-white mb-2">No tasks yet</div>
          <div className="text-[13px] text-[#666] max-w-[360px] leading-relaxed mb-5">
            Describe what you want to accomplish in plain language. An AI assistant will help you define the task clearly, then an agent will execute it.
          </div>
          <Link
            href="/tasks/new"
            className="px-5 py-2.5 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors"
          >
            Create your first task
          </Link>
        </div>
      </div>
    );
  }

  const counts: Record<string, number> = { ALL: tasks.length };
  for (const t of tasks) counts[t.status] = (counts[t.status] || 0) + 1;

  const filtered = filter === "ALL" ? tasks : tasks.filter((t) => t.status === filter);

  const tabs = [
    { key: "ALL", label: "All" },
    { key: "RUNNING", label: "Running" },
    { key: "PENDING", label: "Pending" },
    { key: "COMPLETED", label: "Completed" },
    { key: "FAILED", label: "Failed" },
  ];

  return (
    <div className="p-8">
      <div className="flex justify-between items-center mb-5">
        <h1 className="text-xl font-semibold text-white">Tasks</h1>
        <Link
          href="/tasks/new"
          className="px-4 py-2 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors"
        >
          + New Task
        </Link>
      </div>

      <div className="flex border-b border-[#222] mb-0">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setFilter(tab.key)}
            className={`px-5 py-3 text-[13px] border-b-2 transition-colors ${
              filter === tab.key
                ? "text-white border-[#6366f1]"
                : "text-[#666] border-transparent hover:text-[#aaa]"
            }`}
          >
            {tab.label} ({counts[tab.key] || 0})
          </button>
        ))}
      </div>

      <table className="w-full">
        <thead>
          <tr className="border-b border-[#222]">
            <th className="text-left py-2.5 px-3 text-[11px] text-[#555] uppercase tracking-wider">Task</th>
            <th className="text-left py-2.5 px-3 text-[11px] text-[#555] uppercase tracking-wider">Status</th>
            <th className="text-left py-2.5 px-3 text-[11px] text-[#555] uppercase tracking-wider">Agent / Model</th>
            <th className="text-left py-2.5 px-3 text-[11px] text-[#555] uppercase tracking-wider">Created</th>
          </tr>
        </thead>
        <tbody>
          {filtered.map((task) => (
            <tr key={task.id} className="border-b border-[#1a1a1d] hover:bg-[#111113] transition-colors">
              <td className="py-3 px-3">
                <Link href={`/tasks/${task.id}`} className="block">
                  <div className="text-[13px] font-medium text-white">{task.name}</div>
                  {task.description && (
                    <div className="text-[12px] text-[#666] mt-0.5 truncate max-w-md">{task.description}</div>
                  )}
                </Link>
              </td>
              <td className="py-3 px-3">
                <span className={`px-2 py-0.5 rounded-full text-[11px] font-medium ${statusColors[task.status] || ""}`}>
                  {task.status}
                </span>
              </td>
              <td className="py-3 px-3 font-mono text-[12px] text-[#888]">
                {task.agentType} / {task.modelId || "—"}
              </td>
              <td className="py-3 px-3 font-mono text-[12px] text-[#888]">
                {new Date(task.createdAt).toLocaleDateString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
