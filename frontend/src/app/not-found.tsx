import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { ContactDeveloper } from "@/components/common/contact-developer";

export default function NotFound() {
  return (
    <div className="auth-backdrop flex min-h-screen flex-col items-center justify-center gap-6 px-4 text-center">
      <Link href="/" aria-label="Brok's Forge home">
        <Logo />
      </Link>
      <div className="space-y-2">
        <p className="text-6xl font-bold tracking-tight text-primary">404</p>
        <h1 className="text-xl font-semibold">Page not found</h1>
        <p className="max-w-sm text-sm text-muted-foreground">
          The page you&apos;re looking for doesn&apos;t exist or may have moved.
        </p>
      </div>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Button asChild>
          <Link href="/dashboard">Go to dashboard</Link>
        </Button>
        <Button asChild variant="outline">
          <Link href="/">Back home</Link>
        </Button>
      </div>
      <ContactDeveloper />
    </div>
  );
}
