import type { Metadata } from "next";
import {
  Bot,
  Boxes,
  Building2,
  Cpu,
  ExternalLink,
  FlaskConical,
  Github,
  Globe,
  Linkedin,
  Mail,
  ShieldCheck,
} from "lucide-react";
import { PageHeader } from "@/components/layout/page-header";
import { Card, CardContent } from "@/components/ui/card";

export const metadata: Metadata = {
  title: "About · Brok's Forge",
  description: "The story behind Brok's Forge and the engineer who built it.",
};

const DEMONSTRATES = [
  { icon: Boxes, label: "Modern software architecture" },
  { icon: ShieldCheck, label: "Secure backend engineering" },
  { icon: Bot, label: "Agent orchestration" },
  { icon: FlaskConical, label: "Evaluation frameworks" },
  { icon: Building2, label: "Enterprise-grade development practices" },
  { icon: Cpu, label: "AI platform engineering" },
];

const AUDIENCE = [
  "LLM applications",
  "AI Infrastructure",
  "Autonomous Agents",
  "AI Developer Tools",
  "Enterprise AI Platforms",
];

type ExternalLinkItem = {
  icon: typeof Globe;
  label: string;
  value: string;
  href: string;
  external: boolean;
};

const LINKS: ExternalLinkItem[] = [
  { icon: Globe, label: "Portfolio", value: "gokul.quest", href: "https://gokul.quest", external: true },
  {
    icon: Github,
    label: "GitHub",
    value: "github.com/gokulraj9488",
    href: "https://github.com/gokulraj9488",
    external: true,
  },
  {
    icon: Linkedin,
    label: "LinkedIn",
    value: "in/gokul-raj3003",
    href: "https://www.linkedin.com/in/gokul-raj3003/",
    external: true,
  },
  {
    icon: Mail,
    label: "Email",
    value: "gokulraj.gokul3003@gmail.com",
    href: "mailto:gokulraj.gokul3003@gmail.com",
    external: false,
  },
];

export default function AboutPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="About"
        description="The story behind Brok's Forge and the engineer who built it."
      />

      {/* Intro */}
      <Card>
        <CardContent className="p-6 sm:p-8">
          <div className="flex items-center gap-3">
            <span className="text-2xl" aria-hidden="true">
              👨‍💻
            </span>
            <h2 className="text-xl font-semibold tracking-tight">Built by Gokulraj</h2>
          </div>
          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-muted-foreground">
            Brok&apos;s Forge was built as a production-quality AI Engineering Platform to demonstrate
            modern engineering across the full stack — from a secure, multi-tenant backend to a
            polished product experience.
          </p>

          <h3 className="mt-6 text-sm font-medium text-foreground">It demonstrates</h3>
          <ul className="mt-3 grid gap-2.5 sm:grid-cols-2">
            {DEMONSTRATES.map(({ icon: Icon, label }) => (
              <li key={label} className="flex items-center gap-3 text-sm text-muted-foreground">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                  <Icon className="h-4 w-4 text-primary" aria-hidden="true" />
                </span>
                {label}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      {/* Purpose */}
      <Card>
        <CardContent className="p-6 sm:p-8">
          <h2 className="text-lg font-semibold tracking-tight">Purpose</h2>
          <p className="mt-2 max-w-2xl text-sm leading-relaxed text-muted-foreground">
            The purpose of this project is to showcase engineering capability for companies building:
          </p>
          <ul className="mt-4 flex flex-wrap gap-2">
            {AUDIENCE.map((item) => (
              <li
                key={item}
                className="rounded-full border border-border bg-muted/40 px-3 py-1 text-xs font-medium text-foreground"
              >
                {item}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      {/* Connect */}
      <section aria-labelledby="connect-heading" className="space-y-3">
        <h2 id="connect-heading" className="text-lg font-semibold tracking-tight">
          Connect
        </h2>
        <div className="grid gap-3 sm:grid-cols-2">
          {LINKS.map(({ icon: Icon, label, value, href, external }) => (
            <a
              key={label}
              href={href}
              {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
              aria-label={
                external ? `${label}: ${value} (opens in a new tab)` : `${label}: ${value}`
              }
              className="group flex items-center gap-4 rounded-xl border border-border bg-card p-4 outline-none transition-colors hover:border-primary/40 focus-visible:ring-2 focus-visible:ring-ring"
            >
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                <Icon className="h-5 w-5 text-primary" aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-foreground">{label}</span>
                <span className="block truncate text-xs text-muted-foreground">{value}</span>
              </span>
              {external && (
                <ExternalLink
                  className="h-4 w-4 shrink-0 text-muted-foreground transition-colors group-hover:text-foreground"
                  aria-hidden="true"
                />
              )}
            </a>
          ))}
        </div>
      </section>

      <p className="text-xs text-muted-foreground">
        Brok&apos;s Forge is an open-source portfolio project · Apache-2.0 licensed.
      </p>
    </div>
  );
}
