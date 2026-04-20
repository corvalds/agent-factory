"use client";

import { useState, useRef, useEffect } from "react";
import { useRouter } from "next/navigation";
import { api, DefineResponse } from "@/lib/api";

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export default function NewTaskPage() {
  const router = useRouter();
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [structured, setStructured] = useState<Record<string, string> | null>(null);
  const [isComplete, setIsComplete] = useState(false);
  const [turnCount, setTurnCount] = useState(0);

  const [agentType, setAgentType] = useState("general-purpose");
  const [modelId, setModelId] = useState("gpt-4o");
  const [sandboxEnabled, setSandboxEnabled] = useState(false);
  const [launching, setLaunching] = useState(false);

  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    api.define.start().then((res) => {
      setSessionId(res.sessionId);
      setMessages([{ role: "assistant", content: res.message }]);
    }).catch(() => {
      setMessages([{ role: "assistant", content: "Could not connect to Agent Factory API. Make sure the platform service is running on port 8080." }]);
    });
  }, []);

  async function sendMessage() {
    if (!input.trim() || !sessionId || sending) return;
    const msg = input.trim();
    setInput("");
    setMessages((prev) => [...prev, { role: "user", content: msg }]);
    setSending(true);

    try {
      const res: DefineResponse = await api.define.message(sessionId, msg, modelId);
      setMessages((prev) => [...prev, { role: "assistant", content: res.reply }]);
      setTurnCount((c) => c + 1);
      if (res.structured) setStructured(res.structured);
      if (res.isComplete) setIsComplete(true);
    } catch (e: any) {
      setMessages((prev) => [...prev, { role: "assistant", content: `Error: ${e.message}` }]);
    } finally {
      setSending(false);
    }
  }

  async function handleLaunch() {
    if (!sessionId || !structured || launching) return;
    setLaunching(true);
    try {
      const task = await api.define.confirm(sessionId, agentType, modelId, sandboxEnabled);
      await api.tasks.execute(task.id);
      router.push(`/tasks/${task.id}`);
    } catch (e: any) {
      alert(`Launch failed: ${e.message}`);
      setLaunching(false);
    }
  }

  return (
    <div className="p-8">
      <h1 className="text-xl font-semibold text-white mb-6">New Task</h1>
      <div className="grid grid-cols-[1fr_340px] gap-6">
        <div className="bg-[#111113] border border-[#222] rounded-lg p-5 flex flex-col" style={{ minHeight: 480 }}>
          <div className="flex-1 overflow-y-auto space-y-4 mb-4">
            {messages.map((msg, i) => (
              <div key={i} className={msg.role === "user" ? "pl-10" : ""}>
                <div className={`text-[11px] font-semibold mb-1 ${msg.role === "user" ? "text-[#4ade80]" : "text-[#6366f1]"}`}>
                  {msg.role === "user" ? "You" : "AI Assistant"}
                </div>
                <div className="text-[13px] text-[#ccc] leading-relaxed whitespace-pre-wrap">{msg.content}</div>
              </div>
            ))}
            {sending && (
              <div className="flex items-center gap-2 text-[12px] text-[#555]">
                <div className="w-2 h-2 bg-[#6366f1] rounded-full animate-pulse" />
                Thinking...
              </div>
            )}
            <div ref={chatEndRef} />
          </div>

          {structured && (
            <div className="border-t border-[#222] pt-3 mb-3 space-y-2">
              {["background", "goal", "acceptance_criteria"].map((key) => (
                <div key={key} className="bg-[#0a0a0b] border border-[#222] rounded-md p-3">
                  <div className="text-[10px] text-[#555] uppercase tracking-wider mb-1">
                    {key.replace("_", " ")}
                  </div>
                  <div className="text-[12px] text-[#aaa] leading-relaxed">
                    {structured[key] || "—"}
                  </div>
                </div>
              ))}
            </div>
          )}

          {turnCount >= 8 && turnCount < 10 && !isComplete && (
            <div className="text-[11px] text-[#facc15] mb-2">
              AI will finalize the task definition in {10 - turnCount} more message{10 - turnCount > 1 ? "s" : ""}.
            </div>
          )}

          <div className="border-t border-[#222] pt-4 flex gap-2">
            <input
              className="flex-1 bg-[#0a0a0b] border border-[#333] rounded-md px-3 py-2.5 text-[13px] text-white placeholder-[#555] disabled:opacity-50"
              placeholder={isComplete ? "Task definition complete. Configure and launch." : "Describe what you want to accomplish..."}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && sendMessage()}
              disabled={isComplete || sending}
            />
            <button
              onClick={sendMessage}
              disabled={isComplete || sending || !input.trim()}
              className="px-4 py-2.5 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors disabled:opacity-50"
            >
              Send
            </button>
          </div>
        </div>

        <div className="bg-[#111113] border border-[#222] rounded-lg p-5">
          <div className="text-[13px] font-semibold text-white mb-4">Task Configuration</div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Agent Type</label>
            <select
              className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white"
              value={agentType}
              onChange={(e) => setAgentType(e.target.value)}
            >
              <option value="web-scraper">web-scraper</option>
              <option value="code-analyst">code-analyst</option>
              <option value="general-purpose">general-purpose</option>
            </select>
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">LLM Model</label>
            <select
              className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white"
              value={modelId}
              onChange={(e) => setModelId(e.target.value)}
            >
              <option value="gpt-4o">gpt-4o</option>
              <option value="gpt-4o-mini">gpt-4o-mini</option>
              <option value="claude-sonnet-4">claude-sonnet-4</option>
              <option value="claude-opus-4">claude-opus-4</option>
              <option value="deepseek-v3">deepseek-v3</option>
            </select>
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Sandbox</label>
            <label className="flex items-center gap-2 text-[13px] text-[#ccc] cursor-pointer" onClick={() => setSandboxEnabled(!sandboxEnabled)}>
              <div className={`w-9 h-5 rounded-full relative transition-colors ${sandboxEnabled ? "bg-[#6366f1]" : "bg-[#333]"}`}>
                <div className={`absolute top-0.5 w-4 h-4 bg-white rounded-full transition-all ${sandboxEnabled ? "left-[18px]" : "left-0.5"}`} />
              </div>
              Enable Docker sandbox
            </label>
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Sandbox Image</label>
            <select className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white">
              <option value="python:3.12-slim">python:3.12-slim</option>
              <option value="node:20-slim">node:20-slim</option>
              <option value="ubuntu:24.04">ubuntu:24.04</option>
              <option value="custom">Custom...</option>
            </select>
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Dependencies</label>
            <input
              className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white placeholder-[#555]"
              placeholder="e.g. requests, beautifulsoup4"
            />
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Timeout</label>
            <select className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white">
              <option value="5">5 minutes</option>
              <option value="15">15 minutes</option>
              <option value="30">30 minutes</option>
              <option value="60">1 hour</option>
            </select>
          </div>

          <button
            onClick={handleLaunch}
            disabled={!structured || launching}
            className="w-full py-3 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors disabled:opacity-50"
          >
            {launching ? "Launching..." : "Launch Task"}
          </button>

          {!structured && (
            <div className="text-[11px] text-[#555] mt-2 text-center">
              Complete the conversation to enable launch
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
