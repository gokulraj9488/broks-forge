import Link from "next/link";
import { GuestGuard } from "@/components/auth/auth-guard";
import { Logo } from "@/components/brand/logo";
import { ContactDeveloper } from "@/components/common/contact-developer";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <GuestGuard>
      <div className="auth-backdrop flex min-h-screen flex-col items-center justify-center px-4 py-12">
        <div className="mb-8">
          <Link href="/">
            <Logo />
          </Link>
        </div>
        <div className="w-full max-w-md animate-fade-in">{children}</div>
        <div className="mt-8 flex flex-col items-center gap-2 text-center">
          <ContactDeveloper />
          <p className="text-xs text-muted-foreground">
            © {new Date().getFullYear()} Brok&apos;s Forge · The Engineering Platform for AI Agents.
          </p>
        </div>
      </div>
    </GuestGuard>
  );
}
