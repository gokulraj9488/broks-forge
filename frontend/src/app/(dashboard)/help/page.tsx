import type { Metadata } from "next";
import Link from "next/link";
import { BookOpen, KeyRound, LifeBuoy, PlayCircle, ShieldCheck, Stethoscope } from "lucide-react";
import { PageHeader } from "@/components/layout/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { ContactDeveloperButton } from "@/components/common/contact-developer";

export const metadata: Metadata = {
  title: "Help",
  description: "Guides for getting the most out of Brok's Forge, and how to reach the developer.",
};

const TOPICS = [
  {
    icon: PlayCircle,
    title: "Run an evaluation",
    body: "Register an agent, import a dataset, then open Evaluations → New evaluation. The dialog guides you through any missing prerequisites.",
  },
  {
    icon: KeyRound,
    title: "Connect a credential",
    body: "On an agent's Credentials tab, add the API key/header your agent needs. Use Test connection to verify it before you save — secrets are encrypted at rest and never shown again.",
  },
  {
    icon: Stethoscope,
    title: "Check agent health",
    body: "The Health tab probes your agent the right way automatically — /actuator/health for Spring Boot, /health for FastAPI/LangGraph, or a tiny completion for provider-backed agents.",
  },
  {
    icon: ShieldCheck,
    title: "Change your password",
    body: "Settings → Security emails you a 6-digit code, then lets you set a new password. All sessions sign out afterwards.",
  },
];

export default function HelpPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Help & support" description="Quick answers, and a direct line to the developer." />

      <Card>
        <CardContent className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <LifeBuoy className="h-5 w-5 text-primary" aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-base font-semibold">Need a hand?</h2>
              <p className="text-sm text-muted-foreground">
                Reach out for questions, feedback or bug reports — happy to help.
              </p>
            </div>
          </div>
          <ContactDeveloperButton variant="default" />
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2">
        {TOPICS.map(({ icon: Icon, title, body }) => (
          <Card key={title}>
            <CardContent className="space-y-2 p-5">
              <div className="flex items-center gap-2">
                <Icon className="h-4 w-4 text-primary" aria-hidden="true" />
                <h3 className="text-sm font-semibold">{title}</h3>
              </div>
              <p className="text-sm leading-relaxed text-muted-foreground">{body}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardContent className="flex items-center gap-3 p-5">
          <BookOpen className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <p className="text-sm text-muted-foreground">
            Want the story behind the platform? See the{" "}
            <Link href="/about" className="font-medium text-primary underline-offset-4 hover:underline">
              About page
            </Link>
            .
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
