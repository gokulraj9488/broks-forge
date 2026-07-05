import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import { Providers } from "@/components/providers";
import { cn } from "@/lib/utils";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Brok's Forge",
    template: "%s · Brok's Forge",
  },
  description: "The Engineering Platform for AI Agents.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // next-themes stamps the theme class before first paint (suppressHydrationWarning
    // covers the attribute swap); hardcoding "dark" here would flash dark for
    // light-theme users on every load.
    <html lang="en" suppressHydrationWarning>
      <body className={cn(inter.variable, jetbrainsMono.variable, "font-sans")}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
