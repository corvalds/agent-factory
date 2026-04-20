"use client";

import { useEffect, useState } from "react";
import { api, Provider } from "@/lib/api";

type TestState = "idle" | "testing" | "success" | "error";

export default function SettingsPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [testStates, setTestStates] = useState<Record<number, { state: TestState; msg: string }>>({});
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);

  const [formName, setFormName] = useState("");
  const [formType, setFormType] = useState<"OPENAI" | "ANTHROPIC" | "DEEPSEEK" | "CUSTOM">("OPENAI");
  const [formApiKey, setFormApiKey] = useState("");
  const [formBaseUrl, setFormBaseUrl] = useState("");
  const [formModels, setFormModels] = useState("");
  const [formSaving, setFormSaving] = useState(false);

  function loadProviders() {
    api.providers.list()
      .then(setProviders)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }

  useEffect(() => { loadProviders(); }, []);

  async function handleAdd() {
    if (!formName || !formApiKey) return;
    setFormSaving(true);
    try {
      await api.providers.create({ name: formName, type: formType, apiKey: formApiKey, baseUrl: formBaseUrl || undefined, models: formModels });
      setShowAdd(false);
      setFormName(""); setFormType("OPENAI"); setFormApiKey(""); setFormBaseUrl(""); setFormModels("");
      loadProviders();
    } catch (e: any) {
      alert(`Failed: ${e.message}`);
    } finally {
      setFormSaving(false);
    }
  }

  async function handleTest(id: number) {
    setTestStates((s) => ({ ...s, [id]: { state: "testing", msg: "" } }));
    try {
      const res = await api.providers.test(id);
      setTestStates((s) => ({ ...s, [id]: { state: res.success ? "success" : "error", msg: res.message } }));
      if (res.success) setTimeout(() => setTestStates((s) => ({ ...s, [id]: { state: "idle", msg: "" } })), 5000);
    } catch (e: any) {
      setTestStates((s) => ({ ...s, [id]: { state: "error", msg: e.message } }));
    }
  }

  async function handleDelete(id: number) {
    try {
      await api.providers.delete(id);
      setDeleteConfirm(null);
      loadProviders();
    } catch (e: any) {
      alert(`Cannot delete: ${e.message}`);
      setDeleteConfirm(null);
    }
  }

  if (loading) {
    return (
      <div className="p-8">
        <h1 className="text-xl font-semibold text-white mb-6">LLM Providers</h1>
        <div className="grid grid-cols-2 gap-4">
          {[...Array(2)].map((_, i) => <div key={i} className="h-48 bg-[#1a1a1d] rounded-lg animate-pulse" />)}
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-xl font-semibold text-white">LLM Providers</h1>
        <button onClick={() => setShowAdd(true)} className="px-4 py-2 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors">
          + Add Provider
        </button>
      </div>

      {error && (
        <div className="bg-[#1a0a0a] border border-[#3a1a1a] rounded-lg p-4 mb-4 flex gap-3">
          <span className="text-[#f87171]">!</span>
          <div className="text-[12px] text-[#888]">{error}</div>
        </div>
      )}

      {showAdd && (
        <div className="bg-[#111113] border border-[#6366f1] rounded-lg p-5 mb-6">
          <div className="text-[13px] font-semibold text-white mb-4">Add Provider</div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[11px] text-[#666] uppercase mb-1">Name</label>
              <input className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white" value={formName} onChange={(e) => setFormName(e.target.value)} placeholder="OpenAI" />
            </div>
            <div>
              <label className="block text-[11px] text-[#666] uppercase mb-1">Type</label>
              <select className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white" value={formType} onChange={(e) => setFormType(e.target.value as any)}>
                <option value="OPENAI">OpenAI</option>
                <option value="ANTHROPIC">Anthropic</option>
                <option value="DEEPSEEK">DeepSeek</option>
                <option value="CUSTOM">Custom</option>
              </select>
            </div>
            <div>
              <label className="block text-[11px] text-[#666] uppercase mb-1">API Key</label>
              <input type="password" className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white" value={formApiKey} onChange={(e) => setFormApiKey(e.target.value)} placeholder="sk-..." />
            </div>
            <div>
              <label className="block text-[11px] text-[#666] uppercase mb-1">Base URL (optional)</label>
              <input className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white" value={formBaseUrl} onChange={(e) => setFormBaseUrl(e.target.value)} placeholder="https://api.openai.com" />
            </div>
            <div className="col-span-2">
              <label className="block text-[11px] text-[#666] uppercase mb-1">Models (comma-separated)</label>
              <input className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white" value={formModels} onChange={(e) => setFormModels(e.target.value)} placeholder="gpt-4o, gpt-4o-mini" />
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <button onClick={handleAdd} disabled={formSaving || !formName || !formApiKey} className="px-4 py-2 bg-[#6366f1] text-white rounded-md text-[13px] disabled:opacity-50">
              {formSaving ? "Saving..." : "Save"}
            </button>
            <button onClick={() => setShowAdd(false)} className="px-4 py-2 border border-[#333] text-[#aaa] rounded-md text-[13px]">Cancel</button>
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4">
        {providers.map((p) => {
          const ts = testStates[p.id] || { state: "idle", msg: "" };
          return (
            <div key={p.id} className="bg-[#111113] border border-[#222] rounded-lg p-5">
              <div className="flex justify-between items-center mb-4">
                <span className="text-[14px] font-semibold text-white">{p.name}</span>
                <span className={`text-[11px] px-2 py-0.5 rounded ${p.active ? "bg-[#1a3a2a] text-[#4ade80]" : "bg-[#2a1a1a] text-[#f87171]"}`}>
                  {p.active ? "Connected" : "Inactive"}
                </span>
              </div>
              <div className="space-y-1">
                <div className="flex justify-between text-[12px] py-1.5 border-b border-[#1a1a1d]">
                  <span className="text-[#666]">API Key</span><span className="text-[#ccc] font-mono">••••••••</span>
                </div>
                <div className="flex justify-between text-[12px] py-1.5 border-b border-[#1a1a1d]">
                  <span className="text-[#666]">Type</span><span className="text-[#ccc] font-mono">{p.type}</span>
                </div>
                <div className="flex justify-between text-[12px] py-1.5 border-b border-[#1a1a1d]">
                  <span className="text-[#666]">Models</span><span className="text-[#ccc] font-mono text-right max-w-[200px] truncate">{p.models || "—"}</span>
                </div>
              </div>

              {ts.state === "error" && (
                <div className="text-[11px] text-[#f87171] mt-2">{ts.msg}</div>
              )}
              {ts.state === "success" && (
                <div className="text-[11px] text-[#4ade80] mt-2">{ts.msg}</div>
              )}

              <div className="flex gap-2 mt-4">
                <button
                  onClick={() => handleTest(p.id)}
                  disabled={ts.state === "testing"}
                  className="px-2.5 py-1 text-[11px] bg-[#1a1a1d] border border-[#333] text-[#aaa] rounded hover:bg-[#222] transition-colors disabled:opacity-50"
                >
                  {ts.state === "testing" ? "Testing..." : "Test Connection"}
                </button>
                {deleteConfirm === p.id ? (
                  <>
                    <button onClick={() => handleDelete(p.id)} className="px-2.5 py-1 text-[11px] border border-[#f87171] text-[#f87171] rounded">Confirm Delete</button>
                    <button onClick={() => setDeleteConfirm(null)} className="px-2.5 py-1 text-[11px] border border-[#333] text-[#aaa] rounded">Cancel</button>
                  </>
                ) : (
                  <button onClick={() => setDeleteConfirm(p.id)} className="px-2.5 py-1 text-[11px] bg-[#1a1a1d] border border-[#333] text-[#aaa] rounded hover:border-[#f87171] hover:text-[#f87171] transition-colors">
                    Delete
                  </button>
                )}
              </div>
            </div>
          );
        })}

        <div
          onClick={() => setShowAdd(true)}
          className="border border-dashed border-[#333] rounded-lg flex items-center justify-center min-h-[180px] hover:border-[#555] transition-colors cursor-pointer"
        >
          <div className="text-center text-[#555]">
            <div className="text-2xl mb-2">+</div>
            <div className="text-[13px]">Add Custom Provider</div>
            <div className="text-[11px] text-[#444] mt-1">OpenAI-compatible API</div>
          </div>
        </div>
      </div>
    </div>
  );
}
