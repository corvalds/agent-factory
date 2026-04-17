"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const nav = [
  { label: "Tasks", href: "/", icon: "□" },
  { label: "New Task", href: "/tasks/new", icon: "+" },
  { label: "LLM Providers", href: "/settings", icon: "◇" },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-[220px] bg-[#111113] border-r border-[#222] flex-shrink-0 flex flex-col">
      <div className="px-5 py-5 font-mono text-[15px] font-semibold text-white">
        <span className="text-[#6366f1]">&gt;</span> agent-factory
      </div>

      <div className="px-5 pt-4 pb-1.5 text-[11px] text-[#555] uppercase tracking-wider">
        Core
      </div>
      {nav.map((item) => {
        const active =
          item.href === "/"
            ? pathname === "/"
            : pathname.startsWith(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`flex items-center gap-2 px-5 py-2 text-[13px] transition-colors ${
              active
                ? "text-white bg-[#1a1a1d] border-r-2 border-[#6366f1]"
                : "text-[#888] hover:text-[#ccc] hover:bg-[#1a1a1d]"
            }`}
          >
            <span className="w-4 text-center text-xs">{item.icon}</span>
            {item.label}
          </Link>
        );
      })}

      <div className="px-5 pt-4 pb-1.5 text-[11px] text-[#555] uppercase tracking-wider">
        Resources
      </div>
      <div className="flex items-center gap-2 px-5 py-2 text-[13px] text-[#444]">
        <span className="w-4 text-center text-xs">◆</span>
        Knowledge Base
        <span className="text-[10px] text-[#444]">(V2)</span>
      </div>
    </aside>
  );
}
