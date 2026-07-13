"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BarChart3,
  Bot,
  Building2,
  Database,
  FileText,
  FlaskConical,
  FolderKanban,
  Gauge,
  LayoutGrid,
  Lightbulb,
  Network,
  Plug,
  Settings,
  Trophy,
} from "lucide-react";
import { cn } from "@/lib/utils";

type NavItem = { href: string; label: string; icon: typeof LayoutGrid };
type NavGroup = { label: string; items: NavItem[] };

// Grouped by workflow (Build → Evaluate → Observe) rather than by database entity, so the
// sidebar reads like one engineering workflow instead of a table-of-contents for the schema.
const NAV_GROUPS: NavGroup[] = [
  {
    label: "Workspace",
    items: [
      { href: "/dashboard", label: "Overview", icon: LayoutGrid },
      { href: "/organizations", label: "Organizations", icon: Building2 },
    ],
  },
  {
    label: "Build",
    items: [
      { href: "/projects", label: "Projects", icon: FolderKanban },
      { href: "/agents", label: "Agents", icon: Bot },
      { href: "/providers", label: "Providers", icon: Plug },
    ],
  },
  {
    label: "Evaluate",
    items: [
      { href: "/datasets", label: "Datasets", icon: Database },
      { href: "/benchmarks", label: "Benchmarks", icon: Trophy },
      { href: "/prompts", label: "Prompts", icon: FileText },
      { href: "/evaluations", label: "Evaluations", icon: FlaskConical },
    ],
  },
  {
    label: "Observe",
    items: [
      { href: "/analytics", label: "Analytics", icon: BarChart3 },
      { href: "/insights", label: "Insights", icon: Gauge },
      { href: "/advisor", label: "Advisor", icon: Lightbulb },
      { href: "/knowledge", label: "Knowledge", icon: Network },
    ],
  },
  {
    label: "",
    items: [{ href: "/settings", label: "Settings", icon: Settings }],
  },
];

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();

  return (
    <nav className="flex flex-1 flex-col gap-6 px-3 py-5">
      {NAV_GROUPS.map((group, groupIndex) => (
        <div key={group.label || `group-${groupIndex}`} className="flex flex-col gap-1">
          {group.label && (
            <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
              {group.label}
            </p>
          )}
          {group.items.map((item) => {
            const active = pathname === item.href || pathname.startsWith(item.href + "/");
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onNavigate}
                className={cn(
                  "group flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  active
                    ? "bg-accent text-accent-foreground"
                    : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
                )}
              >
                <Icon
                  className={cn(
                    "h-4 w-4 shrink-0 transition-colors",
                    active ? "text-primary" : "text-muted-foreground group-hover:text-foreground",
                  )}
                />
                {item.label}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
