export default async function TaskDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  return (
    <div className="p-8">
      <div className="text-[12px] text-[#555] mb-4">
        <a href="/" className="text-[#6366f1] hover:underline">Tasks</a> / Task #{id}
      </div>

      <div className="flex justify-between items-start mb-6">
        <div>
          <h1 className="text-xl font-semibold text-white mb-2">Task #{id}</h1>
          <div className="flex gap-4 items-center text-[12px] text-[#666]">
            <span className="px-2 py-0.5 rounded-full text-[11px] font-medium bg-[#1a1a2a] text-[#818cf8]">
              Loading...
            </span>
          </div>
        </div>
        <div className="flex gap-2">
          <button className="px-3.5 py-1.5 text-[12px] rounded-md border border-[#333] bg-[#1a1a1d] text-[#aaa] hover:bg-[#222] hover:text-white transition-colors">
            Clone
          </button>
          <button className="px-3.5 py-1.5 text-[12px] rounded-md bg-[#6366f1] text-white hover:bg-[#5558e6] transition-colors">
            Export
          </button>
        </div>
      </div>

      <div className="grid grid-cols-[1fr_300px] gap-6">
        <div>
          <div className="text-[11px] text-[#555] uppercase tracking-wider mb-3">Execution Timeline</div>
          <div className="bg-[#111113] border border-[#222] rounded-lg p-5 min-h-[300px] flex items-center justify-center text-[#555] text-[13px]">
            Timeline visualization will stream here via SSE when the task runs.
          </div>
        </div>

        <div>
          <div className="text-[11px] text-[#555] uppercase tracking-wider mb-3">Task Definition</div>
          <div className="text-[13px] text-[#888] leading-relaxed">
            Task details will load from the API.
          </div>

          <div className="text-[11px] text-[#555] uppercase tracking-wider mt-6 mb-3">Cost</div>
          <div className="text-[13px] text-[#888]">
            Cost tracking will appear here during execution.
          </div>

          <div className="text-[11px] text-[#555] uppercase tracking-wider mt-6 mb-3">Configuration</div>
          <div className="text-[13px] text-[#888]">
            Agent type, model, sandbox status will load from the API.
          </div>
        </div>
      </div>
    </div>
  );
}
