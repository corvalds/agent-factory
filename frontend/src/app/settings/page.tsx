"use client";

import { useEffect, useState } from "react";
import { api, Provider } from "@/lib/api";

export default function SettingsPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.providers
      .list()
      .then(setProviders)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="p-8">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-xl font-semibold text-white">LLM Providers</h1>
        <button className="px-4 py-2 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors">
          + Add Provider
        </button>
      </div>

      {loading ? (
        <div className="grid grid-cols-2 gap-4">
          {[...Array(2)].map((_, i) => (
            <div key={i} className="h-48 bg-[#1a1a1d] rounded-lg animate-pulse" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {providers.map((provider) => (
            <div key={provider.id} className="bg-[#111113] border border-[#222] rounded-lg p-5">
              <div className="flex justify-between items-center mb-4">
                <span className="text-[14px] font-semibold text-white">{provider.name}</span>
                <span
                  className={`text-[11px] px-2 py-0.5 rounded ${
                    provider.active
                      ? "bg-[#1a3a2a] text-[#4ade80]"
                      : "bg-[#2a1a1a] text-[#f87171]"
                  }`}
                >
                  {provider.active ? "Connected" : "Inactive"}
                </span>
              </div>
              <div className="space-y-2">
                <div className="flex justify-between text-[12px] py-1.5 border-b border-[#1a1a1d]">
                  <span className="text-[#666]">Type</span>
                  <span className="text-[#ccc] font-mono">{provider.type}</span>
                </div>
                <div className="flex justify-between text-[12px] py-1.5 border-b border-[#1a1a1d]">
                  <span className="text-[#666]">Models</span>
                  <span className="text-[#ccc] font-mono text-right max-w-[200px] truncate">{provider.models}</span>
                </div>
              </div>
              <div className="flex gap-2 mt-4">
                <button className="px-2.5 py-1 text-[11px] bg-[#1a1a1d] border border-[#333] text-[#aaa] rounded hover:bg-[#222] transition-colors">
                  Edit
                </button>
                <button className="px-2.5 py-1 text-[11px] bg-[#1a1a1d] border border-[#333] text-[#aaa] rounded hover:bg-[#222] transition-colors">
                  Test Connection
                </button>
              </div>
            </div>
          ))}

          <div className="border border-dashed border-[#333] rounded-lg flex items-center justify-center min-h-[180px] hover:border-[#555] transition-colors cursor-pointer">
            <div className="text-center text-[#555]">
              <div className="text-2xl mb-2">+</div>
              <div className="text-[13px]">Add Custom Provider</div>
              <div className="text-[11px] text-[#444] mt-1">OpenAI-compatible API</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
