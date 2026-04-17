export default function NewTaskPage() {
  return (
    <div className="p-8">
      <h1 className="text-xl font-semibold text-white mb-6">New Task</h1>
      <div className="grid grid-cols-[1fr_340px] gap-6">
        <div className="bg-[#111113] border border-[#222] rounded-lg p-5 min-h-[400px] flex flex-col">
          <div className="flex-1 flex items-center justify-center text-[#555] text-[13px]">
            AI-assisted task definition chat will be implemented here.
            <br />
            Describe your task in plain language to get started.
          </div>
          <div className="border-t border-[#222] pt-4 flex gap-2">
            <input
              className="flex-1 bg-[#0a0a0b] border border-[#333] rounded-md px-3 py-2.5 text-[13px] text-white placeholder-[#555]"
              placeholder="Describe what you want to accomplish..."
            />
            <button className="px-4 py-2.5 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors">
              Send
            </button>
          </div>
        </div>

        <div className="bg-[#111113] border border-[#222] rounded-lg p-5">
          <div className="text-[13px] font-semibold text-white mb-4">Task Configuration</div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Agent Type</label>
            <select className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white">
              <option>web-scraper</option>
              <option>code-analyst</option>
              <option>general-purpose</option>
            </select>
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">LLM Model</label>
            <select className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white">
              <option>gpt-4o</option>
              <option>claude-sonnet-4</option>
              <option>claude-opus-4</option>
            </select>
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Sandbox</label>
            <div className="flex items-center gap-2 text-[13px]">
              <input type="checkbox" defaultChecked className="accent-[#6366f1]" />
              <span>Enable Docker sandbox</span>
            </div>
          </div>

          <div className="mb-4">
            <label className="block text-[11px] text-[#666] uppercase tracking-wider mb-1.5">Timeout</label>
            <select className="w-full bg-[#0a0a0b] border border-[#333] rounded-md px-2.5 py-2 text-[13px] text-white">
              <option>5 minutes</option>
              <option>15 minutes</option>
              <option>30 minutes</option>
              <option>1 hour</option>
            </select>
          </div>

          <div className="mb-4 text-[12px] text-[#888]">
            Estimated cost: <span className="text-[#ccc] font-mono">—</span>
          </div>

          <button className="w-full py-3 bg-[#6366f1] text-white rounded-md text-[13px] hover:bg-[#5558e6] transition-colors">
            Launch Task
          </button>
        </div>
      </div>
    </div>
  );
}
