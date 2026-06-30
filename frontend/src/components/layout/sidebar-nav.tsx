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
  Settings,
  Trophy,
} from "lucide-react";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutGrid },
  { href: "/organizations", label: "Organizations", icon: Building2 },
  { href: "/projects", label: "Projects", icon: FolderKanban },
  { href: "/agents", label: "Agents", icon: Bot },
  { href: "/datasets", label: "Datasets", icon: Database },
  { href: "/prompts", label: "Prompts", icon: FileText },
  { href: "/evaluations", label: "Evaluations", icon: FlaskConical },
  { href: "/benchmarks", label: "Benchmarks", icon: Trophy },
  { href: "/analytics", label: "Analytics", icon: BarChart3 },
  { href: "/insights", label: "Insights", icon: Gauge },
  { href: "/advisor", label: "Advisor", icon: Lightbulb },
  { href: "/knowledge", label: "Knowledge", icon: Network },
  { href: "/settings", label: "Settings", icon: Settings },
];

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();

  return (
    <nav className="flex flex-1 flex-col gap-1 px-3 py-4">
      {NAV_ITEMS.map((item) => {
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
    </nav>
  );
}
