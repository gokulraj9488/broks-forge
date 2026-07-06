"use client";

import { useEffect, useState } from "react";
import { useTheme } from "next-themes";
import { Moon, Sun } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { PasswordChangeCard } from "@/components/settings/password-change-card";

export default function SettingsPage() {
  const { theme, setTheme } = useTheme();
  // next-themes only knows the real theme after mount; render theme UI then to
  // avoid a wrong "dark" flash for light-theme users during hydration.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  return (
    <div>
      <PageHeader title="Settings" description="Manage your account preferences and security." />

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Appearance</CardTitle>
            <CardDescription>Choose how Brok&apos;s Forge looks to you.</CardDescription>
          </CardHeader>
          <CardContent>
            {mounted ? (
              <>
                <Field label="Theme" htmlFor="theme" className="max-w-xs">
                  <Select
                    value={theme === "light" ? "light" : "dark"}
                    onValueChange={(value) => setTheme(value)}
                  >
                    <SelectTrigger id="theme">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="dark">Dark</SelectItem>
                      <SelectItem value="light">Light</SelectItem>
                    </SelectContent>
                  </Select>
                </Field>
                <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                  {theme === "light" ? (
                    <Sun className="h-3.5 w-3.5" />
                  ) : (
                    <Moon className="h-3.5 w-3.5" />
                  )}
                  Currently using the {theme === "light" ? "light" : "dark"} theme.
                </div>
              </>
            ) : (
              <div className="h-[4.75rem] max-w-xs animate-pulse rounded-md bg-muted/50" />
            )}
          </CardContent>
        </Card>

        <PasswordChangeCard />
      </div>
    </div>
  );
}
