import type { Metadata, Viewport } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import { Providers } from "@/components/providers";
import { cn } from "@/lib/utils";
import { SITE_DESCRIPTION, SITE_NAME, SITE_TAGLINE, SITE_URL } from "@/lib/site";
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
  metadataBase: new URL(SITE_URL),
  title: {
    default: `${SITE_NAME} · ${SITE_TAGLINE}`,
    template: `%s · ${SITE_NAME}`,
  },
  description: SITE_DESCRIPTION,
  applicationName: SITE_NAME,
  // Terminology is deliberately led by the category and this product's own concept names — those
  // are the terms the documentation defines and uses consistently, so they are the ones worth
  // being findable by. Generic LLMOps terms follow, because that is what people search today.
  keywords: [
    "AI Engineering Operating System",
    "AI engineering",
    "engineering intelligence",
    "engineering memory",
    "AI Git",
    "root cause explorer",
    "forge graph",
    "AI system observability alternative",
    "LLM evaluation",
    "agent evaluation",
    "prompt versioning",
    "prompt management",
    "regression detection",
    "LLMOps",
    "AI agents",
    "self-hosted LLM platform",
    "open source AI engineering platform",
  ],
  authors: [{ name: "Gokulraj", url: "https://gokul.quest" }],
  creator: "Gokulraj",
  publisher: SITE_NAME,
  category: "technology",
  alternates: { canonical: "/" },
  // The favicon is auto-linked from app/icon.svg. `shortcut` is declared explicitly as well: without
  // a rel="shortcut icon" link, browsers fall back to probing /favicon.ico, which this app does not
  // ship — a guaranteed 404 on every first page view.
  icons: {
    icon: [{ url: "/icon.svg", type: "image/svg+xml" }],
    shortcut: [{ url: "/icon.svg", type: "image/svg+xml" }],
    apple: [{ url: "/icon.svg" }],
  },
  openGraph: {
    type: "website",
    siteName: SITE_NAME,
    title: `${SITE_NAME} · ${SITE_TAGLINE}`,
    description: SITE_DESCRIPTION,
    url: SITE_URL,
    locale: "en_US",
  },
  twitter: {
    card: "summary_large_image",
    title: `${SITE_NAME} · ${SITE_TAGLINE}`,
    description: SITE_DESCRIPTION,
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true },
  },
  formatDetection: { telephone: false },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#2A363B" },
  ],
  colorScheme: "dark light",
  width: "device-width",
  initialScale: 1,
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
